package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

import java.util.Map;

@Data
public class ReportConfig {
    private String reportClass;
    private Map<String, String> initParameters;
}
