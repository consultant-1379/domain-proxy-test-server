package com.ericsson.oss.services.domainproxy.test.server.config;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class NodeDefinition {
    private String name;
    private int repeat;
    private String dataTemplate;
    private List<String> cbsdSerials;
    private Map<String, Object> attributes;
    private long latencyMillis;
    private MeasurementsDefinition measurements;
    private boolean useIp6;
    private List<CbsdSerialToCpiDefinition> cbsdSerialToCpi;
}
