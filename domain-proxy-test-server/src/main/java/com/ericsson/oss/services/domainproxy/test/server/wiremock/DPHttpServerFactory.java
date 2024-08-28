package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.ericsson.oss.services.domainproxy.test.server.config.SasConfig;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty9.JettyHttpServer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.util.ssl.SslContextFactory;

@RequiredArgsConstructor
public class DPHttpServerFactory implements HttpServerFactory {

    @Getter
    private final SasConfig sasConfig;

    @Override
    public HttpServer buildHttpServer(final Options options, final AdminRequestHandler adminRequestHandler,
                                      final StubRequestHandler stubRequestHandler) {
        return new JettyHttpServer(options, adminRequestHandler, stubRequestHandler) {
            @Override
            protected SslContextFactory buildSslContextFactory() {
                return new DPSslContextFactory(getSasConfig().getCipherSuites());
            }
        };
    }
}
