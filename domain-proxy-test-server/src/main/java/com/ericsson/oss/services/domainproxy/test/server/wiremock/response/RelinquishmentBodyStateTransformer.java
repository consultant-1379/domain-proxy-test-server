package com.ericsson.oss.services.domainproxy.test.server.wiremock.response;

import static com.ericsson.oss.services.domainproxy.test.server.wiremock.ResponseReader.SKIP_READER_HEADER;

import java.util.List;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.RelinquishmentResponse;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.RelinquishmentResponses;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.RelinquishmentBodyTransformer;
import com.github.jknack.handlebars.Handlebars;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

public class RelinquishmentBodyStateTransformer extends RelinquishmentBodyTransformer {
    private final Topology topology;

    public RelinquishmentBodyStateTransformer(final Topology topology) {
        this.topology = topology;
    }

    public RelinquishmentBodyStateTransformer(final Handlebars handlebars,
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
    protected Object buildResponse(final Request request, final RelinquishmentResponses relinquishmentResponses, final Parameters parameters) {
        if (relinquishmentResponses != null && relinquishmentResponses.getRelinquishmentResponse() != null) {
            processRelinquishment(relinquishmentResponses.getRelinquishmentResponse());
        }
        return relinquishmentResponses;
    }

    private void processRelinquishment(final List<RelinquishmentResponse> relinquishmentResponses) {
        for (final RelinquishmentResponse response : relinquishmentResponses) {
            final String cbsdId = response.getCbsdId();
            final String grantId = response.getGrantId();
            topology.relinquishGrant(cbsdId, grantId);
        }
    }
}
