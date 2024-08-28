package com.ericsson.oss.services.domainproxy.test.server.wiremock.response;

import static com.ericsson.oss.services.domainproxy.test.server.wiremock.ResponseReader.SKIP_READER_HEADER;

import java.util.List;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.DeregistrationResponse;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.DeregistrationResponses;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.DeregistrationBodyTransformer;
import com.github.jknack.handlebars.Handlebars;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

public class DeregistrationBodyStateTransformer extends DeregistrationBodyTransformer {

    private final Topology topology;

    public DeregistrationBodyStateTransformer(final Topology topology) {
        this.topology = topology;
    }

    public DeregistrationBodyStateTransformer(final Handlebars handlebars,
                                              final Topology topology) {
        super(handlebars);
        this.topology = topology;
    }

    @Override
    public ResponseDefinition transform(final Request request, final ResponseDefinition responseDefinition, final FileSource files,
                                        final Parameters parameters) {
        final ResponseDefinition response = super.transform(request, responseDefinition, files, parameters);
        return ResponseDefinitionBuilder.like(response).but().withHeaders(response.getHeaders().plus(SKIP_READER_HEADER)).build();
    }

    @Override
    protected Object buildResponse(final Request request, final DeregistrationResponses deregistrationResponses, final Parameters parameters) {
        if (deregistrationResponses != null && deregistrationResponses.getDeregistrationResponse() != null) {
            processDeregistration(deregistrationResponses.getDeregistrationResponse());
        }
        return deregistrationResponses;

    }
    private void processDeregistration(final List<DeregistrationResponse> deregistrationResponse) {
        for (final DeregistrationResponse response : deregistrationResponse) {
            final String cbsdId = response.getCbsdId();
            topology.unregisterCbsdId(cbsdId);
        }
    }
}
