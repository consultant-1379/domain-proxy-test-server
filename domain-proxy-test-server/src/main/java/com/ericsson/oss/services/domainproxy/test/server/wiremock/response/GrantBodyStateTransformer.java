package com.ericsson.oss.services.domainproxy.test.server.wiremock.response;

import static com.ericsson.oss.services.domainproxy.test.server.wiremock.ResponseReader.SKIP_READER_HEADER;
import static com.ericsson.oss.services.domainproxy.test.wiremock.util.JsonUtil.getJsonAsJavaObject;

import java.time.Instant;
import java.util.List;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.FrequencyRangeHz;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.FrequencyRange;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.GrantRequest;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.GrantRequests;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.GrantResponse;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.GrantResponses;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.GrantBodyTransformer;
import com.github.jknack.handlebars.Handlebars;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import lombok.SneakyThrows;

public class GrantBodyStateTransformer  extends GrantBodyTransformer {

    private final Topology topology;

    public GrantBodyStateTransformer(final Topology topology) {
        this.topology = topology;
    }

    public GrantBodyStateTransformer(final Handlebars handlebars,
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

    @SneakyThrows
    @Override
    protected Object buildResponse(final Request request, final GrantResponses grantResponses, final Parameters parameters) {
        if (grantResponses != null && grantResponses.getGrantResponse() != null) {
            final String bodyAsString = request.getBodyAsString();
            final GrantRequests grantRequests = getJsonAsJavaObject(bodyAsString, GrantRequests.class);
            processGrantRequests(grantResponses.getGrantResponse(), grantRequests);
        }

        return grantResponses;
    }

    private void processGrantRequests(final List<GrantResponse> grantResponses, final GrantRequests grantRequests) {
        int offset = 0;
        final List<GrantRequest> requestList = grantRequests.getGrantRequest();
        for (final GrantResponse grantResponse : grantResponses) {
            final int responseCode = grantResponse.getResponse().getResponseCode();
            final String cbsdId = grantResponse.getCbsdId();
            FrequencyRangeHz frequencyRangeHz = null;
            if (offset < requestList.size()) {
                final GrantRequest grantRequest = requestList.get(offset);
                final FrequencyRange operationFrequencyRange = grantRequest.getOperationParam().getOperationFrequencyRange();
                frequencyRangeHz = new FrequencyRangeHz(operationFrequencyRange.getLowFrequency(), operationFrequencyRange.getHighFrequency());
            }

            if (responseCode == 0 && frequencyRangeHz != null) {
                final String grantId = grantResponse.getGrantId();
                final Instant grantExpireTime = getInstant(grantResponse.getGrantExpireTime());
                final long heartbeatInterval = (long) grantResponse.getHeartbeatInterval();

                topology.addGrant(cbsdId, frequencyRangeHz, grantId, grantExpireTime, heartbeatInterval);
            } else {
                topology.addIdleGrant(cbsdId, frequencyRangeHz);
            }
            offset++;
        }
    }

    private Instant getInstant(final String timestamp) {
        if (timestamp != null && !timestamp.trim().isEmpty()) {
            return Instant.parse(timestamp);
        }
        return null;
    }
}
