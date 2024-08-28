package com.ericsson.oss.services.domainproxy.test.server;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.DispatcherSelector;
import akka.actor.typed.Props;
import akka.actor.typed.SpawnProtocol;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import com.ericsson.oss.mediation.netconf.config.SecurityDefinition;
import com.ericsson.oss.mediation.netconf.config.TlsConfiguration;
import com.ericsson.oss.mediation.netconf.config.TrustedCertificate;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.CbsdCpiData;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.NodeManagement;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.server.config.CbsdSerialToCpiDefinition;
import com.ericsson.oss.services.domainproxy.test.server.config.MeasurementsDefinition;
import com.ericsson.oss.services.domainproxy.test.server.config.NodeDefinition;
import com.ericsson.oss.services.domainproxy.test.server.config.ProductToCpiDefinition;
import com.ericsson.oss.services.domainproxy.test.server.config.ReportConfig;
import com.ericsson.oss.services.domainproxy.test.server.config.SasConfig;
import com.ericsson.oss.services.domainproxy.test.server.config.TestConfiguration;
import com.ericsson.oss.services.domainproxy.test.server.config.TlsSecurityDefinition;
import com.ericsson.oss.services.domainproxy.test.server.netconf.NetconfNode;
import com.ericsson.oss.services.domainproxy.test.server.netconf.ServerConfiguration;
import com.ericsson.oss.services.domainproxy.test.server.testevent.Report;
import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import com.ericsson.oss.services.domainproxy.test.server.testevent.reports.InMemoryReport;
import com.ericsson.oss.services.domainproxy.test.server.testevent.reports.LoggerReport;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.AddNodeCommands;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.AdminLoadMapping;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.AdminReport;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.AdminReset;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.AdminSetSysProperty;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.Cpi;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.DPHttpServerFactory;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.GroupIds;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.ResponseReader;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.TopologyTree;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.response.DeregistrationBodyStateTransformer;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.response.GrantBodyStateTransformer;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.response.HeartbeatBodyStateTransformer;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.response.RegistrationBodyStateTransformer;
import com.ericsson.oss.services.domainproxy.test.server.wiremock.response.RelinquishmentBodyStateTransformer;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.SpectrumInquiryBodyTransformer;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.TagType;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.cache.ConcurrentMapTemplateCache;
import com.github.jknack.handlebars.helper.AssignHelper;
import com.github.jknack.handlebars.helper.NumberHelper;
import com.github.jknack.handlebars.helper.StringHelpers;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.WireMockHelpers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.ericsson.oss.services.domainproxy.test.server.cbrs.RadioNodeCbrsDataReader.extractCbrsTopologyFromRadioNode;

@RequiredArgsConstructor
public class TestManager {
    private static final Logger logger = LoggerFactory.getLogger(TestManager.class);
    private static final Map<String, Template> nodeDataTemplate = new ConcurrentHashMap<>();
    public static final String FILE_LOCATION_PREFIX = "file:";
    private static final long ONE_MINUTE_IN_MILLI = 60 * 1000L;
    private final TestConfiguration testConfiguration;
    private final List<ActorRef<NetconfNode.Command>> netconfServers = new ArrayList<>(5000);
    private final LoadingCache<NodeDefinition, byte[]> nodeDataCache = CacheBuilder.newBuilder()
            .maximumSize(6000)
            .build(CacheLoader.from(this::generateNodeData));
    private long lastCacheMaintenance = 0;
    private Reporter reporter  = null;
    @Getter
    private InMemoryReport inMemoryReport = new InMemoryReport();
    private Topology topology = null;
    private final Map<String, CbsdCpiData> productNumberToCpi = new ConcurrentHashMap<>();
    private final AtomicBoolean stop = new AtomicBoolean();
    private WireMockServer wireMockServer;
    private ActorSystem<SpawnProtocol.Command> system;
    private WireMockServer dcmWireMockServer;

    public void start() throws IOException {
        logger.info("Starting DP TestManager...");
        validate();
        configure();
        reporter.start();
        startActorSystem(topology);
        startNodes();
        topology.applyCpiDataByProductNumber(this.productNumberToCpi);
        startWiremock();
        startDcmWireMock();
        logger.info("---------------------------------------\n             DP TestManager started\n---------------------------------------");
    }

