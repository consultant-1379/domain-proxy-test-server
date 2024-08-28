package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

@Data
public class NetconfConfig {
    private String ipRange;
    private String portRange;
    private String ipRangeV6;
    private String portRangeV6;
    private TlsSecurityDefinition tlsSecurityDefinition;
    private TrustedCertificate trustedCertificate;
    private String ipAddrPath;
}
