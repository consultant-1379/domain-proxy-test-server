package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.tomakehurst.wiremock.common.Json;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(of = "cbsdSeria")
public class Cbsd {
    private static final Logger logger = LoggerFactory.getLogger(Cbsd.class);
    public static final int INVALID_VALUE = 103;
    public static final int UNSYNC_OP_PARAM = 502;
    public static final int TERMINATED_GRANT = 500;
    public static final int OK = 0;
    @JsonView(Json.PublicView.class)
    private final String cbsdSeria;
    @JsonView(Json.PublicView.class)
    private String productNumber;
    @JsonIgnore
    private final Reporter reporter;
    @JsonView(Json.PublicView.class)
    private String cbsdId;
    @JsonView(Json.PublicView.class)
    private CbsdState state = CbsdState.UNREGISTERED;
    @JsonView(Json.PrivateView.class)
    private Map<Long, Grant> freqStartToGrant = new ConcurrentHashMap<>();
    @JsonView(Json.PrivateView.class)
    private Map<String, Grant> grantIdToGrant = new ConcurrentHashMap<>();
    @JsonView(Json.PrivateView.class)
    private CbsdCpiData cpiData;

    public void setAsRegistered(final String cbsdId) {
        if (!CbsdState.REGISTERED.equals(this.state)) {
            reporter.reportCbsdRegistered(cbsdSeria, cbsdId);
        }
        this.state = CbsdState.REGISTERED;
        this.setCbsdId(cbsdId);
    }

    public void setAsUnregistered() {
        if (!CbsdState.UNREGISTERED.equals(this.state)) {
            reporter.reportCbsdUnregistered(cbsdSeria);
        }
        reset();
    }

    public void addGrant(final FrequencyRangeHz frequencyRange, final String grantId, final GrantState grantState, final Instant grantExpireTime,
                         final long heartbeatInterval) {
        final Grant grant = new Grant(grantId, reporter);
        grant.setFrequencyRangeHz(frequencyRange);
        grant.setGrantExpireTime(grantExpireTime);
        grant.setHeartbeatInterval(heartbeatInterval);
        grant.setState(grantState);
        if (grantId != null) {
            grantIdToGrant.put(grantId, grant);
        }
        if (frequencyRange != null) {
            freqStartToGrant.put(frequencyRange.getFrequencyStart(), grant);
        }
    }

    public int grantHeartbeatSucceed(final String grantId, final Instant transmitExpireTime, final Instant grantExpireTime,
                                      final Long heartbeatInterval, final GrantState requestGrantState) {
        if (reportUnregisteredHeartbeat(grantId)) {
            return INVALID_VALUE;
        }

        final Grant grant = grantIdToGrant.get(grantId);
        if (grant == null) {
            reporter.reportGrantUnknownHeartbeat(grantId);
            return INVALID_VALUE;
        } else {
            if (GrantState.AUTHORIZED.equals(requestGrantState) && !grant.isTransmitting()) {
                // if hb request is Authorized but grant is not transmitting
                if (GrantState.AUTHORIZED.equals(grant.getState())) {
                    grant.heartbeat(null, grantExpireTime, heartbeatInterval);
                    grant.setState(GrantState.GRANTED);
                    logger.debug("Authorized HB of Granted grant due to past TX, returning UNSYNC_OP_PARAM. cbsd-id={}, grant-freq={}, range={}", cbsdId, grant.getFrequencyRangeHz().getFrequencyStart(), grant.getFrequencyRangeHz());
                    return UNSYNC_OP_PARAM;
                } else {
                    grant.setState(GrantState.IDLE);
                    logger.debug("Second Authorized HB of Granted grant due to past TX, returning TERMINATED_GRANT. cbsd-id={}, grant-freq={}, range={}", cbsdId, grant.getFrequencyRangeHz().getFrequencyStart(), grant.getFrequencyRangeHz());
                    return TERMINATED_GRANT;
                }
            }
            grant.heartbeat(transmitExpireTime, grantExpireTime, heartbeatInterval);
            grant.setState(GrantState.AUTHORIZED);
            return OK;
        }
    }

