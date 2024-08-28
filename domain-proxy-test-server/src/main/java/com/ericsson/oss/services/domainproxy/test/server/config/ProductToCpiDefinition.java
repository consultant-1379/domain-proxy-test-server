package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

@Data
public class ProductToCpiDefinition {
    private String productNumber;
    private String eirpCapability;
    private String antennaGain;
    private boolean indoorDeployment;
    private String fccid;
}
