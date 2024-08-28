package com.ericsson.oss.services.domainproxy.test.server.netconf;

import com.ericsson.oss.mediation.netconf.config.listener.DefaultCommandListener;
import com.ericsson.oss.mediation.netconf.parser.operation.RpcReplyFormat;
import com.ericsson.oss.mediation.netconf.server.api.Datastore;
import com.ericsson.oss.mediation.netconf.server.api.DefaultOperation;
import com.ericsson.oss.mediation.netconf.server.api.ErrorOption;
import com.ericsson.oss.mediation.netconf.server.api.Filter;
import com.ericsson.oss.mediation.netconf.server.api.RFC4741;
import com.ericsson.oss.mediation.netconf.server.api.RFC5277;
import com.ericsson.oss.mediation.netconf.server.api.TestOption;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Fru;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.RadioNodeCbrsDataReader;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.server.config.NodeDefinition;
import com.ericsson.oss.services.domainproxy.test.server.xml.subtree.XmlSubtreeFilter;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLStreamException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ericsson.oss.mediation.netconf.parser.operation.RpcReplyFormat.RPC_ERROR_FAILED_TO_PARSE_XML;

public class DPCommandListener extends DefaultCommandListener {
    private static Logger logger = LoggerFactory.getLogger(DPCommandListener.class);
    static final Pattern LTE_CELL_FDN_PATTERN = Pattern.compile("managedElementId>([^<]+).*eNodeBFunctionId>([^<]+).*eUtranCellTDDId>([^<]+).*");
    static final Pattern NR_CELL_FDN_PATTERN = Pattern.compile("managedElementId>([^<]+).*gNBDUFunctionId>([^<]+).*nRCellDUId>([^<]+).*");
    static final Pattern NR_CELL_SECTOR_CARRIER_FDN_PATTERN = Pattern.compile("managedElementId>([^<]+).*gNBDUFunctionId>([^<]+).*nRSectorCarrierId>([^<]+).*");
    static final Pattern TX_EXPIRE_PATTERN = Pattern.compile("transmitExpireTime>([^<]+)");
    static final Pattern EARFCN_PATTERN = Pattern.compile("earfcn>([^<]+)");
    static final Pattern ARFCNDL_PATTERN = Pattern.compile("arfcnDL>([^<]+)");
    static final Pattern ARFCNUL_PATTERN = Pattern.compile("arfcnUL>([^<]+)");
    static final Pattern FREQ_PATTERN = Pattern.compile("freq>([^<]+)</freq>");
    static final Pattern BANDWIDTH_PATTERN = Pattern.compile("bandwidth>([^<]+)</bandwidth>");
    static final Pattern FRU_ID_PATTERN = Pattern.compile("fieldReplaceableUnitId>([^<]+)");
    static final Pattern EQUIPMENT_ID_PATTERN = Pattern.compile("equipmentId>([^<]+)");

    private static JAXBContext jaxbContext;
    private final NodeDefinition nodeDefinition;
    private final Topology topology;
    private final Function<NodeDefinition, String> nodeDataProducer;
    private final NotificationPublisher notificationPublisher;
    @Getter
    private int sessionId;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    static {
        try {
            jaxbContext = JAXBContext.newInstance(Fru.RcvdPowerScanner.class);
        } catch (JAXBException e) {
            logger.error("Failed to crate JAXB context.", e);
        }
    }