    private void configure() {
        List<ReportConfig> reportsConfig = testConfiguration.getReports();
        if (reportsConfig == null) {
            logger.info("Initializing report: {}", LoggerReport.class.getName());
            LoggerReport report = new LoggerReport();
            report.initialize(Collections.emptyMap());
            reporter = new Reporter(Arrays.asList(report, inMemoryReport));
        } else {
            final List<Report> reports = new ArrayList<>(reportsConfig.size() + 1);
            reports.add(inMemoryReport);
            for (final ReportConfig reportConfig : reportsConfig) {
                try {
                    logger.info("Initializing report: {}", reportConfig.getReportClass());
                    final Report report = (Report) getClass().getClassLoader().loadClass(reportConfig.getReportClass()).newInstance();
                    final Map<String, String> initParameters = reportConfig.getInitParameters();
                    report.initialize(initParameters == null ? Collections.emptyMap() : initParameters);
                    reports.add(report);
                } catch (Exception e) {
                    logger.error("Failed to initialize report: {}", reportConfig, e);
                }
            }
            reporter = new Reporter(reports);
        }
        this.topology = new Topology(reporter);

        this.productNumberToCpi.put("KRC 161 711/1", new CbsdCpiData("47.0", "0", false, "TA8AKRC161711-1"));
        this.productNumberToCpi.put("KRC 161 746/1", new CbsdCpiData("47.0", "0", false, "TA8AKRC161746-1"));
        this.productNumberToCpi.put("KRD 901 160/2", new CbsdCpiData("44.50", "11", false, "TA8BKRD901160"));
        this.productNumberToCpi.put("KRD 901 160/1", new CbsdCpiData("44.50", "0", false, "TA8BKRD901160"));
        this.productNumberToCpi.put("KRD 901 160/21", new CbsdCpiData("44.50", "0", false, "TA8BKRD901160"));
        this.productNumberToCpi.put("KRD 901 160/11", new CbsdCpiData("44.50", "0", false, "TA8BKRD901160"));
        this.productNumberToCpi.put("KRY 901 385/1", new CbsdCpiData("25.0", "3", true, "TA8AKRY901385-1"));
        this.productNumberToCpi.put("KRD 901 254/1", new CbsdCpiData("46.0", "11", false, "TA8AKRD901254"));
        this.productNumberToCpi.put("KRD 901 254/11", new CbsdCpiData("46.0", "0", false, "TA8AKRD901254"));
        this.productNumberToCpi.put("KRD 901 254/3", new CbsdCpiData("46.0", "0", false, "TA8AKRD901254"));
        this.productNumberToCpi.put("KRD 901 254/31", new CbsdCpiData("46.0", "0", false, "TA8AKRD901254"));
        this.productNumberToCpi.put("KRY 901 537/2", new CbsdCpiData("29.0", "5", true, "TA8BKRY901537-2"));
        this.productNumberToCpi.put("KRY 901 537/1", new CbsdCpiData("29.0", "5", true, "TA8BKRY901537-1"));

        final List<ProductToCpiDefinition> productCpi = testConfiguration.getProductCpi();
        if (productCpi != null) {
            productCpi.forEach(productToCpiDefinition -> productNumberToCpi.put(productToCpiDefinition.getProductNumber(),
                    new CbsdCpiData(productToCpiDefinition.getEirpCapability(), productToCpiDefinition.getAntennaGain(),
                            productToCpiDefinition.isIndoorDeployment(), productToCpiDefinition.getFccid())));
        }
    }

    public void stop() {
        if (wireMockServer != null) {
            try {
                wireMockServer.stop();
            } catch (Exception e) {
                System.out.println("failed to stop wiremock");
                e.printStackTrace();
            }
        }
        if (dcmWireMockServer != null) {
            try {
                dcmWireMockServer.stop();
            } catch (Exception e) {
                logger.error("failed to stop dcmWireMockServer");
                e.printStackTrace();
            }
        }
        if (system != null) {
            system.terminate();
            netconfServers.clear();
            system = null;
        }
        try {
            reporter.stop();
        } catch (Exception e) {
            System.out.println("failed to stop reporter");
            e.printStackTrace();
        }
        this.stop.set(true);
    }

    private void startActorSystem(final Topology topology) {
        final Duration verificationInterval = logger.isDebugEnabled() ? Duration.ofSeconds(5) : Duration.ofMillis(800);
        system = ActorSystem.create(TestManagerBehavior.create(topology, verificationInterval), "testManager");
    }

