package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class Topology {
    private static final Logger logger = LoggerFactory.getLogger(Topology.class);
    public static final String DEFAULT_FCCID = "FCCID161711-1";
    private final Reporter reporter;
    private final Map<String, Node> nodes = new LinkedHashMap<>(1000);
    private final Map<String, Cbsd> cbsds = new ConcurrentHashMap<>();
    private final Map<String, Cell> cells = new ConcurrentHashMap<>();
    private final Map<String, Cbsd> cbsdIdRegistry = new ConcurrentHashMap<>();
    private final Map<String, Iru> irus = new ConcurrentHashMap<>();
    private final Map<String, Fru> frus = new ConcurrentHashMap<>();
    private final Set<String> untrackedIds = new HashSet<>();

    public void addNode(final String name, final String address, final int port, final long latencyInMillis) {
        nodes.computeIfAbsent(name, key -> new Node(name, address, port));
        nodes.get(name).setLatencyMillis(latencyInMillis);
    }

    public void addCellAndCbsd(final String nodeName, final String cellId, final String cbsdSerial) {
        logger.info("Adding cell and CBSD to topology. CBSD={}, Cell={}, node={}", cbsdSerial, cellId, nodeName);
        final Cell cell = cells.computeIfAbsent(cellId, key -> new Cell(cellId, reporter));
        final Cbsd cbsd = cbsds.computeIfAbsent(cbsdSerial, key -> new Cbsd(cbsdSerial, reporter));

        cell.addCbsd(cbsd);
        nodes.computeIfPresent(nodeName, (key, node) -> {
            node.getCells().add(cell);
            return node;
        });
    }

    public void addIruAndPort(final String iruId, final String rdiPortId, final String rdiPortRefId) {
        irus.computeIfAbsent(iruId, Iru::new)
                .addPortMap(rdiPortId, rdiPortRefId);
    }

    public void addIruWithSerialNumber(final String iruId, final String serialNumber) {
        irus.computeIfAbsent(iruId, Iru::new).setSerialNumber(serialNumber);
    }

    public void addIru(final String iruId) {
        irus.computeIfAbsent(iruId, Iru::new);
    }

    public void addFruWithSerialNumber(final String fru, final String serialNumber) {
        frus.computeIfAbsent(fru, Fru::new).setSerialNumber(serialNumber);
    }

    public void addFru(final String fruId) {
        frus.computeIfAbsent(fruId, Fru::new);
    }

    public void setCellFrequency(final String cellId, long frequencyStartHz, long frequencyEndHz) {
        withCell(cellId, cell -> cell.setFrequencyRangeHz(new FrequencyRangeHz(frequencyStartHz, frequencyEndHz)));
    }

    public void setCellEarfcn(final String cellId, long earfcn) {
        logger.info("Setting cell Earfcn. cell={}, Earfcn={}", cellId, earfcn);
        withCell(cellId, cell -> cell.setCellEarfcn(earfcn));
    }

    public void setCellArfcnUL(final String cellId, long arfcnUL) {
        logger.info("Setting cell ArfcnUL. cell={}, ArfcnUL={}", cellId, arfcnUL);
        withCell(cellId, cell -> cell.setCellArfcnUL(arfcnUL));
    }

    public void setCellArfcnDL(final String cellId, long arfcnDL) {
        logger.info("Setting cell ArfcnDL. cell={}, ArfcnDL={}", cellId, arfcnDL);
        withCell(cellId, cell -> cell.setCellArfcnDL(arfcnDL));
    }

    public void setCellBandwidthHz(final String cellId, long bandwidthHz) {
        logger.info("Setting cell BandwidthHz. cell={}, BandwidthHz={}", cellId, bandwidthHz);
        withCell(cellId, cell -> cell.setChannelBandwidthHz(bandwidthHz));
    }

    public void setCellTxEpireTime(final String cellId, final long txDurationSeconds) {
        logger.debug("Setting cell expire time. cell={}, expiration={} sec.", cellId, txDurationSeconds);
        withCell(cellId, cell -> cell.updateTxExpireTime(txDurationSeconds));
    }

    public Instant getCellTxExpireTime(final String cellId) {
        logger.debug("Getting cell expire time. cell={}", cellId);
        final Cell cell = cells.get(cellId);
        if (cell != null) {
            return cell.getTxExpirationTime();
        }
        return null;
    }

    public void registerCbsdId(final String cbsdSerial, final String cbsdId) {
        logger.info("Registering cbsd. cbsd-serial={}, cbsd-id={}", cbsdSerial, cbsdId);
        final Cbsd cbsd = cbsds.get(cbsdSerial);
        if (cbsd == null) {
            if (untrackedIds.add(cbsdId)) {
                reporter.reportUntrackedCbsd(cbsdSerial, cbsdId);
            }
        } else {
            cbsdIdRegistry.put(cbsdId, cbsd);
            cbsd.setAsRegistered(cbsdId);
        }
    }

    public void unregisterCbsdId(final String cbsdId) {
        logger.info("Unregistering cbsd. cbsd-id={}", cbsdId);
        final Cbsd cbsd = cbsdIdRegistry.remove(cbsdId);
        if (cbsd != null) {
            cbsd.setAsUnregistered();
        }
    }

    public void addGrant(final String cbsdId, final FrequencyRangeHz frequencyRange, final String grantId, final Instant grantExpireTime,
                         final long heartbeatInterval) {
        logger.debug("Adding grant. cbsd-id={}, frequency-range={}, grant-id={}, grant-expire={}, hb-interval={}", cbsdId, frequencyRange, grantId,
                grantExpireTime, heartbeatInterval);
        withCbsd(cbsdId, cbsd -> cbsd.addGrant(frequencyRange, grantId, GrantState.GRANTED, grantExpireTime, heartbeatInterval));
    }

    public void addIdleGrant(final String cbsdId, final FrequencyRangeHz frequencyRange) {
        logger.debug("Adding IDLE grant. cbsd-id={}, frequency-range={}", cbsdId, frequencyRange);
        withCbsd(cbsdId, cbsd -> cbsd.addGrant(frequencyRange, null, GrantState.IDLE, null, 60));
    }

    public Integer grantHeartbeatSucceed(final String cbsdId, final String grantId, final Instant transmitExpireTime, final Instant grantExpireTime,
                                      final Long heartbeatInterval, final GrantState requestGrantState) {
        logger.debug("Heartbeat succeed. cbsd-id={}, grant-id={}, tx-expire={}, grant-expire={}, hb-interval={}", cbsdId, grantId, transmitExpireTime,
                grantExpireTime, heartbeatInterval);
        return fromCbsd(cbsdId, cbsd -> cbsd.grantHeartbeatSucceed(grantId, transmitExpireTime, grantExpireTime, heartbeatInterval, requestGrantState));
    }

    public void grantHeartbeatSuspended(final String cbsdId, final String grantId, final Instant transmitExpireTime, final Instant grantExpireTime, final Long heartbeatInterval) {
        logger.debug("Heartbeat suspended. cbsd-id={}, grant-id={}, tx-expire={}, grant-expire={}, hb-interval={}", cbsdId, grantId, transmitExpireTime, grantExpireTime,
                heartbeatInterval);
        withCbsd(cbsdId, cbsd -> cbsd.grantHeartbeatSuspended(grantId, transmitExpireTime, grantExpireTime, heartbeatInterval));
    }

    public void grantHeartbeatFailed(final String cbsdId, final String grantId, final Instant transmitExpireTime) {
        logger.debug("Heartbeat failed. cbsd-id={}, grant-id={}, tx-expire={}", cbsdId, grantId, transmitExpireTime);
        withCbsd(cbsdId, cbsd -> cbsd.grantHeartbeatFailed(grantId, transmitExpireTime));
    }

    public void relinquishGrant(final String cbsdId, final String grantId) {
        logger.debug("Relinquishing grant. cbsd-id={}, grant-id={}", cbsdId, grantId);
        withCbsd(cbsdId, cbsd -> cbsd.relinquishGrant(grantId));
    }

    public void performPeriodicChecks() {
        for (final Cell cell : this.cells.values()) {
            cell.reportCellTransmittingState();
            cell.reportIfTransmissionInterrupted();
            cell.reportIfTransmissionBeyondPermitted();
        }
        for (final Cbsd cbsd : this.cbsds.values()) {
            cbsd.reportTimeouts();
        }
        reportTotalGrantsByState();
    }

    private void reportTotalGrantsByState() {
        final Map<String, Long> grantsByState = this.cbsds.values().stream()
                .map(Cbsd::getGrantCountByState)
                .reduce(new HashMap<>(), (stringLongMap, stringLongMap2) -> {
                    final HashMap<String, Long> merged = new HashMap<>(stringLongMap);
                    stringLongMap2.forEach((state, total) -> merged.compute(state, (k, acc) -> acc == null ? total : acc + total));
                    return merged;
                });
        reporter.reportTotalGrantsByState(grantsByState);
    }

    public void trySetCellEarfcn(final String cellId, long earfcn) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell Earfcn. cell={}, Earfcn={}", cellId, earfcn);
            cell.setCellEarfcn(earfcn);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellArfcnDL(final String cellId, long arfcnDL) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell ArfcnDL. cell={}, ArfcnDL={}", cellId, arfcnDL);
            cell.setCellArfcnDL(arfcnDL);
        } else {
            untrackedIds.add(cellId);
        }
    }


    public void trySetCellArfcnUL(final String cellId, long arfcnUL) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell ArfcnUL. cell={}, ArfcnUL={}", cellId, arfcnUL);
            cell.setCellArfcnUL(arfcnUL);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellSsbFrequencyAutoSelected(final String cellId, long ssbFrequencyAutoSelected) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell SsbFrequencyAutoSelected. cell={}, SsbFrequencyAutoSelected={}", cellId, ssbFrequencyAutoSelected);
            cell.setCellSsbFrequencyAutoSelected(ssbFrequencyAutoSelected);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellConfiguredMaxTxPower(final String cellId, long configuredMaxTxPower) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell ConfiguredMaxTxPower. cell={}, ConfiguredMaxTxPower={}", cellId, configuredMaxTxPower);
            cell.setCellconfiguredMaxTxPower(configuredMaxTxPower);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellSsbFrequency(final String cellId, long ssbFrequency) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell SsbFrequency. cell={}, SsbFrequency={}", cellId, ssbFrequency);
            cell.setCellSsbFrequency(ssbFrequency);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellBandwidthHz(final String cellId, long bandwidthHz) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell BandwidthHz. cell={}, BandwidthHz={}", cellId, bandwidthHz);
            cell.setChannelBandwidthHz(bandwidthHz);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellBSChannelBwDLHz(final String cellId, long bSChannelBwDL) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell BSChannelBwDLHz. cell={}, BSChannelBwDLHz={}", cellId, bSChannelBwDL);
            cell.setBSChannelBwDLHz(bSChannelBwDL);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellBSChannelBwULHz(final String cellId, long bSChannelBwUL) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell BSChannelBwULHz. cell={}, BSChannelBwULHz={}", cellId, bSChannelBwUL);
            cell.setBSChannelBwULHz(bSChannelBwUL);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCellCbrsEnabled(final String cellId, boolean enabled) {
        final Cell cell = this.cells.get(cellId);
        if (cell != null) {
            logger.info("Setting cell as cbrs enabled. cell={}, enabled={}", cellId, enabled);
            cell.setCbrsEnabled(enabled);
        } else {
            untrackedIds.add(cellId);
        }
    }

    public void trySetCbsdProductNumber(final String serialNumber, final String productNumber) {
        final Cbsd cbsd = this.cbsds.get(serialNumber);
        if (cbsd != null) {
            logger.info("Setting cbsd product number. cbsd-serial={}, product-number={}", serialNumber, productNumber);
            cbsd.setProductNumber(productNumber);
        }
    }

    public void trySetCbsdCpiData(final String serialNumber, final CbsdCpiData cpiData) {
        final Cbsd cbsd = this.cbsds.get(serialNumber);
        if (cbsd != null) {
            logger.info("Setting cbsd cpi data. cbsd-serial={}, cpi-data={}", serialNumber, cpiData);
            cbsd.setCpiData(cpiData);
        }
    }

    public void applyCpiDataByProductNumber(final Map<String, CbsdCpiData> productNumberToCpi) {
        CbsdCpiData defaultCpi = new CbsdCpiData("29.0", "0", false, DEFAULT_FCCID);
        final long updates = this.cbsds.values()
                .stream()
                .peek(cbsd -> {
                    if (cbsd.getCpiData() == null) {
                        if (cbsd.getProductNumber() == null) {
                            cbsd.setCpiData(defaultCpi);
                        } else {
                            cbsd.setCpiData(productNumberToCpi.getOrDefault(cbsd.getProductNumber(), defaultCpi));
                        }
                    } else if (cbsd.getCpiData().getFccid() == null) {
                        final CbsdCpiData currentCpi = cbsd.getCpiData();
                        if (cbsd.getProductNumber() == null) {
                            cbsd.setCpiData(new CbsdCpiData(currentCpi.getEirpCapability(), currentCpi.getAntennaGain(), currentCpi.isIndoorDeployment(), DEFAULT_FCCID));
                        } else {
                            final String fccid = productNumberToCpi.getOrDefault(cbsd.getProductNumber(), defaultCpi).getFccid();
                            cbsd.setCpiData(new CbsdCpiData(currentCpi.getEirpCapability(), currentCpi.getAntennaGain(), currentCpi.isIndoorDeployment(), fccid));
                        }
                    }
                }).count();
        logger.info("Update cpi by product on {} CBSDs", updates);
    }

    public int startMeasurement(final String fruOrIruId, final int frequencyStartKhz, final int frequencyEndKhz) {
        logger.debug("Starting measurement. fru={}, frequency-start={} kHz, frequency-end={} kHz", fruOrIruId, frequencyStartKhz, frequencyEndKhz);
        Fru fru = frus.get(fruOrIruId);
        if (fru == null) {
            fru = irus.get(fruOrIruId);
        }

        if (fru == null) {
            logger.error("Start measurement requested on unknown FRU. fru={}", fruOrIruId);
            return -2;
        } else {
            return fru.startScanRcvdPower(frequencyStartKhz, frequencyEndKhz);
        }
    }

    public Fru.RcvdPowerScanner getMeasurement(final String fruOrIruId, final int bandwidthKhz, final int powerMin, final int powerMax,
                                               final Duration scanSuration) {
        logger.debug("Getting measurements for fru. fru={}", fruOrIruId);
        Fru fru = frus.get(fruOrIruId);
        if (fru == null) {
            fru = irus.get(fruOrIruId);
        }

        if (fru == null) {
            logger.error("Start measurement requested on unknown FRU. fru={}", fruOrIruId);
            return null;
        } else {
            return fru.generateScanData(scanSuration, bandwidthKhz, powerMin, powerMax);
        }
    }

    public void reset() {
        logger.info("Resetting topology state.");
        cbsds.values().forEach(Cbsd::reset);
        cells.values().forEach(Cell::reset);
        cbsdIdRegistry.clear();
    }

    public List<Node> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<Cbsd> getCbsds() {
        return new ArrayList<>(cbsds.values());
    }

    public List<String> getGroupIds() {
        return this.nodes.values().stream().flatMap(node -> node.getGroupIds().stream()).collect(Collectors.toList());
    }

    public Optional<Node> getNode(final String nodeName) {
        return Optional.ofNullable(nodes.get(nodeName));
    }

    public void setNodeAsStarted(final String nodeName) {
        withNode(nodeName, node -> {
            node.setState(Node.State.STARTED);
            node.setRemarks(null);
        });
    }

    public void setNodeAsStopped(final String nodeName) {
        setNodeAsStopped(nodeName, null);
    }

    public void setNodeAsStopped(final String nodeName, final String remarks) {
        withNode(nodeName, node -> {
            node.setState(Node.State.STOPPED);
            node.setRemarks(remarks);
        });
    }

    public void setNodeManagement(final String nodeName, final NodeManagement nodeManagement) {
        withNode(nodeName, node -> node.setManagement(nodeManagement));
    }

    public void setNodeLatencyMillis(final String nodeName, final long latencyMillis) {
        withNode(nodeName, node -> node.setLatencyMillis(latencyMillis));
        logger.info("New latency {} is set for node {}",latencyMillis, nodeName);
    }

    public void nodeStop(final String nodeName) {
        withNode(nodeName, node -> {
            if (node.isManageable()) {
                node.getManagement().stop();
            }
        });
    }

    public void nodeStart(final String nodeName) {
        withNode(nodeName, node -> {
            if (node.isManageable()) {
                node.getManagement().start();
            }
        });
    }

    public void setRcvdPowerScanCapability(final String nodeName, final boolean rcvdPowerScanCapability) {
        withFrusForNode(nodeName, fru -> fru.setRcvdPowerScanCapability(rcvdPowerScanCapability));
    }

    private void withNode(final String nodeName, final Consumer<Node> nodeConsumer) {
        final Node node = nodes.get(nodeName);
        if (node != null) {
            nodeConsumer.accept(node);
        }
    }

    private void withCbsd(final String cbsdId, final Consumer<Cbsd> cbsdConsumer) {
        final Cbsd cbsd = cbsdIdRegistry.get(cbsdId);
        if (cbsd != null) {
            cbsdConsumer.accept(cbsd);
        }
    }

    private <T> T fromCbsd(final String cbsdId, final Function<Cbsd, T> cbsdFunction) {
        final Cbsd cbsd = cbsdIdRegistry.get(cbsdId);
        if (cbsd != null) {
            return cbsdFunction.apply(cbsd);
        }
        return null;
    }

    private void withCell(final String cellId, final Consumer<Cell> cellConsumer) {
        final Cell cell = cells.get(cellId);
        if (cell == null) {
            if (untrackedIds.add(cellId)) {
                reporter.reportUntrackedCell(cellId);
            }
        } else {
            cellConsumer.accept(cell);
        }
    }

    private void withFrusForNode(final String nodeName, final Consumer<Fru> fruConsumer) {
        final Set<Fru> frusForNode = Stream.of(frus, irus)
                .flatMap(map -> map.entrySet().stream())
                .filter(fruEntry -> fruEntry.getKey().contains(nodeName))
                .map(Map.Entry::getValue)
                .collect(Collectors.toSet());
        frusForNode.forEach(fruConsumer);
    }

}
