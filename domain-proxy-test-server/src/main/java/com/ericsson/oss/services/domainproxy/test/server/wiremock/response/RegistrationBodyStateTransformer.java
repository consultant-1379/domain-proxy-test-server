package com.ericsson.oss.services.domainproxy.test.server.wiremock.response;

import static com.ericsson.oss.services.domainproxy.test.server.wiremock.ResponseReader.SKIP_READER_HEADER;

import java.util.List;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.RegistrationRequest;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.RegistrationResponse;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.RegistrationResponses;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.RegistrationBodyTransformer;
import com.github.jknack.handlebars.Handlebars;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

public class RegistrationBodyStateTransformer extends RegistrationBodyTransformer {
    private final Topology topology;

    public RegistrationBodyStateTransformer(final Topology topology) {
        this.topology = topology;
    }

    public RegistrationBodyStateTransformer(final Handlebars handlebars,
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
    protected Object buildResponse(final Request request, final RegistrationResponses registrationResponses, final Parameters parameters) {
        if (registrationResponses != null && registrationResponses.getRegistrationResponse() != null) {
            processRegistration(registrationResponses.getRegistrationResponse());
        }
        return registrationResponses;
    }

    private void processRegistration(final List<RegistrationResponse> registrationResponses) {
        for (final RegistrationResponse response : registrationResponses) {
            final int responseCode = response.getResponse().getResponseCode();
            if (responseCode == 0) {
                final RegistrationRequest registrationRequest = (RegistrationRequest) response.getRequest();
                final String cbsdSerialNumber = registrationRequest.getCbsdSerialNumber();
                final String cbsdId = response.getCbsdId();
                topology.registerCbsdId(cbsdSerialNumber, cbsdId);
            }
        }
    }
}