    /**
     * PM latency alarms are received by DCM via http port 43340 or https port 43341.
     * Configuring wiremock with http port 43340 and https port 43341 as a mock DCM to receive pm latency alarms.
     */
    private void startDcmWireMock() {
        dcmWireMockServer = new WireMockServer(43340, 43341);
        dcmWireMockServer.start();
    }

    private void startWiremock() {
        final WireMockConfiguration options = WireMockConfiguration.options();
        final SasConfig sasConfig = testConfiguration.getSasConfig();
        options.httpServerFactory(new DPHttpServerFactory(sasConfig));
        if (sasConfig != null) {
            setNonNull(sasConfig.getHttpPort(), options::port);
            setNonNull(sasConfig.getHttpsPort(), options::httpsPort);
            setNonNull(sasConfig.getBindAddress(), options::bindAddress);
            setNonNull(sasConfig.getKeystorePath(), options::keystorePath);
            setNonNull(sasConfig.getKeystoreType(), options::keystoreType);
            setNonNull(sasConfig.getKeystorePassword(), options::keystorePassword);
            setNonNull(sasConfig.getNeedClientAuth(), options::needClientAuth);
            setNonNull(sasConfig.getTrustStorePath(), options::trustStorePath);
            setNonNull(sasConfig.getTrustStorePassword(), options::trustStorePassword);
            setNonNull(sasConfig.getKeystoreType(), options::trustStoreType);
            setNonNull(sasConfig.getUsingFilesUnderClasspath(), options::usingFilesUnderClasspath);
            setNonNull(sasConfig.getUsingFilesUnderDirectory(), options::usingFilesUnderDirectory);
            setNonNull(sasConfig.getAsynchronousResponseEnabled(), options::asynchronousResponseEnabled);
            setNonNull(sasConfig.getAsynchronousResponseThreads(), options::asynchronousResponseThreads);
            setNonNull(sasConfig.getJettyAcceptorsThreads(), options::jettyAcceptors);
            setNonNull(sasConfig.getJettyContainerThreads(), options::containerThreads);

            if (sasConfig.getDisableRequestJournal() != null && sasConfig.getDisableRequestJournal()) {
                options.disableRequestJournal();
            }
            if (sasConfig.getUsingFilesUnderClasspath() == null && sasConfig.getUsingFilesUnderDirectory() == null) {
                options.usingFilesUnderClasspath("wiremockroot");
            }
        } else {
            options.usingFilesUnderClasspath("wiremockroot");
        }
        final Handlebars handlebars = createHandlebars();
        options.extensions(new GrantBodyStateTransformer(handlebars, topology), new HeartbeatBodyStateTransformer(handlebars, topology),
                new DeregistrationBodyStateTransformer(handlebars, topology), new RegistrationBodyStateTransformer(handlebars, topology),
                new RelinquishmentBodyStateTransformer(handlebars, topology));
        options.extensions(SpectrumInquiryBodyTransformer.class);
        options.extensions(new ResponseTemplateTransformer(false, handlebars, Collections.emptyMap(), 20L));
        options.extensions(new ResponseReader(topology, reporter));
        options.extensions(new AdminReset(topology, reporter));
        options.extensions(new AdminReport(inMemoryReport));
        options.extensions(new AdminLoadMapping());
        options.extensions(new AdminSetSysProperty());
        options.extensions(new AddNodeCommands(topology));
        options.extensions(new Cpi(topology));
        options.extensions(new GroupIds(topology));

        try {
            options.extensions(new TopologyTree(topology, handlebars));
        } catch (IOException e) {
            logger.error("Error creating TopologyTree wiremock extension.");
        }

        wireMockServer = new WireMockServer(options);
        wireMockServer.start();
    }

    private Handlebars createHandlebars() {
        final Handlebars handlebars = new Handlebars().with(new ConcurrentMapTemplateCache());
        for (StringHelpers helper: StringHelpers.values()) {
            if (!helper.name().equals("now")) {
                handlebars.registerHelper(helper.name(), helper);
            }
        }

        for (NumberHelper helper: NumberHelper.values()) {
            handlebars.registerHelper(helper.name(), helper);
        }

        handlebars.registerHelper("contains", new Helper<String>() {
            @Override
            public Object apply(final String a, final Options options) throws IOException {
                String b = options.param(0, null);
                boolean result = a.contains(b);
                if (options.tagType == TagType.SECTION) {
                    return result ? options.fn() : options.inverse();
                }
                return result ? options.hash("yes", true): options.hash("no", false);
            }
         });


        handlebars.registerHelper(AssignHelper.NAME, new AssignHelper());

        //Add all available wiremock helpers
        for(WireMockHelpers helper: WireMockHelpers.values()){
            handlebars.registerHelper(helper.name(), helper);
        }
        return handlebars;
    }

