package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.Data;

import java.util.List;

@Data
public class SasConfig {
    private Integer httpPort;
    private Integer httpsPort;
    private String bindAddress;
    private String keystorePath;
    private String keystorePassword;
    private String keystoreType;
    private Boolean needClientAuth;
    private String trustStorePath;
    private String trustStorePassword;
    private String usingFilesUnderDirectory;
    private String usingFilesUnderClasspath;
    private Boolean disableRequestJournal;
    private Boolean asynchronousResponseEnabled;
    private Integer asynchronousResponseThreads;
    private Integer jettyAcceptorsThreads;
    private Integer jettyContainerThreads;
    private List<String> cipherSuites;
}
