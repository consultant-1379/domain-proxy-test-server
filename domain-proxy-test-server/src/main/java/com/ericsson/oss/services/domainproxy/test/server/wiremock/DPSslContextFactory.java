package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.SSLEngine;
import java.util.List;

public class DPSslContextFactory extends SslContextFactory {

    private final List<String> cipherSuites;

    public DPSslContextFactory(final List<String> cipherSuites) {

        this.cipherSuites = cipherSuites;
    }

    @Override
    public void customize(final SSLEngine sslEngine) {
        super.customize(sslEngine);
        if (cipherSuites != null && !cipherSuites.isEmpty()) {
            sslEngine.setEnabledCipherSuites(cipherSuites.toArray(new String[]{}));
        }
    }
}
