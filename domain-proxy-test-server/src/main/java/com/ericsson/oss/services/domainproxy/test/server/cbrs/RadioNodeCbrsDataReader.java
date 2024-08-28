package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import com.ericsson.oss.services.domainproxy.test.server.xml.datareader.XmlDataReader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;

import static com.ericsson.oss.services.domainproxy.test.server.xml.datareader.AssociationMatcher.SAME_PARENT;
import static com.ericsson.oss.services.domainproxy.test.server.xml.datareader.AssociationMatcher.SRC_VALUE_CONTAINS_TRG_PARENT;
import static com.ericsson.oss.services.domainproxy.test.server.xml.datareader.AssociationMatcher.SRC_VALUE_IS_TRG_PARENT;
import static com.ericsson.oss.services.domainproxy.test.server.xml.datareader.AssociationMatcher.TRG_PARENT_CONTAINS_SRC_PARENT;

@RequiredArgsConstructor
public class RadioNodeCbrsDataReader {
    private static final Logger logger = LoggerFactory.getLogger(RadioNodeCbrsDataReader.class);

    private static final String NR_SECTORCARRIER_TAG = "<NRSectorCarrier>";
    private static final String EUTRAN_CELL_TDD_TAG = "<EUtranCellTDD>";

    public static void extractCbrsTopologyFromRadioNode(final String nodeName, final String nodeData, final Topology topology) {
        try {
            XmlDataReader reader = null;
            XmlDataReader.ReaderBuilder builder = null;
            if(nodeData.contains(EUTRAN_CELL_TDD_TAG)) {
                builder = XmlDataReader.builder();
                buildCellToFruSerialMappingforLTE(builder);
                buildFruToRdiPortMapping(builder);
                reader = builder.readElement("EUtranCellTDD", "channelBandwidth")
                        .readElement("EUtranCellTDD", "earfcn")
                        .readElement("EUtranCellTDD", "cbrsCell")
                        .readElement("FieldReplaceableUnit", "serialNumber")
                        .linkingToNewElement("FieldReplaceableUnit", "productNumber", SAME_PARENT)
                        .build();
                processXmlData(reader, nodeName, nodeData, topology);
            }
            if(nodeData.contains(NR_SECTORCARRIER_TAG)) {
                //Read NRSectorCarrier elements
                builder = XmlDataReader.builder();
                buildCellToFruSerialMappingForNR(builder, true);
                buildFruToRdiPortMapping(builder);
                reader = builder.readElement("NRSectorCarrier", "bSChannelBwDL")
                        .readElement("NRSectorCarrier", "bSChannelBwUL")
                        .readElement("NRSectorCarrier", "arfcnDL")
                        .readElement("NRSectorCarrier", "arfcnUL")
                        .readElement("NRSectorCarrier", "configuredMaxTxPower")
                        .readElement("NRSectorCarrier", "cbrsEnabled")
                        .readElement("FieldReplaceableUnit", "serialNumber")
                        .linkingToNewElement("FieldReplaceableUnit", "productNumber", SAME_PARENT)
                        .build();
                processXmlData(reader, nodeName, nodeData, topology);

                //Read NRCellDU elements
              /*  builder = XmlDataReader.builder();
                buildCellToFruSerialMappingForNR(builder, false);
                buildFruToRdiPortMapping(builder);
                reader = builder.readElement("NRCellDU", "ssbFrequency")
                        .readElement("NRCellDU", "ssbFrequencyAutoSelected")
                        .build();
                processXmlData(reader, nodeName, nodeData, topology);*/
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException("extractCbrsTopologyFromRadioNode: Failed to read node data.", e);
        }
    }

    private static void processXmlData (final XmlDataReader reader, final String nodeName, final String nodeData, final Topology topology ) throws XMLStreamException {
        reader.processXmlData(nodeData);
        reader.mapHeadToLeafValue((cellOrProductName, leafValue) -> {
            if (cellOrProductName.getElementName().equals("productName")) {
                if (cellOrProductName.getValue().contains("IRU")) {
                    if (leafValue.getElementName().equals("remoteRdiPortRef")) {
                        topology.addIruAndPort(cellOrProductName.getParentFdn(), leafValue.getParentFdn(), leafValue.getValue());
                    } else if (leafValue.getElementName().equals("serialNumber")) {
                        topology.addIruWithSerialNumber(cellOrProductName.getParentFdn(), leafValue.getValue());
                    } else {
                        topology.addIru(cellOrProductName.getParentFdn());
                    }
                } else {
                    if (leafValue.getElementName().equals("serialNumber")) {
                        topology.addFruWithSerialNumber(cellOrProductName.getParentFdn(), leafValue.getValue());
                    } else {
                        topology.addFru(cellOrProductName.getParentFdn());
                    }
                }
            } else {
                switch (leafValue.getElementName()) {
                    case "serialNumber":
                        topology.addCellAndCbsd(nodeName, cellOrProductName.getParentFdn(), leafValue.getValue());
                        break;
                    case "channelBandwidth":
                        final long bwKhz = Long.parseLong(leafValue.getValue());
                        topology.trySetCellBandwidthHz(leafValue.getParentFdn(), bwKhz * 1000);
                        break;
                    case "bSChannelBwDL":
                        final long bwDLKhz = Long.parseLong(leafValue.getValue());
                        topology.trySetCellBSChannelBwDLHz(leafValue.getParentFdn(), bwDLKhz * 1000);
                        break;
                    case "bSChannelBwUL":
                        final long bwULKhz = Long.parseLong(leafValue.getValue());
                        topology.trySetCellBSChannelBwULHz(leafValue.getParentFdn(), bwULKhz * 1000);
                        break;
                    case "earfcn":
                        final long earfcn = Long.parseLong(leafValue.getValue());
                        topology.trySetCellEarfcn(leafValue.getParentFdn(), earfcn);
                        break;
                    case "arfcnDL":
                        final long arfcnDL = Long.parseLong(leafValue.getValue());
                        topology.trySetCellArfcnDL(leafValue.getParentFdn(), arfcnDL);
                        break;
                    case "arfcnUL":
                        final long arfcnUL = Long.parseLong(leafValue.getValue());
                        topology.trySetCellArfcnUL(leafValue.getParentFdn(), arfcnUL);
                        break;
                    case "configuredMaxTxPower":
                        final long configuredMaxTxPower = Long.parseLong(leafValue.getValue());
                        topology.trySetCellConfiguredMaxTxPower(leafValue.getParentFdn(), configuredMaxTxPower);
                        break;
                    case "ssbFrequency":
                        final long ssbFrequency = Long.parseLong(leafValue.getValue());
                        topology.trySetCellSsbFrequency(leafValue.getParentFdn(), ssbFrequency);
                        break;
                    case "ssbFrequencyAutoSelected":
                        final long ssbFrequencyAutoSelected = Long.parseLong(leafValue.getValue());
                        topology.trySetCellSsbFrequencyAutoSelected(leafValue.getParentFdn(), ssbFrequencyAutoSelected);
                        break;
                    case "cbrsEnabled":
                    case "cbrsCell":
                        final boolean enabled = leafValue.getValue().toLowerCase().trim().equals("true");
                        topology.trySetCellCbrsEnabled(leafValue.getParentFdn(), enabled);
                        break;
                    case "productNumber":
                        final String serialNumber = cellOrProductName.getValue();
                        topology.trySetCbsdProductNumber(serialNumber, leafValue.getValue());
                        break;
                }
            }
        });
    }

    public static void updateBandwidth(final String nodeData, final Topology topology) {
        try {
            final XmlDataReader reader = XmlDataReader.builder()
                    .readElement("EUtranCellTDD", "channelBandwidth")
                    .build();
            reader.processXmlData(nodeData);
            reader.walkValues((isLeaf, valueHolder) -> {
                if (valueHolder.getElementName().equals("channelBandwidth")) {
                    final long bwKhz = Long.parseLong(valueHolder.getValue());
                    topology.setCellBandwidthHz(valueHolder.getParentFdn(), bwKhz * 1000);
                }
            });
        } catch (XMLStreamException e) {
            throw new RuntimeException("updateCellEarfcn: Failed to read node data.", e);
        }
    }

    public static void updateBandwidthDownlink(final String nodeData, final Topology topology) {
        try {
            final XmlDataReader reader = XmlDataReader.builder()
                    .readElement("NRSectorCarrier", "bSChannelBwDL")
                    .build();
            reader.processXmlData(nodeData);
            reader.walkValues((isLeaf, valueHolder) -> {
                if (valueHolder.getElementName().equals("bSChannelBwDL")) {
                    final long bwKhz = Long.parseLong(valueHolder.getValue());
                    logger.info("Update cell BSChannelBwDLHz. cell={}, BSChannelBwDLHz={}", valueHolder.getParentFdn(), bwKhz);
                    topology.trySetCellBSChannelBwDLHz(valueHolder.getParentFdn(), bwKhz);
                }
            });
        } catch (XMLStreamException e) {
            throw new RuntimeException("updateBandwidthDownlink: Failed to read node data.", e);
        }
    }

    public static void updateBandwidthUplink(final String nodeData, final Topology topology) {
        try {
            final XmlDataReader reader = XmlDataReader.builder()
                    .readElement("NRSectorCarrier", "bSChannelBwUL")
                    .build();
            reader.processXmlData(nodeData);
            reader.walkValues((isLeaf, valueHolder) -> {
                if (valueHolder.getElementName().equals("bSChannelBwUL")) {
                    final long bwKhz = Long.parseLong(valueHolder.getValue());
                    logger.info("Update cell BSChannelBwUL. cell={}, BSChannelBwULHz={}", valueHolder.getParentFdn(), bwKhz);
                    topology.trySetCellBSChannelBwULHz(valueHolder.getParentFdn(), bwKhz);
                }
            });
        } catch (XMLStreamException e) {
            throw new RuntimeException("updateBandwidthUplink: Failed to read node data.", e);
        }
    }

    public static void updateArfcnDownlink(final String nodeData, final Topology topology) {
        try {
            final XmlDataReader reader = XmlDataReader.builder()
                    .readElement("NRSectorCarrier", "arfcnDL")
                    .build();
            reader.processXmlData(nodeData);
            reader.walkValues((isLeaf, valueHolder) -> {
                if (valueHolder.getElementName().equals("arfcnDL")) {
                    final long arfcnDL = Long.parseLong(valueHolder.getValue());
                    logger.info("Update cell arfcnDL. cell={}, arfcnDL={}", valueHolder.getParentFdn(), arfcnDL);
                    topology.trySetCellArfcnDL(valueHolder.getParentFdn(), arfcnDL);
                }
            });
        } catch (XMLStreamException e) {
            throw new RuntimeException("updateArfcnDownlink: Failed to read node data.", e);
        }
    }

    public static void updateArfcnUplink(final String nodeData, final Topology topology) {
        try {
            final XmlDataReader reader = XmlDataReader.builder()
                    .readElement("NRSectorCarrier", "arfcnUL")
                    .build();
            reader.processXmlData(nodeData);
            reader.walkValues((isLeaf, valueHolder) -> {
                if (valueHolder.getElementName().equals("arfcnUL")) {
                    final long arfcnUL = Long.parseLong(valueHolder.getValue());
                    logger.info("Update cell arfcnUL. cell={}, arfcnUL={}", valueHolder.getParentFdn(), arfcnUL);
                    topology.trySetCellArfcnUL(valueHolder.getParentFdn(), arfcnUL);
                }
            });
        } catch (XMLStreamException e) {
            throw new RuntimeException("updateArfcnUplink: Failed to read node data.", e);
        }
    }

    public static void updateConfiguredMaxTxPower(final String nodeData, final Topology topology) {
        try {
            XmlDataReader reader = null;
            reader = XmlDataReader.builder()
                    .readElement("NRSectorCarrier", "configuredMaxTxPower")
                    .build();
            reader = XmlDataReader.builder()
                    .readElement("EUtranCellTDD", "configuredMaxTxPower")
                    .build();
            reader.processXmlData(nodeData);
            reader.walkValues((isLeaf, valueHolder) -> {
                if (valueHolder.getElementName().equals("configuredMaxTxPower")) {
                    final long configuredMaxTxPower = Long.parseLong(valueHolder.getValue());
                    topology.trySetCellConfiguredMaxTxPower(valueHolder.getParentFdn(), configuredMaxTxPower);
                }
            });
        } catch (XMLStreamException e) {
            throw new RuntimeException("configuredMaxTxPower: Failed to read node data.", e);
        }
    }

    private static void buildCellToFruSerialMappingforLTE(final XmlDataReader.ReaderBuilder builder) {
        final XmlDataReader.ElementReaderBuilder rfBranchRef = builder.readElement("EUtranCellTDD", "sectorCarrierRef")
                .linkingToNewElement("SectorCarrier", "sectorFunctionRef", SRC_VALUE_IS_TRG_PARENT)
                .linkingToNewElement("SectorEquipmentFunction", "rfBranchRef", SRC_VALUE_IS_TRG_PARENT);

        final XmlDataReader.ElementReaderBuilder fieldReplaceableUnit =
                rfBranchRef.linkingToNewElement("FieldReplaceableUnit", "serialNumber", SRC_VALUE_CONTAINS_TRG_PARENT);

        rfBranchRef.linkingToNewElement("MulticastAntennaBranch", "transceiverRef", SRC_VALUE_IS_TRG_PARENT)
                .linkingToElement(fieldReplaceableUnit, SRC_VALUE_CONTAINS_TRG_PARENT);

        rfBranchRef.linkingToNewElement("RfBranch", "rfPortRef", SRC_VALUE_IS_TRG_PARENT)
                .linkingToElement(fieldReplaceableUnit, SRC_VALUE_CONTAINS_TRG_PARENT);
    }

    private static void buildCellToFruSerialMappingForNR(final XmlDataReader.ReaderBuilder builder, final boolean isSectorCarrierFdn) {
        XmlDataReader.ElementReaderBuilder rfBranchRef = null;
        if(!isSectorCarrierFdn) {
            rfBranchRef = builder.readElement("NRCellDU", "nRSectorCarrierRef")
                    .linkingToNewElement("NRSectorCarrier", "sectorEquipmentFunctionRef", SRC_VALUE_IS_TRG_PARENT)
                    .linkingToNewElement("SectorEquipmentFunction", "rfBranchRef", SRC_VALUE_IS_TRG_PARENT);
        } else {
            rfBranchRef = builder.readElement("NRSectorCarrier", "sectorEquipmentFunctionRef")
                    .linkingToNewElement("SectorEquipmentFunction", "rfBranchRef", SRC_VALUE_IS_TRG_PARENT);
        }

        final XmlDataReader.ElementReaderBuilder fieldReplaceableUnit =
                rfBranchRef.linkingToNewElement("FieldReplaceableUnit", "serialNumber", SRC_VALUE_CONTAINS_TRG_PARENT);

        rfBranchRef.linkingToNewElement("MulticastAntennaBranch", "transceiverRef", SRC_VALUE_IS_TRG_PARENT)
                .linkingToElement(fieldReplaceableUnit, SRC_VALUE_CONTAINS_TRG_PARENT);

        rfBranchRef.linkingToNewElement("RfBranch", "rfPortRef", SRC_VALUE_IS_TRG_PARENT)
                .linkingToElement(fieldReplaceableUnit, SRC_VALUE_CONTAINS_TRG_PARENT);
    }

    private static void buildFruToRdiPortMapping(final XmlDataReader.ReaderBuilder builder) {
        final XmlDataReader.ElementReaderBuilder fruProductName = builder.readElement("FieldReplaceableUnit", "productName");
        fruProductName.linkingToNewElement("FieldReplaceableUnit", "serialNumber", SAME_PARENT);
        fruProductName.linkingToNewElement("RdiPort", "remoteRdiPortRef", TRG_PARENT_CONTAINS_SRC_PARENT);
    }
}
