package com.ericsson.oss.services.domainproxy.test.server.netconf;

import akka.NotUsed;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.event.Logging;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.DelayOverflowStrategy;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.TLSClosing;
import akka.stream.javadsl.DelayStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Framing;
import akka.stream.javadsl.FramingTruncation;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueue;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.javadsl.Tcp;
import akka.util.ByteString;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.slf4j.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.ericsson.oss.mediation.netconf.TerribleButExpectedException;
import com.ericsson.oss.mediation.netconf.config.CommandListener;
import com.ericsson.oss.mediation.netconf.parser.DefaultNetconfHandler;
import com.ericsson.oss.mediation.netconf.parser.operation.RpcReplyFormat;
import com.ericsson.oss.mediation.netconf.server.api.Datastore;
import com.ericsson.oss.mediation.netconf.server.api.DefaultOperation;
import com.ericsson.oss.mediation.netconf.server.api.ErrorOption;
import com.ericsson.oss.mediation.netconf.server.api.Filter;
import com.ericsson.oss.mediation.netconf.server.api.TestOption;
import com.ericsson.oss.mediation.netconf.ssh.Killable;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Node;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.server.xml.XmlFactories;

public class NetconfNode {

    public interface Command {}

    private enum HeartbeatTimeout implements Command {
        INSTANCE
    }

    public static class StartCommand implements Command {
    }

    public static class StopCommand implements Command {
    }


    public static Behavior<Command> create(final ServerConfiguration serverConfiguration) {
        return Behaviors.withTimers(timers -> Behaviors.setup(ctx -> new NetconfNodeBehavior(ctx, serverConfiguration, timers)));
    }

    public static class NetconfNodeBehavior extends AbstractBehavior<NetconfNode.Command> {
        private static final Object TIMER_KEY = new Object();

        private final ServerConfiguration serverConfiguration;
        private final TimerScheduler<Command> timers;
        private final NetconfSessionRegistry sessionRegistry = new NetconfSessionRegistry();
        private boolean stopped = true;
        private Tcp.ServerBinding serverBinding;

        private NetconfNodeBehavior(final ActorContext<Command> actorContext,
                                    final ServerConfiguration serverConfiguration, final TimerScheduler<Command> timers) {
            super(actorContext);
            actorContext.setLoggerName(NetconfNodeBehavior.class);
            this.serverConfiguration = serverConfiguration;
            this.timers = timers;
            timers.startSingleTimer(new StartCommand(), Duration.ofSeconds(5));
            startHeartbeat();
        }

        private void startHeartbeat() {
            timers.startTimerWithFixedDelay(TIMER_KEY, HeartbeatTimeout.INSTANCE, Duration.ofSeconds(30));
        }

        private void startServerFlow() {
            final Logger log = getContext().getLog();
            stopped = false;
            final SSLContext sslContext = new NetconfSSLContext().getSSLContext(serverConfiguration);
            final String ipAddress = serverConfiguration.getIpAddress();
            final int port = serverConfiguration.getPort();
            final Source<Tcp.IncomingConnection, CompletionStage<Tcp.ServerBinding>> connectionSource =
                    Tcp.get(getContext().getSystem()).bindWithTls(ipAddress, port, () ->  createSslEngine(sslContext, ipAddress, port), 100,
                            Collections.EMPTY_LIST, Optional.empty(), s -> Optional.empty(), TLSClosing
                            .eagerClose());

            final Materializer materializer = Materializer.createMaterializer(getContext());

            connectionSource
                    .to(Sink.foreach(connection -> {
                        final Pair<SourceQueueWithComplete<String>, Source<String, NotUsed>> notificationSource =
                                Source.<String>queue(100, OverflowStrategy.dropNew(), 5)
                                        .preMaterialize(materializer);
                        final SourceQueueWithComplete<String> notificationQueue = notificationSource.first();

                        final DPCommandListener commandListener =
                                new DPCommandListener(serverConfiguration.getNode(), serverConfiguration.getTopology(),
                                        serverConfiguration.getNodeDataProducer(), sessionRegistry);
                        final NetconfSession session = new NetconfSession(serverConfiguration.getNode().getName(), commandListener, sessionRegistry, notificationQueue);
                        final Source<String, NotUsed> helloSource = Source.single(session.start());
                        Supplier<DelayStrategy<String>> topologyDelaySupplier = getDelayStrategy();
                        final Flow<ByteString, ByteString, NotUsed> serverFlow =
                                Flow.of(ByteString.class)
                                        .via(Framing.delimiter(ByteString.fromString(NetconfConstants.ENDSTRING), Integer.MAX_VALUE, FramingTruncation.ALLOW))
                                        .map(ByteString::utf8String)
                                        .log("NETCONF IN "+ serverConfiguration.getNode().getName())
                                        .filter(data -> data.indexOf('<') >= 0)
//                                        .map(elementTimer::start)
                                        .async()
                                        .map(session::onDataReceived)
                                        .filter(ele -> ele.length() > 0)
                                        .merge(helloSource)
                                        .merge(notificationSource.second()
//                                                .map(elementTimer::start)
                                                .log("NOTIFICATION " + serverConfiguration.getNode().getName())
                                                .withAttributes(Attributes.createLogLevels(Logging.InfoLevel(),Logging.InfoLevel(), Logging.ErrorLevel()))
                                        )
                                        .delayWith(topologyDelaySupplier, DelayOverflowStrategy.emitEarly())
                                        .takeWhile(elem -> {
                                            final boolean closed = session.isClosed();
                                            if (closed) {
                                                sessionRegistry.deregister(session.getSessionId());
                                            }
                                            return !closed;
                                        }, true)
                                        .log("NETCONF OUT " + serverConfiguration.getNode().getName())
                                        .withAttributes(Attributes.createLogLevels(Logging.DebugLevel(), Logging.WarningLevel(), Logging.ErrorLevel()))
                                        .map(ByteString::fromString);
                        connection.handleWith(serverFlow, materializer);
                    }))
                    .run(materializer)
                    .whenComplete((serverBinding, throwable) -> {
                        final String nodeName = serverConfiguration.getNode().getName();
                        if (throwable == null) {
                            this.serverBinding = serverBinding;
                            setAsStarted();
                            serverBinding.whenUnbound().thenRun(() -> this.serverBinding = null);
                            log.info("Server started, node={}, address={}", nodeName, serverBinding.localAddress());
                        } else {
                            setAsStopped(throwable.getMessage());
                            log.error("Server failed to start, node={}", nodeName, throwable);
                        }
                    });
        }