    private void startNodes() throws IOException {
        final TlsSecurityDefinition tlsSecurityDefinition = testConfiguration.getNetconf().getTlsSecurityDefinition();
        final SecurityDefinition securityDefinition = new SecurityDefinition(tlsSecurityDefinition.getAlias(), tlsSecurityDefinition.getKeyPath(),
                                                                             tlsSecurityDefinition.getCertificatePath());
        final TrustedCertificate
                trustedCertificate = new TrustedCertificate(testConfiguration.getNetconf().getTrustedCertificate().getAlias(), testConfiguration.getNetconf().getTrustedCertificate().getCertificatePath());
        final TlsConfiguration tlsConfiguration =
                new TlsConfiguration(1, 0, 2000L, Collections.emptyList(), Collections.singletonList(securityDefinition), Collections.singletonList(trustedCertificate));
        final String ipAddPath = testConfiguration.getNetconf().getIpAddrPath();
        logger.info("IP Address Path {}", ipAddPath);
        List<String> listOfIPAddrFromTxt = null;
        if(ipAddPath != null) {
            final ConvertFileToIPList ConvertFileToIPList = new ConvertFileToIPList();
            listOfIPAddrFromTxt = ConvertFileToIPList.readIpAddFile(ipAddPath);
        }
        final String portRangeV6 = testConfiguration.getNetconf().getPortRangeV6() == null ? testConfiguration.getNetconf().getPortRange() :
                testConfiguration.getNetconf().getPortRangeV6();
        final ServerAddressProvider serverAddressProvider =
                ServerAddressProvider.from(listOfIPAddrFromTxt, testConfiguration.getNetconf().getIpRange(), testConfiguration.getNetconf().getPortRange(),
                        testConfiguration.getNetconf().getIpRangeV6(),
                        portRangeV6);
        startNetconfServers(createNodesDefinition(), tlsConfiguration, serverAddressProvider);
    }

    private void validate() {

    }

    private List<NodeDefinition> createNodesDefinition() {
        final Pattern repeatPattern = Pattern.compile("\\$\\{\\s*repeat\\s*}");
        final List<NodeDefinition> nodes = new ArrayList<>();
        if (testConfiguration.getNodes() == null || testConfiguration.getNodes().isEmpty()) {
            return nodes;
        }

        for (final NodeDefinition nodeDefinition : testConfiguration.getNodes()) {
            final int repeatTotal = Math.max(1, nodeDefinition.getRepeat());
            for (int i = 0; i < repeatTotal; i++) {
                final String repeatString = String.format("%05d", i+1);
                final Function<String, String> replaceRepeat = value -> repeatPattern.matcher(value).replaceAll(repeatString);
                final NodeDefinition node = new NodeDefinition();
                node.setName(replaceRepeat.apply(nodeDefinition.getName()));
                node.setLatencyMillis(nodeDefinition.getLatencyMillis());
                if (nodeDefinition.getCbsdSerials() != null) {
                    node.setCbsdSerials(nodeDefinition.getCbsdSerials().stream()
                                                .map(replaceRepeat).collect(Collectors.toList()));
                }
                if(nodeDefinition.getCbsdSerialToCpi() != null) {
                    final List<CbsdSerialToCpiDefinition> newCpiDefList = nodeDefinition.getCbsdSerialToCpi().stream().map(cpiDef -> {
                        final String serial = replaceRepeat.apply(cpiDef.getSerialNumber());
                        final CbsdSerialToCpiDefinition newDef = new CbsdSerialToCpiDefinition();
                        newDef.setSerialNumber(serial);
                        newDef.setAntennaGain(cpiDef.getAntennaGain());
                        newDef.setEirpCapability(cpiDef.getEirpCapability());
                        newDef.setIndoorDeployment(cpiDef.isIndoorDeployment());

                        return newDef;
                    }).collect(Collectors.toList());
                    node.setCbsdSerialToCpi(newCpiDefList);
                }
                node.setRepeat(i+1);
                if (nodeDefinition.getAttributes() != null) {
                    final Map<String, Object> attributes = nodeDefinition.getAttributes().entrySet().stream()
                            .collect(Collectors.toMap(entry -> replaceRepeat.apply(entry.getKey()), entry -> {
                                final Object value = entry.getValue();
                                if (value instanceof String) {
                                    return replaceRepeat.apply(entry.getValue().toString());
                                }
                                return value;
                            }));
                    node.setAttributes(attributes);
                }
                node.setDataTemplate(replaceRepeat.apply(nodeDefinition.getDataTemplate()));
                if (nodeDefinition.getMeasurements() == null) {
                    node.setMeasurements(new MeasurementsDefinition());
                } else {
                    node.setMeasurements(nodeDefinition.getMeasurements());
                }
                node.setUseIp6(nodeDefinition.isUseIp6());
                nodes.add(node);
            }
        }
        return nodes;
    }