    public void grantHeartbeatSuspended(final String grantId, final Instant transmitExpireTime, final Instant grantExpireTime,
                                        final Long heartbeatInterval) {
        if (reportUnregisteredHeartbeat(grantId)) {
            return;
        }

        final Grant grant = grantIdToGrant.get(grantId);
        if (grant == null) {
            reporter.reportGrantUnknownHeartbeat(grantId);
        } else {
            grant.heartbeat(transmitExpireTime, grantExpireTime, heartbeatInterval);
            grant.setState(GrantState.GRANTED);
        }
    }

    public void grantHeartbeatFailed(final String grantId, final Instant transmitExpireTime) {
        if (reportUnregisteredHeartbeat(grantId)) {
            return;
        }

        final Grant grant = grantIdToGrant.get(grantId);
        if (grant == null) {
            reporter.reportGrantUnknownHeartbeat(grantId);
        } else {
            grant.heartbeat(transmitExpireTime, null, null);
            grant.setState(GrantState.IDLE);
        }
    }

    public void relinquishGrant(final String grantId) {
        final Grant grant = grantIdToGrant.get(grantId);
        if (grant == null) {
            reporter.reportGrantUnknown(grantId);
        } else {
            grant.setState(GrantState.IDLE);
            freqStartToGrant.remove(grant.getFrequencyRangeHz().getFrequencyStart());
            grantIdToGrant.remove(grantId);
        }
    }

    public boolean isValidTxExpireTime(final FrequencyRangeHz rangeHz, final Instant txExpireTime, final Duration txExpireUpdateTolerance) {
        boolean foundGrantInRange = false;
        for (final Grant grant : freqStartToGrant.values()) {
            if (grant.getFrequencyRangeHz().getFrequencyEnd() > rangeHz.getFrequencyStart() && grant.getFrequencyRangeHz().getFrequencyStart() < rangeHz.getFrequencyEnd()) {
                foundGrantInRange = true;
                final Instant grantTransmitExpireTime = grant.getTransmitExpireTime();
                if (grantTransmitExpireTime == null) {
                    logger.debug("Invalid tx-expire time. Grant does not have a tx-expire time. grant-freq={}, range={}, tx-expire={}", grant.getFrequencyRangeHz().getFrequencyStart(), rangeHz, txExpireTime);
                    return false;
                } else if (txExpireTime.isAfter(grantTransmitExpireTime.plus(txExpireUpdateTolerance == null ? Duration.ZERO : txExpireUpdateTolerance))) {
                    logger.info("Invalid tx-expire time too high. grant-freq={}, range={}, tx-expire={}, grant-tx-expire={}", grant.getFrequencyRangeHz().getFrequencyStart(), rangeHz, txExpireTime, grantTransmitExpireTime);
                    return false;
                }
            }
        }
        if (!foundGrantInRange) {
            logger.debug("Invalid tx-expire time. No grant in range. range={}, tx-expire={}", rangeHz, txExpireTime);
        }
        return foundGrantInRange;
    }

    public boolean isFrequencyRangeAuthorized(final FrequencyRangeHz rangeHz) {
        logger.trace("Checking if frequency is authorized. cbsd-id={}, range={}", cbsdId, rangeHz);
        Predicate<Grant> grantAuthorizedPredicate = (grant -> {
            final boolean shouldBeTransmitting = grant.shouldBeTransmitting();
            if (!shouldBeTransmitting) {
                logger.debug("Frequency-range not authorized. Grant not authorized. cbsd={}, grant={}, frequency-start={}", cbsdId,
                        grant.getGrantId(), grant.getFrequencyRangeHz().getFrequencyStart());
            }
            return shouldBeTransmitting;
        });

        if (rangeHz == null) {
            return false;
        }
        return checkFrequencyRangeGrants(rangeHz, grantAuthorizedPredicate);
    }

