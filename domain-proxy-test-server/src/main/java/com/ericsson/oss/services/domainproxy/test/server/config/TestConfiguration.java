package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

import java.util.List;

@Data
public class TestConfiguration {
    private NetconfConfig netconf;
    private List<ProductToCpiDefinition> productCpi;
    private List<NodeDefinition> nodes;
    private SasConfig sasConfig;
    private List<ReportConfig> reports;
}