    private void startNetconfServers(final List<NodeDefinition> nodes, final TlsConfiguration baseConfig,
                                     final ServerAddressProvider addressProvider) throws IOException {
        final CountDownLatch startingNodes = new CountDownLatch(nodes.size());
        for (final NodeDefinition node : nodes) {
            final ServerAddress serverAddress;
            if(addressProvider.getIPListProvider() != null) {
                serverAddress = addressProvider.nextIPListAddress();
            } else {
                serverAddress = node.isUseIp6() ? addressProvider.nextV6Address() : addressProvider.nextV4Address();
            }
            final String nodeAddress = serverAddress.getInetAddress().getHostAddress();
            final int nodePort = serverAddress.getPort();
            logger.debug("Generating node data. Starting. node={}", node);
            final String nodeData = getNodeData(node);
            logger.debug("Generating node data. Finished. node={}, size={}", node, nodeData.length());
            logger.trace("Generating node data. node={}, data={}", node, nodeData);
            final ServerConfiguration serverConfig =
                    new ServerConfiguration(nodeAddress, nodePort, baseConfig.getSocketTimeout(),
                            baseConfig.getWaitForClose(),baseConfig.getSecurityDefinitions(),
                            baseConfig.getTrustedCertificates(), node, topology, this::getNodeData);

            logger.info("Starting netconf server with configuration. configuration={}", serverConfig);
            topology.addNode(node.getName(), nodeAddress, nodePort, node.getLatencyMillis());
            final CompletionStage<ActorRef<NetconfNode.Command>> futureServer =
                    AskPattern.ask(system, replyTo -> new SpawnProtocol.Spawn<>(NetconfNode.create(serverConfig), node.getName(), Props.empty(), replyTo),
                            Duration.ofMinutes(1), system.scheduler());
            futureServer.whenComplete((commandActorRef, error) -> {
                startingNodes.countDown();
                if (error == null) {
                    try {
                        netconfServers.add(commandActorRef);
                        topology.setNodeManagement(node.getName(), new ActorNodeManagement(commandActorRef));
                        extractCbrsTopology(nodeData, node);
                        logger.info("Node created {} -----------------------------------------------------------------------", node.getName());
                        logger.info("\ncmedit create NetworkElement={} networkElementId=\"{}\",neType=\"RadioNode\",ossPrefix=\"\" " +
                                "-ns=OSS_NE_DEF -v=2.0.0", node.getName(), node.getName());
                        logger.info("\ncmedit create NetworkElement={},ComConnectivityInformation=1 ComConnectivityInformationId=\"1\"," +
                                "port=\"{}\",transportProtocol=\"TLS\",ipAddress=\"{}\" -ns=COM_MED -v=1.1.0", node.getName(), nodePort, nodeAddress);
                        logger.info("\nsecadm credentials create --secureusername netsim --secureuserpassword netsim -n {}", node.getName());
                        logger.info("\ncmedit set NetworkElement={},CmNodeHeartbeatSupervision=1 active=true", node.getName());
                    } catch (IOException e) {
                        logger.error("Node failed to read node topology. node={}", node.getName(), e);
                    }
                } else {
                    logger.error("Node failed to create. node={}", node.getName(), error);
                    topology.setNodeAsStopped(node.getName(), error.getMessage());
                }
            });
        }
        try {
            startingNodes.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SneakyThrows
    private String getNodeData(final NodeDefinition node) {
        final byte[] dataBytes = nodeDataCache.get(node);
        ByteArrayOutputStream result = new ByteArrayOutputStream(1024 * 1024);
        try(final LZ4FrameInputStream inputStream = new LZ4FrameInputStream(new ByteArrayInputStream(dataBytes))){
            byte[] buffer = new byte[1024 * 10];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
        }
        return result.toString("UTF-8");
    }

    @SneakyThrows
    public byte[] generateNodeData(final NodeDefinition nodeDefinition){
        final ByteArrayOutputStream data = new ByteArrayOutputStream(1024 * 100);
        try (final OutputStreamWriter osw = new OutputStreamWriter(new LZ4FrameOutputStream(data))) {
            final Template template = getTemplate(nodeDefinition.getDataTemplate());
            template.apply(nodeDefinition, osw);
        }
        final byte[] nodeData = data.toByteArray();
        logger.debug("Generated node data. node={}, size={}", nodeDefinition.getName(), nodeData.length);
        return nodeData;
    }

    private static Template getTemplate(final String template) throws IOException {
        return nodeDataTemplate.computeIfAbsent(template, key -> {
            final String templateLocation = key;
            try {
                if (templateLocation.startsWith(FILE_LOCATION_PREFIX)) {
                    final Path path = Paths.get(templateLocation.substring(FILE_LOCATION_PREFIX.length()));
                    final Handlebars handlebars = new Handlebars(new FileTemplateLoader(path.getParent().toFile()));
                    return handlebars.compile(path.toString());
                }
                final Handlebars handlebars = new Handlebars(new ClassPathTemplateLoader());
                return handlebars.compile(templateLocation);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

        });
    }

    private void extractCbrsTopology(final String nodeData, final NodeDefinition node) throws IOException {
        logger.debug("Extract CBRS topology. Node data generated. node={}", node);
        extractCbrsTopologyFromRadioNode(node.getName(), nodeData, topology);
        logger.debug("Extract CBRS topology. Completed. node={}", node);
        final List<CbsdSerialToCpiDefinition> cbsdSerialToCpi = node.getCbsdSerialToCpi();
        if (cbsdSerialToCpi != null) {
            cbsdSerialToCpi.forEach(def -> {
                CbsdCpiData cpiData = new CbsdCpiData(def.getEirpCapability(), def.getAntennaGain(), def.isIndoorDeployment(), null);
                topology.trySetCbsdCpiData(def.getSerialNumber(), cpiData);
            });
        }
    }

    private <T> void setNonNull(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    public abstract static class TestManagerBehavior {
        private TestManagerBehavior() {
        }

        public static Behavior<SpawnProtocol.Command> create(final Topology topology, final Duration verificationInterval) {
            return Behaviors.setup(
                    context -> {
                        // Start initial tasks
                        context.spawn(TestManagerMonitorBehavior.create(topology, verificationInterval), "testManagerMonitor",
                                DispatcherSelector.fromConfig("verification-dispatcher"));
                        return SpawnProtocol.create();
                    });
        }
    }

    public abstract static class TestManagerMonitorBehavior {
        private static final Object TIMER_KEY = new Object();
        private TestManagerMonitorBehavior() {
        }

        enum Commands {
            RUN_VERIFICATION
        }

        public static Behavior<Commands> create(final Topology topology, final Duration interval) {
            return Behaviors.withTimers(timers -> Behaviors.setup(context -> {
                context.setLoggerName(TestManagerMonitorBehavior.class);
                timers.startTimerWithFixedDelay(TIMER_KEY, Commands.RUN_VERIFICATION, interval);
                return Behaviors.receive(Commands.class)
                        .onMessageEquals(Commands.RUN_VERIFICATION, () -> {
                            context.getLog().debug("Starting periodic checks...");
                            topology.performPeriodicChecks();
                            return Behaviors.same();
                        })
                        .build();
            }));
        }
    }

    private static class ActorNodeManagement implements NodeManagement {
        private final ActorRef<NetconfNode.Command> actorRef;

        public ActorNodeManagement(final ActorRef<NetconfNode.Command> actorRef) {
            this.actorRef = actorRef;
        }

        @Override
        public void start() {
            actorRef.tell(new NetconfNode.StartCommand());
        }

        @Override
        public void stop() {
            actorRef.tell(new NetconfNode.StopCommand());
        }
    }
}
