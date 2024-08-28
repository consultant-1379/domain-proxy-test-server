package com.ericsson.oss.services.domainproxy.test.server.wiremock.response;

import static com.ericsson.oss.services.domainproxy.test.server.wiremock.ResponseReader.SKIP_READER_HEADER;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.GrantState;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.HeartbeatRequest;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.HeartbeatRequests;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.HeartbeatResponse;
import com.ericsson.oss.services.domainproxy.test.wiremock.protocol.HeartbeatResponses;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.HeartbeatBodyTransformer;
import com.ericsson.oss.services.domainproxy.test.wiremock.util.JsonUtil;
import com.github.jknack.handlebars.Handlebars;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import lombok.SneakyThrows;

public class HeartbeatBodyStateTransformer extends HeartbeatBodyTransformer {

    private final Topology topology;

    public HeartbeatBodyStateTransformer(final Topology topology) {
        this.topology = topology;
    }

    public HeartbeatBodyStateTransformer(final Handlebars handlebars,
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
    protected Object buildResponse(final Request request, final HeartbeatResponses heartbeatResponses, final Parameters parameters) {
        if (heartbeatResponses != null && heartbeatResponses.getHeartbeatResponse() != null) {
            processHeartbeats(heartbeatResponses.getHeartbeatResponse(), request, parameters);
        }
        return heartbeatResponses;
    }

    private void processHeartbeats(final List<HeartbeatResponse> heartbeatResponse, final Request request, final Parameters parameters) throws IOException {
        final String bodyAsString = request.getBodyAsString();
        final HeartbeatRequests heartbeatRequests = JsonUtil.getJsonAsJavaObject(bodyAsString, HeartbeatRequests.class);
        final BiFunction<HeartbeatResponse, Integer, HeartbeatRequest> findRequest = (response, idx) -> {
            HeartbeatRequest heartbeatRequest = heartbeatRequests.getHeartbeatRequest().get(idx);
            if (Objects.equals(response.getGrantId(), heartbeatRequest.getGrantId())) {
                return heartbeatRequest;
            }
            for (final HeartbeatRequest candidate : heartbeatRequests.getHeartbeatRequest()) {
                if (Objects.equals(response.getGrantId(), candidate.getGrantId())) {
                    return candidate;
                }
            }
            return null;
        };

        int responseIndex = 0;
        for (final HeartbeatResponse response : heartbeatResponse) {
            final String cbsdId = response.getCbsdId();
            final String grantId = response.getGrantId();
            final Instant transmitExpireTime = getInstant(response.getTransmitExpireTime());
            final Instant grantExpireTime = getInstant(response.getGrantExpireTime());
            final Long heartbeatInterval = response.isHeartbeatIntervalSet() ? (long) response.getHeartbeatInterval() : null;
            final int responseCode = response.getResponse().getResponseCode();

            switch (responseCode) {
                case 0: // SUCCESS
                    final HeartbeatRequest matchingRequest = findRequest.apply(response, responseIndex);
                    if (matchingRequest != null && shouldCheckHeartbeatState(parameters)) {
                        final String operationState = matchingRequest.getOperationState();
                        final GrantState grantState = GrantState.valueOf(operationState);
                        final Integer newResponseCode =
                                topology.grantHeartbeatSucceed(cbsdId, grantId, transmitExpireTime, grantExpireTime, heartbeatInterval, grantState);
                        if (newResponseCode != null) {
                            response.getResponse().setResponseCode(newResponseCode);
                        }
                    } else {
                        topology.grantHeartbeatSucceed(cbsdId, grantId, transmitExpireTime, grantExpireTime, heartbeatInterval, null);
                    }
                    break;
                case 105: //DEREGISTER
                    topology.unregisterCbsdId(cbsdId);
                    break;
                case 501: //SUSPENDED_GRANT
                    topology.grantHeartbeatSuspended(cbsdId, grantId, transmitExpireTime, grantExpireTime, heartbeatInterval);
                    break;
                default:
                    topology.grantHeartbeatFailed(cbsdId, grantId, transmitExpireTime);
            }
            responseIndex++;
        }
    }

    private boolean shouldCheckHeartbeatState(final Parameters parameters) {
        final Map<String, Object> bodyTransformer = (Map<String, Object>) parameters.get("BodyTransformer");
        Object checkOption = null;
        if (bodyTransformer != null) {
            checkOption = bodyTransformer.get("enableHeartbeatStateCheck");
        }
        if (checkOption == null) {
            checkOption = System.getProperty("com.ericsson.oss.services.domainproxy.test.wiremock.enableHeartbeatStateCheck");
        }
        return checkOption != null && checkOption.toString().equalsIgnoreCase("true");
    }

    private Instant getInstant(final String timestamp) {
        if (timestamp != null && !timestamp.trim().isEmpty()) {
            return Instant.parse(timestamp);
        }
        return null;
    }
}