        private Supplier<DelayStrategy<String>> getDelayStrategy() {
            final String nodeName = serverConfiguration.getNode().getName();
            final Optional<Node> node = serverConfiguration.getTopology().getNode(nodeName);
            final long latencyMillis = node.get().getLatencyMillis();
            return () -> (DelayStrategy<String>) elem -> Duration.ofMillis(latencyMillis);
        }

        private SSLEngine createSslEngine(final SSLContext context, final String ipAddress, final int port) {
            SSLEngine engine = context.createSSLEngine(ipAddress, port);

            engine.setUseClientMode(false);
            engine.setWantClientAuth(true);
            engine.setEnabledCipherSuites(serverConfiguration.getSupportedCiphers().toArray(new String[]{}));
            engine.setEnabledProtocols(serverConfiguration.getSupportedProtocols().toArray(new String[]{}));
            return engine;
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder()
                    .onMessage(HeartbeatTimeout.class, this::onHeartbeatTimeout)
                    .onSignal(PostStop.class, this::postStop)
                    .onMessage(StartCommand.class, this::onStartCommand)
                    .onMessage(StopCommand.class, this::onStopCommand)
                    .build();
        }

        private Behavior<Command> onHeartbeatTimeout(final HeartbeatTimeout message) {
            getContext().getLog().debug("Starting to heartbeat {}", serverConfiguration.getNode().getName());
            sessionRegistry.heartbeatAll();
            return this;
        }

        private Behavior<Command> onStartCommand(final StartCommand message) {
            if (stopped) {
                getContext().getLog().info("Starting node {}", getNodeName());
                startServerFlow();
            }
            return this;
        }

        private Behavior<Command> onStopCommand(final StopCommand message) {
            if (!stopped) {
                getContext().getLog().info("Stopping node {}", getNodeName());
                stopServerFlow();
            }
            return this;
        }

        private Behavior<Command> postStop(final PostStop signal) {
            stopServerFlow();
            return this;
        }

        private void stopServerFlow() {
            sessionRegistry.killAll();
            if (serverBinding != null) {
                serverBinding.unbind();
                serverBinding = null;
            }
            setAsStopped();
        }

        private Topology getTopology() {
            return serverConfiguration.getTopology();
        }

        private String getNodeName() {
            return serverConfiguration.getNode().getName();
        }

        private void setAsStarted() {
            stopped = false;
            getTopology().setNodeAsStarted(getNodeName());
            getTopology().setNodeLatencyMillis(getNodeName(), serverConfiguration.getNode().getLatencyMillis());
        }

        private void setAsStopped() {
            setAsStopped(null);
        }

        private void setAsStopped(final String reason) {
            stopped = true;
            getTopology().setNodeAsStopped(getNodeName(), reason);
        }
    }

    @Slf4j
    static class NetconfSessionRegistry implements NotificationPublisher {
        private final Map<Integer, NetconfSession> sessions = new ConcurrentHashMap<>();