    public boolean isFrequencyRangeTransmissionPermitted(final FrequencyRangeHz rangeHz, final Duration tolerance) {
        logger.trace("Checking if frequency transmission is permitted. cbsd-id={}, range={}, tolerance={}", cbsdId, rangeHz, tolerance);
        Predicate<Grant> grantTransmissionPredicate = (grant -> {
            final boolean permitted = grant.canBeTransmitting(tolerance);
            if (!permitted) {
                logger.info("Frequency-range transmission not permitted. cbsd={}, grant={}, frequency-start={}, tx-expiration={}", cbsdId,
                        grant.getGrantId(), grant.getFrequencyRangeHz().getFrequencyStart(), grant.getTransmitExpireTime());
            }
            return permitted;
        });
        return checkFrequencyRangeGrants(rangeHz, grantTransmissionPredicate);
    }

    private boolean checkFrequencyRangeGrants(final FrequencyRangeHz rangeHz, final Predicate<Grant> grantPredicate) {
        if (!CbsdState.REGISTERED.equals(state)) {
            logger.trace("Frequency-range not authorized. CBSD not registered. cbsd={}", cbsdId);
            return false;
        }
        final ArrayList<Long> sortedFreqStarts = new ArrayList<>(freqStartToGrant.keySet());
        sortedFreqStarts.sort(Comparator.naturalOrder());
        for (int i = sortedFreqStarts.size() - 1; i >= 0; i--) {
            if (sortedFreqStarts.get(i) <= rangeHz.getFrequencyStart()) {
                long previousFreqEnd = sortedFreqStarts.get(i);
                for (int j = i; j < sortedFreqStarts.size(); j++) {
                    final Long grantFreqStart = sortedFreqStarts.get(j);
                    if (grantFreqStart > previousFreqEnd) {
                        // Frequency gap..
                        logger.trace("Frequency-range not authorized. No grant at frequency. cbsd={}, frequency-start={}, frequency-end={}", cbsdId,
                                previousFreqEnd, grantFreqStart);
                        return false;
                    }
                    final Grant grant = freqStartToGrant.get(grantFreqStart);
                    if (grant == null) {
                        logger.trace("Frequency-range not authorized. No grant at frequency. cbsd={}, frequency-start={}", cbsdId, grantFreqStart);
                        return false;
                    }
                    if (!grantPredicate.test(grant)) {
                        return false;
                    }
                    if (grant.getFrequencyRangeHz().getFrequencyEnd() >= rangeHz.getFrequencyEnd()) {
                        logger.trace("Frequency is authorized. cbsd={}, range={}", cbsdId, rangeHz);
                        return true;
                    }
                    previousFreqEnd = grant.getFrequencyRangeHz().getFrequencyEnd();
                }
            }
        }

        logger.debug("Frequency is NOT authorized. cbsd={}, range={}, frequencies={}", cbsdId, rangeHz, sortedFreqStarts);
        return false;
    }

    public void reportTimeouts() {
        for (final Grant grant : grantIdToGrant.values()) {
            grant.reportIfExpired();
        }
    }

    @JsonView(Json.PrivateView.class)
    public Map<String, Long> getGrantCountByState() {
        cleanStaleGrants();
        return grantIdToGrant.values().stream()
                .collect(Collectors.groupingBy(grant -> grant.getState().toString(), Collectors.counting()));
    }

    public int getGrantCount() {
        cleanStaleGrants();
        return grantIdToGrant.size();
    }

    public synchronized void reset() {
        state = CbsdState.UNREGISTERED;
        cbsdId = null;
        freqStartToGrant.clear();
        grantIdToGrant.clear();
    }

    private void cleanStaleGrants() {
        final List<Grant> grantsToRemove = freqStartToGrant.values().stream()
                .filter(Grant::isStale)
                .collect(Collectors.toList());
        grantsToRemove.forEach(grant -> {
            freqStartToGrant.remove(grant.getFrequencyRangeHz().getFrequencyStart());
            final String grantId = grant.getGrantId();
            if (grantId != null) {
                grantIdToGrant.remove(grantId);
            }
        });
    }

    private boolean reportUnregisteredHeartbeat(final String grantId) {
        if (CbsdState.UNREGISTERED.equals(this.state)) {
            reporter.reportGrantUnregisteredHeartbeat(grantId);
            return true;
        }
        return false;
    }

}
