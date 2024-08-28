package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

@Data
public class CbsdSerialToCpiDefinition {
    private String serialNumber;
    private String eirpCapability;
    private String antennaGain;
    private boolean indoorDeployment;
}