    public DPCommandListener(final NodeDefinition nodeDefinition, final Topology topology, final Function<NodeDefinition, String> nodeDataProducer, final NotificationPublisher notificationPublisher) {
        this.nodeDefinition = nodeDefinition;
        this.topology = topology;
        this.nodeDataProducer = nodeDataProducer;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public void hello(int sessionId, PrintWriter out) {
        this.sessionId = sessionId;
        logger.info("{}:{}> Hello", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            logger.info("{}:{}> writing Hello.", getNodeName(), this.sessionId);
            out.print(RpcReplyFormat.XML_START);
            out.print("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
            out.print("\t<capabilities>");
            out.print(this.getCapabilityTree(RFC4741.BASE.urn));
            out.print(this.getCapabilityTree("urn:ietf:params:netconf:base:1.1"));
            out.print(this.getCapabilityTree("urn:ietf:params:xml:ns:netconf:base:1.0"));
            out.print(this.getCapabilityTree(RFC4741.STARTUP.urn));
            out.print(this.getCapabilityTree("urn:ericsson:com:netconf:notification:1.1"));
            out.print(this.getCapabilityTree(RFC5277.NOTIFICATION.urn));
            out.print(this.getCapabilityTree("urn:ericsson:com:netconf:notification:1.0"));
            out.print(this.getCapabilityTree(RFC4741.ROLLBACK_ON_ERROR.urn));
            out.print(this.getCapabilityTree(RFC4741.WRITABLE_RUNNING.urn));
            out.print(this.getCapabilityTree(RFC4741.VALIDATE.urn));
            out.print(this.getCapabilityTree(RFC4741.CANDIDATE.urn));
            out.print(this.getCapabilityTree("urn:ericsson:com:netconf:heartbeat:1.0"));
            out.print(this.getCapabilityTree("urn:ietf:params:netconf:capability:action:1.0"));
            out.print("\t</capabilities>");
            out.print("\t<session-id>");
            out.print(sessionId);
            out.print("</session-id>");
            out.print("</hello>");
            out.println(NetconfConstants.ENDSTRING);
            out.flush();
        });
        logger.info("{}:{}> Hello completed.", getNodeName(), this.sessionId);
    }

    @Override
    public void get(final String messageId, final Filter filter, final PrintWriter out) {
        final long startAt = System.currentTimeMillis();
        if (filter == null) {
            logger.info("{}:{}> NETCONF GET. #node={}, #message-id={}", getNodeName(), this.sessionId, nodeDefinition.getName(), messageId);
        } else {
            logger.info("{}:{}> NETCONF GET WITH FILTER. #node={}, #message-id={}, #filter={}", getNodeName(), this.sessionId,
                    nodeDefinition.getName(), messageId,
                    filter.asString());
        }
        if (filter != null && filter.asString() != null && filter.asString().contains("streams")) {
            logger.debug("{}:{}> Sending default NETCONF stream..", sessionId, getNodeName());
            withWriteLock(() -> {
                out.print(RpcReplyFormat.XML_START);
                out.print("<rpc-reply message-id=\"");
                out.print(messageId);
                out.print("\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
                out.print("\t<data>");
                out.print("\t\t<netconf xmlns=\"urn:ietf:params:xml:ns:netmod:notification\">");
                out.print("\t\t\t<streams>");
                out.print("\t\t\t\t<stream>");
                out.print("\t\t\t\t\t<name>NETCONF</name>");
                out.print("\t\t\t\t\t<description>default NETCONF event stream</description>");
                out.print("\t\t\t\t\t<replaySupport>false</replaySupport>");
                // out.print("\t\t\t\t\t<replayLogCreationTime>2018-06-03T00:00:00Z</replayLogCreationTime>");
                out.print("\t\t\t\t</stream>");
                out.print("\t\t\t</streams>");
                out.print("\t\t</netconf>");
                out.print("\t</data>");
                out.print("</rpc-reply>");
                out.println(NetconfConstants.ENDSTRING);
                out.flush();
            });
        } else if (filter != null && filter.asString() != null && filter.asString().contains("<cbrsTxExpireTime></cbrsTxExpireTime>")) {
            Matcher cellFdnMatcher = null;
            if(filter.asString().contains("GNBDUFunction")) {
                cellFdnMatcher = NR_CELL_SECTOR_CARRIER_FDN_PATTERN.matcher(filter.asString());
            } else {
                cellFdnMatcher = LTE_CELL_FDN_PATTERN.matcher(filter.asString());
            }
            if (cellFdnMatcher.find()) {
                String cellFdn = null;
                if(filter.asString().contains("GNBDUFunction")) {
                    cellFdn =
                            String.format("ManagedElement=%s,GNBDUFunction=%s,NRSectorCarrier=%s", cellFdnMatcher.group(1), cellFdnMatcher.group(2),
                                    cellFdnMatcher.group(3));
                } else {
                    cellFdn =
                            String.format("ManagedElement=%s,ENodeBFunction=%s,EUtranCellTDD=%s", cellFdnMatcher.group(1), cellFdnMatcher.group(2),
                                    cellFdnMatcher.group(3));
                }

                final Instant cellTxExpireTime = topology.getCellTxExpireTime(cellFdn);
                final String cellId = cellFdnMatcher.group(3);

                withWriteLock(() -> {
                    out.print(RpcReplyFormat.XML_START);
                    out.print("<rpc-reply message-id=\"");
                    out.print(messageId);
                    out.print("\"");
                    out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
                    out.print("\t<data>");
                    out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
                    out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
                    if(filter.asString().contains("GNBDUFunction")) {
                        out.print("\t<GNBDUFunction xmlns=\"urn:com:ericsson:ecim:Lrat\">");
                        out.print("\t<gNBDUFunctionId>1</gNBDUFunctionId>");
                        out.print("\t<NRSectorCarrier>");
                        out.print("\t<nRSectorCarrierId>" + cellId + "</nRSectorCarrierId>");
                        out.print("\t<cbrsTxExpireTime>");
                        out.print(cellTxExpireTime == null ? "" : getDate(Date.from(cellTxExpireTime)));
                        out.print("</cbrsTxExpireTime>");
                        out.print("\t</NRSectorCarrier>");
                        out.print("\t</GNBDUFunction>");
                    } else {
                        out.print("\t<ENodeBFunction xmlns=\"urn:com:ericsson:ecim:Lrat\">");
                        out.print("\t<eNodeBFunctionId>1</eNodeBFunctionId>");
                        out.print("\t<EUtranCellTDD>");
                        out.print("\t<eUtranCellTDDId>" + cellId + "</eUtranCellTDDId>");
                        out.print("\t<cbrsTxExpireTime>");
                        out.print(cellTxExpireTime == null ? "" : getDate(Date.from(cellTxExpireTime)));
                        out.print("</cbrsTxExpireTime>");
                        out.print("\t</EUtranCellTDD>");
                        out.print("\t</ENodeBFunction>");
                    }
                    out.print("\t</ManagedElement>");
                    out.print("\t</data>");
                    out.print("</rpc-reply>");
                    out.println(NetconfConstants.ENDSTRING);
                    out.flush();
                });
            } else {
                sendNodeData(messageId, filter, out);
            }
        } else if (filter != null && filter.asString() != null && filter.asString().contains("<RcvdPowerScanner")) {
            logger.debug("{}:{}> received request to get RcvdPowerScanner", getNodeName(), this.sessionId);
            final Matcher fruMatcher = FRU_ID_PATTERN.matcher(filter.asString());
            final Matcher equipMatcher = EQUIPMENT_ID_PATTERN.matcher(filter.asString());
            if (fruMatcher.find() && equipMatcher.find()) {
                final String fruId = fruMatcher.group(1);
                final String equipmentId = equipMatcher.group(1);
                final String fruFdn =
                        String.format("ManagedElement=%s,Equipment=%s,FieldReplaceableUnit=%s", nodeDefinition.getName(), equipmentId,
                                fruId);
                final int bandwidthKhz = nodeDefinition.getMeasurements().getBandwidthMhz() * 1000;
                final int powerMin = nodeDefinition.getMeasurements().getMinPowerDbm() * 10;
                final int powerMax = nodeDefinition.getMeasurements().getMaxPowerDbm() * 10;
                final Fru.RcvdPowerScanner measurement = topology.getMeasurement(fruFdn, bandwidthKhz, powerMin, powerMax,
                        Duration.ofSeconds(nodeDefinition.getMeasurements().getDurationSeconds()));
                logger.debug("{}:{}> RcvdPowerScanner generated measurement result. result={}", getNodeName(), this.sessionId, measurement);

                try {
                    final Marshaller marshaller = jaxbContext.createMarshaller();
                    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
                    final StringWriter writer = new StringWriter();
                    marshaller.marshal(measurement, writer);
                    final String powerScannerData = writer.toString();
                    logger.debug("{}:{}> RcvdPowerScanner data generated. data={}", getNodeName(), this.sessionId, powerScannerData);
                    withWriteLock(() -> {
                        out.print(RpcReplyFormat.XML_START);
                        out.print("<rpc-reply message-id=\"");
                        out.print(messageId);
                        out.print("\"");
                        out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
                        out.print("\t<data>");
                        out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
                        out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
                        out.print("\t<Equipment xmlns=\"urn:com:ericsson:ecim:ReqEquipment\">");
                        out.print("\t<equipmentId>");
                        out.print(equipmentId);
                        out.print("</equipmentId>");
                        out.print("\t<FieldReplaceableUnit xmlns=\"urn:com:ericsson:ecim:ReqFieldReplaceableUnit\">");
                        out.print("\t<fieldReplaceableUnitId>");
                        out.print(fruId);
                        out.print("</fieldReplaceableUnitId>");
                        out.print(powerScannerData);
                        out.print("</FieldReplaceableUnit>");
                        out.print("\t</Equipment>");
                        out.print("\t</ManagedElement>");
                        out.print("\t</data>");
                        out.print("</rpc-reply>");
                        out.println(NetconfConstants.ENDSTRING);
                        out.flush();
                    });
                } catch (JAXBException e) {
                    logger.error("{}:{}> Error during XML generation.", getNodeName(), this.sessionId, e);
                    sendNodeData(messageId, filter, out);
                }
            } else {
                logger.warn("{}:{}> Could not extract FRU fdn from filter. filter={}", getNodeName(), this.sessionId, filter.asString());
                sendNodeData(messageId, filter, out);
            }
        } else if (filter != null && filter.asString() != null && filter.asString().contains("dnPrefix")) {
            logger.debug("{}:{}> Sending dnPrefix {}..", sessionId, getNodeName(), getNodeName());
            withWriteLock(() -> {
                out.print(RpcReplyFormat.XML_START);
                out.print("<rpc-reply message-id=\"");
                out.print(messageId);
                out.print("\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
                out.print("\t<data>");
                out.print("    <ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
                out.print("        <managedElementId>");
                out.print(nodeDefinition.getName());
                out.print("</managedElementId>");
                out.print("        <dnPrefix/>");
                out.print("    </ManagedElement>");
                out.print("\t</data>");
                out.print("</rpc-reply>");
                out.println(NetconfConstants.ENDSTRING);
                out.flush();
            });
        } else {
            sendNodeData(messageId, filter, out);
        }
        final long duration = System.currentTimeMillis() - startAt;
        logger.info("{}:{}> END GET: END OK!!  duration={} millis", getNodeName(), this.sessionId, duration);
    }

    private void sendNodeData(final String messageId, final Filter filter, final PrintWriter out) {
        final String dataToSend = getNodeDataToSend(filter);

        withWriteLock(() -> {
            out.print(RpcReplyFormat.XML_START);
            out.print("<rpc-reply message-id=\"");
            out.print(messageId);
            out.print("\"");
            out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
            out.print(dataToSend);
            out.print("</rpc-reply>");
            out.println(NetconfConstants.ENDSTRING);
            out.flush();
        });
    }

    private String getNodeDataToSend(final Filter filter) {
        final String nodeData = nodeDataProducer.apply(nodeDefinition);
        String dataToSend = nodeData;
        if (filter != null && filter.asString() != null && "subtree".equalsIgnoreCase(filter.getType())) {
            final String filterText = "<data>" + filter.asString().trim() + "</data>";
            final XmlSubtreeFilter subtreeFilter = new XmlSubtreeFilter(filterText);
            try {
                if (subtreeFilter.hasFilter()) {
                    dataToSend = subtreeFilter.filter(nodeData);
                    logger.trace("{}:{}> Node data filtered. filter={}, result={}, size-before={}, size-after={}", getNodeName(), sessionId, subtreeFilter.getFilterText(), dataToSend,
                            nodeData.length(), dataToSend.length());
                }
            } catch (XMLStreamException e) {
                logger.warn("{}:{}>Error during GET with Filter execution.", getNodeName(), sessionId, e);
            }
        }
        return dataToSend;
    }

    @Override
    public void editConfig(final String messageId, final Datastore source, final DefaultOperation defaultOpertaion, final ErrorOption errorOption,
                           final TestOption testOption, final String config, final PrintWriter out) {
        logger.debug("{}:{}> Got edit config.  config={}", getNodeName(), this.sessionId, config);
        if (config.contains("channelBandwidth")) {
            logger.info("{}:{}> Got edit config with channelBandwidth", getNodeName(), this.sessionId);
            RadioNodeCbrsDataReader.updateBandwidth(config, topology);
        } else if (config.contains("bSChannelBwDL") || config.contains("bSChannelBwUL")) {
            logger.info("{}:{}> Got edit config with bSChannelBwDL/bSChannelBwUL", getNodeName(), this.sessionId);
            RadioNodeCbrsDataReader.updateBandwidthDownlink(config, topology);
            RadioNodeCbrsDataReader.updateBandwidthUplink(config, topology);
        } else if (config.contains("configuredMaxTxPower")){
            logger.info("{}:{}> Got edit config with configuredMaxTxPower", getNodeName(), this.sessionId);
            RadioNodeCbrsDataReader.updateConfiguredMaxTxPower(config, topology);
        } else if (config.contains("arfcnDL") || config.contains("arfcnUL")) {
            logger.info("{}:{}> Got edit config with arfcnDL/arfcnUL", getNodeName(), this.sessionId);
            RadioNodeCbrsDataReader.updateArfcnDownlink(config, topology);
            RadioNodeCbrsDataReader.updateArfcnUplink(config, topology);
        }
        withWriteLock(() -> {
            super.editConfig(messageId, source, defaultOpertaion, errorOption, testOption, config, out);
            out.flush();
        });
    }

    @Override
    public void closeSession(final String messageId, final PrintWriter out) {
        logger.info("{}:{}> Closing session.", getNodeName(), sessionId);
        this.stopping.set(true);
        withWriteLock(() -> {
            super.closeSession(messageId, out);
            out.flush();
        });
    }

    @Override
    public void action(final String messageId, final String actionMessage, final PrintWriter out) {
        logger.debug("{}:{}> action called: {}", getNodeName(), this.sessionId, actionMessage);
        if (actionMessage.contains("setCbrsTxExpireTime")) {
            logger.info("{}:{}> setCbrsTxExpireTime called", getNodeName(), this.sessionId);
            String cellFdn = null;
            long txDurationSeconds = 0;
            Matcher cellFdnMatcher = null;
            if(actionMessage.contains("GNBDUFunction")) {
                cellFdnMatcher = NR_CELL_SECTOR_CARRIER_FDN_PATTERN.matcher(actionMessage);
            } else {
                cellFdnMatcher = LTE_CELL_FDN_PATTERN.matcher(actionMessage);
            }

            if (cellFdnMatcher.find()) {
                final Matcher txExpireMatcher = TX_EXPIRE_PATTERN.matcher(actionMessage);
                if (txExpireMatcher.find()) {
                    if(actionMessage.contains("GNBDUFunction")) {
                        cellFdn =
                                String.format("ManagedElement=%s,GNBDUFunction=%s,NRSectorCarrier=%s", cellFdnMatcher.group(1), cellFdnMatcher.group(2),
                                        cellFdnMatcher.group(3));
                        txDurationSeconds = Long.parseLong(txExpireMatcher.group(1));
                        String cellId = cellFdnMatcher.group(3);
                        withWriteLock(() -> {
                            actionNRCbrsTxExpiryReply(messageId, cellId, out);
                        });
                    } else {
                        cellFdn =
                                String.format("ManagedElement=%s,ENodeBFunction=%s,EUtranCellTDD=%s", cellFdnMatcher.group(1), cellFdnMatcher.group(2),
                                        cellFdnMatcher.group(3));
                        txDurationSeconds = Long.parseLong(txExpireMatcher.group(1));
                        String cellId = cellFdnMatcher.group(3);
                        withWriteLock(() -> {
                            actionCbrsTxExpiryReply(messageId, cellId, out);
                        });
                    }

                    topology.setCellTxEpireTime(cellFdn, txDurationSeconds);
                } else {
                    logger.error("{}:{}> Could not extract tx-expire time from message: {}", getNodeName(), this.sessionId, actionMessage);
                }
            } else {
                logger.error("{}:{}> Could not extract cell fdn from message: {}", getNodeName(), this.sessionId, actionMessage);
            }
//            if (cellFdn != null) {
//                final Date txExpiration = Date.from(Instant.now().plusSeconds(txDurationSeconds));
//                notifyAttributeChanged(cellFdn, "cbrsTxExpireTime", getDate(txExpiration));
//            }
        } else if (actionMessage.contains("changeFrequency")) {
            logger.info("{}:{}> Change frequency called", getNodeName(), this.sessionId);
            String cellFdn = null;
            long earfcn = 0;
            long arfcnDL = 0;
            long arfcnUL = 0;
            Matcher cellFdnMatcher = null;
            if (actionMessage.contains("GNBDUFunction")) {
                cellFdnMatcher = NR_CELL_SECTOR_CARRIER_FDN_PATTERN.matcher(actionMessage);
            } else {
                cellFdnMatcher = LTE_CELL_FDN_PATTERN.matcher(actionMessage);
            }
            if (cellFdnMatcher.find()) {
                if (actionMessage.contains("GNBDUFunction")) {
                    final Matcher freqMatcherForaArfcnDL = ARFCNDL_PATTERN.matcher(actionMessage);
                    final Matcher freqMatcherForaArfcnUL = ARFCNUL_PATTERN.matcher(actionMessage);
                    cellFdn =
                            String.format("ManagedElement=%s,GNBDUFunction=%s,NRSectorCarrier=%s", cellFdnMatcher.group(1), cellFdnMatcher.group(2),
                                    cellFdnMatcher.group(3));
                    if (freqMatcherForaArfcnDL.find()) {
                        arfcnDL = Long.parseLong(freqMatcherForaArfcnDL.group(1));
                        topology.setCellArfcnDL(cellFdn, arfcnDL);
                    } else {
                        logger.error("{}:{}> Could not extract arfcnDL from message: {}", getNodeName(), this.sessionId, actionMessage);
                    }

                    if (freqMatcherForaArfcnUL.find()) {
                        arfcnUL = Long.parseLong(freqMatcherForaArfcnUL.group(1));
                        topology.setCellArfcnUL(cellFdn, arfcnUL);
                    } else {
                        logger.error("{}:{}> Could not extract arfcnUL from message: {}", getNodeName(), this.sessionId, actionMessage);
                    }

                } else{
                    final Matcher freqMatcher = EARFCN_PATTERN.matcher(actionMessage);
                    if (freqMatcher.find()) {
                        cellFdn =
                                String.format("ManagedElement=%s,ENodeBFunction=%s,EUtranCellTDD=%s", cellFdnMatcher.group(1), cellFdnMatcher.group(2),
                                        cellFdnMatcher.group(3));
                        earfcn = Long.parseLong(freqMatcher.group(1));
                        topology.setCellEarfcn(cellFdn, earfcn);
                    } else {
                        logger.error("{}:{}> Could not extract earfcn from message: {}", getNodeName(), this.sessionId, actionMessage);
                    }
                }

            } else {
                logger.error("{}:{}> Could not extract cell fdn from message: {}", getNodeName(), this.sessionId, actionMessage);
            }
            final String cellId = cellFdnMatcher.group(3);
            if(actionMessage.contains("GNBDUFunction")){
                withWriteLock(() -> {
                    actionNRChangeFrequencyReply(messageId, cellId, out);
                });
            }
            else {
                withWriteLock(() -> {
                    actionChangeFrequencyReply(messageId, cellId, out);
                });
            }
            if (cellFdn != null) {
                if (actionMessage.contains("GNBDUFunction")) {
                    notifyAttributeChanged(cellFdn, "arfcnDL", String.valueOf(arfcnDL));
                    notifyAttributeChanged(cellFdn, "arfcnUL", String.valueOf(arfcnUL));
                } else {
                    notifyAttributeChanged(cellFdn, "earfcn", String.valueOf(earfcn));
                }
            }
        } else if (actionMessage.contains("<scanRcvdPower>")){
            logger.debug("{}:{}> scanRcvdPower action called: {}", getNodeName(), this.sessionId, actionMessage);
            final Matcher freqMatcher = FREQ_PATTERN.matcher(actionMessage);
            final Matcher bandwidthMatcher = BANDWIDTH_PATTERN.matcher(actionMessage);
            int lowerFreq = Integer.MAX_VALUE;
            int higherFreq = 0;
            int lastBandwidth = 0;
            while (freqMatcher.find()) {
                final int freq = Integer.parseInt(freqMatcher.group(1));
                lowerFreq = Math.min(lowerFreq, freq);
                higherFreq = Math.max(higherFreq, freq);
            }

            if (bandwidthMatcher.find()) {
                lastBandwidth = Integer.parseInt(bandwidthMatcher.group(1));
                while (bandwidthMatcher.find()) {
                    lastBandwidth = Integer.parseInt(bandwidthMatcher.group(1));
                }

                if (higherFreq == 0 || lowerFreq == Integer.MAX_VALUE) {
                    logger.error("{}:{}> Could not read frequencies from request. actionMessage={}", getNodeName(), this.sessionId, actionMessage);
                    sendError(messageId, String.format(RPC_ERROR_FAILED_TO_PARSE_XML, messageId, "Missing element: freq"), out);
                } else {
                    final Matcher fruMatcher = FRU_ID_PATTERN.matcher(actionMessage);
                    final Matcher equipMatcher = EQUIPMENT_ID_PATTERN.matcher(actionMessage);
                    if (fruMatcher.find() && equipMatcher.find()) {
                        final String fruId = fruMatcher.group(1);
                        final String fruFdn =
                                String.format("ManagedElement=%s,Equipment=%s,FieldReplaceableUnit=%s", nodeDefinition.getName(), equipMatcher.group(1),
                                        fruId);
                        final int scanId = topology.startMeasurement(fruFdn, lowerFreq, higherFreq + lastBandwidth);
                        withWriteLock(() -> actionStartRcvdPowerScanReply(messageId, fruId, scanId, out));
                    } else {
                        logger.error("{}:{}> Could not read FRU fdn from request. actionMessage={}", getNodeName(), this.sessionId, actionMessage);
                        sendError(messageId, String.format(RPC_ERROR_FAILED_TO_PARSE_XML, "Missing element fdn."), out);
                    }
                }
            } else {
                logger.error("{}:{}> Could not read bandwidth from request. actionMessage={}", getNodeName(), this.sessionId, actionMessage);
                sendError(messageId, String.format(RPC_ERROR_FAILED_TO_PARSE_XML, messageId, "Missing element: bandwidth"), out);
            }
        } else {
            withWriteLock(() -> super.action(messageId, actionMessage, out));
        }
        out.flush();
    }

    private void actionChangeFrequencyReply(final String messageId, final String cellId, final PrintWriter out) {
        out.print(RpcReplyFormat.XML_START);
        out.print("<rpc-reply message-id=\"");
        out.print(messageId);
        out.print("\"");
        out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
        out.print("\t<data>");
        out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
        out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
        out.print("\t<ENodeBFunction xmlns=\"urn:com:ericsson:ecim:Lrat\">");
        out.print("\t<eNodeBFunctionId>1</eNodeBFunctionId>");
        out.print("\t<EUtranCellTDD>");
        out.print("\t<eUtranCellTDDId>" + cellId + "</eUtranCellTDDId>");
        out.print("\t<changeFrequency>");
        out.print("\t<returnValue>");
        out.print("\t<void></void>");
        out.print("\t</returnValue>");
        out.print("\t</changeFrequency>");
        out.print("\t</EUtranCellTDD>");
        out.print("\t</ENodeBFunction>");
        out.print("\t</ManagedElement>");
        out.print("\t</data>");
        out.print("</rpc-reply>");
        out.println(NetconfConstants.ENDSTRING);
        out.flush();
    }

    private void actionNRChangeFrequencyReply(final String messageId, final String cellId, final PrintWriter out) {
        out.print(RpcReplyFormat.XML_START);
        out.print("<rpc-reply message-id=\"");
        out.print(messageId);
        out.print("\"");
        out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
        out.print("\t<data>");
        out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
        out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
        out.print("\t<GNBDUFunction xmlns=\"urn:com:ericsson:ecim:Lrat\">");
        out.print("\t<gNBDUFunctionId>1</gNBDUFunctionId>");
        out.print("\t<NRSectorCarrier>");
        out.print("\t<nRSectorCarrierId>" + cellId + "</nRSectorCarrierId>");
        out.print("\t<changeFrequency>");
        out.print("\t<returnValue>");
        out.print("\t<void></void>");
        out.print("\t</returnValue>");
        out.print("\t</changeFrequency>");
        out.print("\t</NRSectorCarrier>");
        out.print("\t</GNBDUFunction>");
        out.print("\t</ManagedElement>");
        out.print("\t</data>");
        out.print("</rpc-reply>");
        out.println(NetconfConstants.ENDSTRING);
        out.flush();
    }

    private void actionCbrsTxExpiryReply(final String messageId, final String cellId, final PrintWriter out) {
        out.print(RpcReplyFormat.XML_START);
        out.print("<rpc-reply message-id=\"");
        out.print(messageId);
        out.print("\"");
        out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
        out.print("\t<data>");
        out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
        out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
        out.print("\t<ENodeBFunction xmlns=\"urn:com:ericsson:ecim:Lrat\">");
        out.print("\t<eNodeBFunctionId>1</eNodeBFunctionId>");
        out.print("\t<EUtranCellTDD>");
        out.print("\t<eUtranCellTDDId>" + cellId + "</eUtranCellTDDId>");
        out.print("\t<setCbrsTxExpireTime>");
        out.print("\t<returnValue>");
        out.print("\t<void></void>");
        out.print("\t</returnValue>");
        out.print("\t</setCbrsTxExpireTime>");
        out.print("\t</EUtranCellTDD>");
        out.print("\t</ENodeBFunction>");
        out.print("\t</ManagedElement>");
        out.print("\t</data>");
        out.print("</rpc-reply>");
        out.println(NetconfConstants.ENDSTRING);
        out.flush();
    }

    private void actionNRCbrsTxExpiryReply(final String messageId, final String cellId, final PrintWriter out) {
        out.print(RpcReplyFormat.XML_START);
        out.print("<rpc-reply message-id=\"");
        out.print(messageId);
        out.print("\"");
        out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
        out.print("\t<data>");
        out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
        out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
        out.print("\t<GNBDUFunction xmlns=\"urn:com:ericsson:ecim:Lrat\">");
        out.print("\t<gNBDUFunctionId>1</gNBDUFunctionId>");
        out.print("\t<NRSectorCarrier>");
        out.print("\t<nRSectorCarrierId>" + cellId + "</nRSectorCarrierId>");
        out.print("\t<setCbrsTxExpireTime>");
        out.print("\t<returnValue>");
        out.print("\t<void></void>");
        out.print("\t</returnValue>");
        out.print("\t</setCbrsTxExpireTime>");
        out.print("\t</NRSectorCarrier>");
        out.print("\t</GNBDUFunction>");
        out.print("\t</ManagedElement>");
        out.print("\t</data>");
        out.print("</rpc-reply>");
        out.println(NetconfConstants.ENDSTRING);
        out.flush();
    }

    private void actionStartRcvdPowerScanReply(final String messageId, final String fruId, final int scanId, final PrintWriter out) {
        out.print(RpcReplyFormat.XML_START);
        out.print("<rpc-reply message-id=\"");
        out.print(messageId);
        out.print("\"");
        out.print("\txmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">");
        out.print("\t<data>");
        out.print("\t<ManagedElement xmlns=\"urn:com:ericsson:ecim:ComTop\">");
        out.print("\t<managedElementId>" + nodeDefinition.getName() + "</managedElementId>");
        out.print("\t<Equipment xmlns=\"urn:com:ericsson:ecim:ReqEquipment\">");
        out.print("\t<equipmentId>1</equipmentId>");
        out.print("\t<FieldReplaceableUnit xmlns=\"urn:com:ericsson:ecim:ReqFieldReplaceableUnit\">");
        out.print("\t<fieldReplaceableUnitId>" + fruId + "</fieldReplaceableUnitId>");
        out.print("\t<RcvdPowerScanner xmlns=\"urn:com:ericsson:ecim:ReqRcvdPowerScanner\">");
        out.print("\t<rcvdPowerScannerId>1</rcvdPowerScannerId>");
        out.print("\t<scanRcvdPower>");
        out.print("\t<returnValue>");
        out.print(scanId);
        out.print("</returnValue>");
        out.print("\t</scanRcvdPower>");
        out.print("\t</RcvdPowerScanner>");
        out.print("\t</FieldReplaceableUnit>");
        out.print("\t</Equipment>");
        out.print("\t</ManagedElement>");
        out.print("\t</data>");
        out.print("</rpc-reply>");
        out.println(NetconfConstants.ENDSTRING);
        out.flush();
    }

    @Override
    public Callable<Boolean> createSubscription(final String messageId, final String stream, final Filter filter, final String startTime,
                                                final String stopTime, final PrintWriter out) {
        logger.info("{}:{}> Creating subscription. sessionId={}", getNodeName(), this.sessionId, sessionId);
        withWriteLock(() -> {
            out.print(String.format(RpcReplyFormat.RPC_OK, messageId));
            out.println(NetconfConstants.ENDSTRING);
            out.flush();
        });
        return null;
    }

    @Override
    public void getConfig(final String messageId, final Datastore source, final Filter filter, final PrintWriter out) {
        logger.info("{}:{}> getConfig", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.getConfig(messageId, source, filter, out);
        });
    }

    @Override
    public void killSession(final String messageId, final int sessionId, final PrintWriter out) {
        logger.info("{}:{}> kill session. target={}", getNodeName(), this.sessionId, sessionId);
        withWriteLock(() -> {
            super.killSession(messageId, sessionId, out);
        });
    }

    @Override
    public void lock(final String messageId, final String sessionid, final Datastore target, final PrintWriter out) {
        logger.info("{}:{}> lock", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.lock(messageId, sessionid, target, out);
        });
    }

    @Override
    public void unlock(final String messageId, final Datastore target, final PrintWriter out) {
        logger.info("{}:{}> unlock", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.unlock(messageId, target, out);
        });
    }

    @Override
    public void sendError(final String messageId, final String rpcError, final PrintWriter out) {
        withWriteLock(() -> {
            super.sendError(messageId, rpcError, out);
        });
    }

    @Override
    public void validate(final String messageId, final Datastore source, final PrintWriter out) {
        logger.info("{}:{}> validate", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.validate(messageId, source, out);
        });
    }

    @Override
    public void commit(final String messageId, final PrintWriter out) {
        logger.info("{}:{}> commit", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.commit(messageId, out);
        });
    }

