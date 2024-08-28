package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

@Data
public class TlsSecurityDefinition {
    private String alias;
    private String keyPath;
    private String certificatePath;
}
