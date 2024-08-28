package com.ericsson.oss.services.domainproxy.test.server.netconf;

import com.ericsson.oss.mediation.netconf.config.CommandListener;
import com.ericsson.oss.mediation.netconf.config.SecurityDefinition;
import com.ericsson.oss.mediation.netconf.config.TlsConfiguration;
import com.ericsson.oss.mediation.netconf.config.TrustedCertificate;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.server.config.NodeDefinition;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ServerConfiguration extends TlsConfiguration {
    private final String ipAddress;
    @Getter
    private final NodeDefinition node;
    @Getter
    private final Topology topology;
    @Getter
    private final Function<NodeDefinition, String> nodeDataProducer;

    public ServerConfiguration(final String ipAddress, final int port, final int socketTimeout, final long waitForClose,
                               final List<SecurityDefinition> securityDefinitions,
                               final List<TrustedCertificate> trustedCertificates,
                               final NodeDefinition node, final Topology topology, Function<NodeDefinition, String> nodeDataProducer) {
        super(port, socketTimeout, waitForClose, Collections.emptyList(), securityDefinitions, trustedCertificates);
        this.ipAddress = ipAddress;
        this.node = node;
        this.topology = topology;
        this.nodeDataProducer = nodeDataProducer;
    }

    @Override
    public CommandListener getListener(final Object env) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public String getIpAddress() {
        return ipAddress;
    }

    @Override
    public List<String> getSupportedProtocols() {
        return Arrays.asList("TLSv1.2", "TLSv1");
    }

    @Override
    public List<String> getSupportedCiphers() {
        return Arrays.asList("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                             "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                             "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                             "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                             "TLS_RSA_WITH_AES_256_GCM_SHA384",
                             "TLS_RSA_WITH_AES_128_GCM_SHA256",
                             "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
                             "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
                             "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
                             "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
                             "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                             "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
                             "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                             "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                             "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                             "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                             "TLS_RSA_WITH_AES_256_CBC_SHA256",
                             "TLS_RSA_WITH_AES_128_CBC_SHA256",
                             "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
                             "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
                             "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
                             "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
                             "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                             "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
                             "TLS_RSA_WITH_AES_128_CBC_SHA",
                             "TLS_EMPTY_RENEGOTIATION_INFO_SCSV");
    }
}