    @Override
    public void discardChanges(final String messageId, final PrintWriter out) {
        logger.info("{}:{}> discardChanges", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.discardChanges(messageId, out);
        });
    }

    @Override
    public void customOperation(final String messageId, final String requestBody, final PrintWriter out) {
        logger.info("{}:{}> customOperation1", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.customOperation(messageId, requestBody, out);
        });
    }

    @Override
    public void customOperation(final String messageId, final String requestBody, final boolean returnResponse, final PrintWriter out) {
        logger.info("{}:{}> customOperation2", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.customOperation(messageId, requestBody, returnResponse, out);
        });
    }

    @Override
    public void copyConfig(final String messageId, final String source, final String target, final PrintWriter out) {
        logger.info("{}:{}> copyConfig", getNodeName(), this.sessionId);
        withWriteLock(() -> {
            super.copyConfig(messageId, source, target, out);
        });
    }

    @Override
    public void getSchema(final String messageId, final String identifier, final String version, final String format, final PrintWriter out) {
        logger.info("{}:{}> getSchema", getNodeName(), this.sessionId);
        super.getSchema(messageId, identifier, version, format, out);
    }

    @Override
    public void clientHello(final List<String> capabilities, final PrintWriter out) {
        logger.info("{}:{}> clientHello", getNodeName(), this.sessionId);
        super.clientHello(capabilities, out);
    }

    private void notifyAttributeChanged(final String moFdn, final String attName, final String attValue) {
        sendAVC(moFdn, attName, attValue);
    }

    private void withWriteLock(final Runnable runnable) {
        runnable.run();
    }

    private String getDate() {
        return getDate(new Date());
    }

    private String getDate(final Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return dateFormat.format(date);
    }

    private void sendAVC(final String fnd, final String att, final String value) {
        notificationPublisher.publishNotification(singleNotification(avc(fnd, att, value)));
        logger.trace("AVC notification sent. fdn={}, att={}, value={}", fnd, att, value);
    }

    private String singleNotification(final String notificationType) {
        return RpcReplyFormat.XML_START +
                "<notification xmlns=\"urn:ietf:params:xml:ns:netconf:notification:1.0\">" +
                notificationType +
                "</notification>]]>]]>";

    }

    private String avc(final String fdn, final String att, final String value) {
        return "<eventTime>" + getDate() + "</eventTime>\n" +
                "<events xmlns=\"urn:ericsson:com:netconf:notification:1.0\">" +
                String.format("<AVC dn=\"%s\"> <attr name=\"%s\"><v>%s</v></attr></AVC>", fdn, att, value) +
                "</events>";
    }

    private String getNodeName() {
        return nodeDefinition.getName();
    }
}