        public void register(final Integer sessionId, final NetconfSession session) {
            final NetconfSession old = sessions.put(sessionId, session);
            if (old != null) {
                log.warn("Netconf session override, session id already existed: {} ", sessionId);
                old.kill();
            }
        }

        public void deregister(final Integer sessionId) {
            sessions.remove(sessionId);
        }

        public void kill(final Integer sessionId) {
            final NetconfSession netconfSession = sessions.get(sessionId);
            if (netconfSession != null) {
                netconfSession.kill();
                deregister(sessionId);
            }
        }

        @Override
        public void publishNotification(final String notification) {
            for (final NetconfSession session : sessions.values()) {
                session.sendNotification(notification);
            }
        }

        public void heartbeatAll() {
            for (final NetconfSession session : sessions.values()) {
                session.heartbeat();
            }
        }

        public void killAll() {
            for (final NetconfSession session : sessions.values()) {
                session.kill();
                deregister(session.getSessionId());
            }
        }
    }

    @Slf4j
    static class NetconfSession implements CommandListener, Killable {
        private static final AtomicInteger sessionIdGenerator = new AtomicInteger(1);
        public static final int MAX_BUFFER = 1024 * 1024;

        private final String nodeName;
        private final CommandListener commandListener;
        private final NetconfSessionRegistry sessionRegistry;
        private final SourceQueue<String> notificationChannel;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final StringWriter output;
        private final PrintWriter printWriter;
        private final DefaultNetconfHandler xmlHandler;
        private final XMLReader parser;
        @Getter
        private int sessionId;
        @Getter
        private boolean isSubscribed;
        private Instant nextHeartbeat = null;

        @SneakyThrows
        public NetconfSession(final String nodeName, final CommandListener commandListener,
                              final NetconfSessionRegistry sessionRegistry,
                              final SourceQueue<String> notificationChannel) {
            this.nodeName = nodeName;
            this.commandListener = commandListener;
            this.sessionRegistry = sessionRegistry;
            this.notificationChannel = notificationChannel;
            output = new StringWriter(1024);
            printWriter = new PrintWriter(output);
            xmlHandler = new DefaultNetconfHandler(this, printWriter, closed);
            parser = XmlFactories.newXmlReader();
            parser.setContentHandler(xmlHandler);
            parser.setErrorHandler(xmlHandler);
        }

        public String start() {
            final StringBuffer buffer = output.getBuffer();
            buffer.delete(0, buffer.length());
            this.sessionId = sessionIdGenerator.getAndIncrement();
            this.hello(this.sessionId, printWriter);
            sessionRegistry.register(getSessionId(), this);
            buffer.trimToSize();
            return buffer.toString();
        }

        public String onDataReceived(final String dataReceived) {
            final StringBuffer buffer = output.getBuffer();
            buffer.delete(0, buffer.length());
            try {
                final int indexOfStart = dataReceived.indexOf("<?");
                final String data = indexOfStart != -1 ? dataReceived.substring(indexOfStart) : dataReceived;
                try {
                    parser.parse(new InputSource(new StringReader(data)));
                } catch (final IOException e) {
                    log.error("IOException parse", e);
                } catch (final SAXException e) {
                    if (xmlHandler.isHalt()) {
                        closed.set(true);
                        throw new TerribleButExpectedException();
                    } else if (!xmlHandler.isNop()) {
                        xmlHandler.errorHandler(e.getMessage());
                    }
                    log.error("SAXException parse", e);
                } catch (final Exception e) {
                    log.error("Error processing request. data={}", data, e);
                    xmlHandler.reset();
                }
                return buffer.toString();
            } finally {
                if (buffer.length() > MAX_BUFFER) {
                    buffer.delete(MAX_BUFFER, buffer.length());
                }
                buffer.trimToSize();
            }
        }

        public boolean isClosed() {
            return this.closed.get();
        }

        @Override
        public void closeSession(final String messageId, final PrintWriter printWriter) {
            this.closed.set(true);
            commandListener.closeSession(messageId, printWriter);
        }

        @Override
        public void hello(final int sessionId, final PrintWriter printWriter) {
            commandListener.hello(sessionId, printWriter);
        }

        @Override
        public void clientHello(final List<String> capabilities, final PrintWriter printWriter) {
            commandListener.clientHello(capabilities, printWriter);
        }

        @Override
        public void action(final String messageId, final String actionMessage, final PrintWriter printWriter) {
            commandListener.action(messageId, actionMessage, printWriter);
        }

        @Override
        public void get(final String messageId, final Filter filter, final PrintWriter printWriter) {
            commandListener.get(messageId, filter, printWriter);
        }

        @Override
        public void getSchema(final String messageId, final String identifier, final String version, final String format, final PrintWriter printWriter) {
            commandListener.getSchema(messageId, identifier, version, format, printWriter);
        }

