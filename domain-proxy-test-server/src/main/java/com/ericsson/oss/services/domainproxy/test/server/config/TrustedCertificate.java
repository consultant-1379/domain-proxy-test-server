package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

@Data
public class TrustedCertificate {
    private String alias;
    private String certificatePath;
}
