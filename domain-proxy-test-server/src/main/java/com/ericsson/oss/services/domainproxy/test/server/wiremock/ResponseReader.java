package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.FrequencyRangeHz;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.PostServeAction;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

@RequiredArgsConstructor
public class ResponseReader extends PostServeAction {
    private static Logger logger = LoggerFactory.getLogger(ResponseReader.class);

    public static HttpHeader SKIP_READER_HEADER = HttpHeader.httpHeader("X-cbrs-skip-reader", "yes");

    private ObjectMapper objectMapper = new ObjectMapper();
    private final Topology topology;
    private final Reporter reporter;

    @Override
    public void doGlobalAction(final ServeEvent serveEvent, final Admin admin) {
        try {
            if (serveEvent.getWasMatched()) {
                final String requestUrl = serveEvent.getRequest().getAbsoluteUrl();
                if (requestUrl.contains("/heartbeat")) {
                    reporter.reportHeartbeatRequestReceived();
                    if (serveEvent.getResponseDefinition().getHeaders().getHeader(SKIP_READER_HEADER.key()).isPresent()) {
                        logger.trace("Skipping ResponseReader since skip header is present.");
                    } else {
                        final JsonNode rootNode = objectMapper.readTree(serveEvent.getResponse().getBodyAsString());
                        rootNode.get("heartbeatResponse").elements().forEachRemaining(this::processHeartbeat);
                    }
                } else if (requestUrl.contains("/grant")) {
                    reporter.reportGrantRequestReceived();
                    if (serveEvent.getResponseDefinition().getHeaders().getHeader(SKIP_READER_HEADER.key()).isPresent()) {
                        logger.trace("Skipping ResponseReader since skip header is present.");
                    } else {
                        final JsonNode requestNode = objectMapper.readTree(serveEvent.getRequest().getBodyAsString());
                        final JsonNode responseNode = objectMapper.readTree(serveEvent.getResponse().getBodyAsString());
                        processGrantRequest(requestNode.get("grantRequest"), responseNode.get("grantResponse"));
                    }
                } else if (requestUrl.contains("/registration")) {
                    reporter.reportRegistrationRequestReceived();
                    if (serveEvent.getResponseDefinition().getHeaders().getHeader(SKIP_READER_HEADER.key()).isPresent()) {
                        logger.trace("Skipping ResponseReader since skip header is present.");
                    } else {
                        final JsonNode requestNode = objectMapper.readTree(serveEvent.getRequest().getBodyAsString());
                        final JsonNode responseNode = objectMapper.readTree(serveEvent.getResponse().getBodyAsString());
                        processRegistration(requestNode.get("registrationRequest"), responseNode.get("registrationResponse"));
                    }
                } else if (requestUrl.contains("/relinquishment")) {
                    reporter.reportRelinquishmentRequestReceived();
                    if (serveEvent.getResponseDefinition().getHeaders().getHeader(SKIP_READER_HEADER.key()).isPresent()) {
                        logger.trace("Skipping ResponseReader since skip header is present.");
                    } else {
                        final JsonNode rootNode = objectMapper.readTree(serveEvent.getResponse().getBodyAsString());
                        rootNode.get("relinquishmentResponse").elements().forEachRemaining(this::processRelinquishment);
                    }
                } else if (requestUrl.contains("/deregistration")) {
                    reporter.reportDeregistrationRequestReceived();
                    if (serveEvent.getResponseDefinition().getHeaders().getHeader(SKIP_READER_HEADER.key()).isPresent()) {
                        logger.trace("Skipping ResponseReader since skip header is present.");
                    } else {
                        final JsonNode rootNode = objectMapper.readTree(serveEvent.getResponse().getBodyAsString());
                        rootNode.get("deregistrationResponse").elements().forEachRemaining(this::processDeregistration);
                    }
                } else if (requestUrl.contains("/spectrumInquiry")) {
                    reporter.reportSpectrumInquiryRequestReceived();
                }
            }
        } catch (IOException e) {
            System.out.println("Error during response interception.");
            e.printStackTrace();
        }
    }

    private void processDeregistration(final JsonNode response) {
        final String cbsdId = response.get("cbsdId").asText();
        topology.unregisterCbsdId(cbsdId);
    }

    private void processRelinquishment(final JsonNode response) {
        final String cbsdId = response.get("cbsdId").asText();
        final String grantId = response.get("grantId").asText();
        topology.relinquishGrant(cbsdId, grantId);
    }

    private void processGrantRequest(final JsonNode requests, final JsonNode responses) {
        for (int i = 0; i < requests.size(); i++) {
            final JsonNode response = responses.get(i);
            final int responseCode = response.at("/response/responseCode").asInt();
            final JsonNode request = requests.get(i);
            final JsonNode frequencyRange = request.at("/operationParam/operationFrequencyRange");
            final long lowFrequency = frequencyRange.get("lowFrequency").asLong();
            final long highFrequency = frequencyRange.get("highFrequency").asLong();
            final FrequencyRangeHz frequencyRangeHz = new FrequencyRangeHz(lowFrequency, highFrequency);

            final String cbsdId = response.get("cbsdId").asText();
            if (responseCode == 0) {
                final String grantId = response.get("grantId").asText();
                final Instant grantExpireTime = Instant.parse(response.get("grantExpireTime").asText());
                final long heartbeatInterval = response.get("heartbeatInterval").asLong();

                topology.addGrant(cbsdId, frequencyRangeHz, grantId, grantExpireTime, heartbeatInterval);
            } else {
                topology.addIdleGrant(cbsdId, frequencyRangeHz);
            }
        }
    }

    private void processRegistration(final JsonNode requests, final JsonNode responses) {
        for (int i = 0; i < requests.size(); i++) {
            final JsonNode response = responses.get(i);
            final int responseCode = response.at("/response/responseCode").asInt();
            if (responseCode == 0) {
                final JsonNode request = requests.get(i);
                final String cbsdSerialNumber = request.get("cbsdSerialNumber").asText();
                final String cbsdId = response.get("cbsdId").asText();
                topology.registerCbsdId(cbsdSerialNumber, cbsdId);
            }
        }
    }

    private void processHeartbeat(final JsonNode response) {
        final String cbsdId = response.get("cbsdId").asText();
        final String grantId = response.get("grantId").asText();
        final Instant transmitExpireTime = getInstantValue(response, "transmitExpireTime");
        final Instant grantExpireTime = getInstantValue(response, "grantExpireTime");
        final Long heartbeatInterval = response.has("heartbeatInterval") ? response.get("heartbeatInterval").asLong() : null;

        final int responseCode = response.at("/response/responseCode").asInt();
        switch (responseCode) {
            case 0: // SUCCESS
                topology.grantHeartbeatSucceed(cbsdId, grantId, transmitExpireTime, grantExpireTime, heartbeatInterval, null);
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
    }

    private Instant getInstantValue(final JsonNode response, final String fieldName) {
        final JsonNode node = response.get(fieldName);
        if (node != null && !node.isNull() && !node.asText().trim().isEmpty()) {
            return Instant.parse(node.asText());
        }
        return null;
    }

    @Override
    public String getName() {
        return "dp-response-reader";
    }
}