        @Override
        public void getConfig(final String messageId, final Datastore datastore,
                              final Filter filter, final PrintWriter printWriter) {
            commandListener.getConfig(messageId, datastore, filter, printWriter);
        }

        @Override
        public void lock(final String messageId, final String sessionid, final Datastore target, final PrintWriter printWriter) {
            commandListener.lock(messageId, sessionid, target, printWriter);
        }

        @Override
        public void unlock(final String messageId, final Datastore target, final PrintWriter printWriter) {
            commandListener.unlock(messageId, target, printWriter);
        }

        @Override
        public void validate(final String messageId, final Datastore datastore, final PrintWriter printWriter) {
            commandListener.validate(messageId, datastore, printWriter);
        }

        @Override
        public void commit(final String messageId, final PrintWriter printWriter) {
            commandListener.commit(messageId, printWriter);
        }

        @Override
        public void discardChanges(final String messageId, final PrintWriter printWriter) {
            commandListener.discardChanges(messageId, printWriter);
        }

        @Override
        public void editConfig(final String messageId, final Datastore datastore,
                               final DefaultOperation defaultOperation,
                               final ErrorOption errorOption,
                               final TestOption testOption, final String config, final PrintWriter printWriter) {
            commandListener.editConfig(messageId, datastore, defaultOperation, errorOption, testOption, config, printWriter);
        }

        @Override
        public void killSession(final String messageId, final int sessionId, final PrintWriter printWriter) {
            commandListener.killSession(messageId, sessionId, printWriter);
            if (sessionId != getSessionId()) {
                sessionRegistry.kill(sessionId);
            }
        }

        @Override
        public Callable<Boolean> createSubscription(final String messageId, final String stream, final Filter filter,
                                                    final String startTime, final String stopTime, final PrintWriter printWriter) {
            if (isSubscribed()) {
                printWriter.print(String.format(RpcReplyFormat.RPC_ERROR_CREATE_SUBSCRIPTION_FAILED, messageId));
                printWriter.println(NetconfConstants.ENDSTRING);
                return null;
            } else {
                isSubscribed = true;
                log.info("Creating subscription. name={}, session={}", nodeName, sessionId);
                return commandListener.createSubscription(messageId, stream, filter, startTime, stopTime, printWriter);
            }
        }

        @Override
        public void sendError(final String messageId, final String rpcError, final PrintWriter printWriter) {
            commandListener.sendError(messageId, rpcError, printWriter);
        }

        @Override
        public void customOperation(final String messageId, final String requestBody, final PrintWriter printWriter) {
            commandListener.customOperation(messageId, requestBody, printWriter);
        }

        @Override
        public void customOperation(final String messageId, final String requestBody, final boolean returnResponse, final PrintWriter printWriter) {
            commandListener.customOperation(messageId, requestBody, returnResponse, printWriter);
        }

        @Override
        public void copyConfig(final String messageId, final String source, final String target, final PrintWriter printWriter) {
            commandListener.copyConfig(messageId, source, target, printWriter);
        }

        @Override
        public void kill() {
            // stop notifications
            this.closed.set(true);
        }

        public void heartbeat() {
            if (isSubscribed() && isTimeToSendHeartbeat()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                final String timestamp = dateFormat.format(new Date());
                final String notification = RpcReplyFormat.XML_START +
                        "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">" +
                        "<eventTime>" + timestamp + "</eventTime>" +
                        "<heartbeat xmlns=\"urn:ericsson:com:netconf:heartbeat:1.0\"/>" +
                        "</notification>]]>]]>";
                log.info("Sending HB notification. name={}, session={}", nodeName, sessionId);
                sendNotification(notification);
            }
        }

        public void sendNotification(final String notification) {
            if (isSubscribed()) {
                notificationChannel.offer(notification);
            }
        }

        private boolean isTimeToSendHeartbeat() {
            if (nextHeartbeat == null || Instant.now().isAfter(nextHeartbeat)) {
                nextHeartbeat = Instant.now().plus(Duration.ofMillis(120000));
                return true;
            }
            return false;
        }
    }

    @Slf4j
    private static class ElementTimer {
        private final String name;
        private final NetconfSession session;
        private final Queue<Long> times = new ArrayDeque<>(10);

        public ElementTimer(final String name,
                            final NetconfSession session) {
            this.name = name;
            this.session = session;
        }

        public <T> T start(T element) {
            times.add(System.nanoTime());
            return element;
        }

        public <T> T stop(T element) {
            final Long start = times.poll();
            if (start != null) {
                final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                log.debug("Flow processing took: {} ms, node={}, session={}", duration, name, session.getSessionId());
            }
            return element;
        }
    }
}
