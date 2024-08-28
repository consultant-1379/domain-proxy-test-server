package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import lombok.Value;

@Value
public class CbsdCpiData {
    String eirpCapability;
    String antennaGain;
    boolean indoorDeployment;
    String fccid;
}
