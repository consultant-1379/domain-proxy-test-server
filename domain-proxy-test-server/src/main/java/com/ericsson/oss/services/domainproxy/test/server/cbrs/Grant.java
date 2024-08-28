package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Data
@RequiredArgsConstructor
public class Grant {
    private final String grantId;
    private final Reporter reporter;
    private GrantState state = GrantState.IDLE;
    private FrequencyRangeHz frequencyRangeHz;
    private Instant grantExpireTime;
    @Getter
    private Instant transmitExpireTime;
    private Long heartbeatInterval;
    private Instant lastHeartbeat;
    private boolean grantExpiredReported;
    private boolean grantTxExpiredReported;

    synchronized boolean shouldBeTransmitting() {
        return GrantState.AUTHORIZED.equals(getState()) && Instant.now().isBefore(grantExpireTime);
    }

    synchronized boolean canBeTransmitting(final Duration tolerance) {
        return hasTransmitExpireTimeSet() && !transmitExpireTime.plus(tolerance).isBefore(Instant.now());
    }

    public synchronized  void heartbeat(final Instant transmitExpireTime, final Instant grantExpireTime, final Long heartbeatInterval) {
        final Instant now = Instant.now();
        reportIfExpired();
        if (GrantState.IDLE.equals(state)) {
            reporter.reportGrantUnexpectedHeartbeat(grantId);
        }
        if (lastHeartbeat != null && heartbeatInterval != null) {
            if (now.isAfter(lastHeartbeat.plusSeconds(heartbeatInterval))) {
                reporter.reportGrantLateHeartbeat(grantId, lastHeartbeat, now);
            }
        }

        lastHeartbeat = now;
        if (transmitExpireTime != null) {
            this.transmitExpireTime = transmitExpireTime;
        }
        if (grantExpireTime != null) {
            this.grantExpireTime = grantExpireTime;
        }
        if (heartbeatInterval != null) {
            this.heartbeatInterval = heartbeatInterval;
        }
    }

    public synchronized boolean isTransmitting() {
        return hasTransmitExpireTimeSet() && !transmitExpireTime.isBefore(Instant.now());
    }

    public synchronized  void reportIfExpired() {
        final Instant now = Instant.now();
        if (grantExpireTime != null && now.isAfter(grantExpireTime)) {
            if (!grantExpiredReported) {
                grantExpiredReported = true;
                reporter.reportGrantExpired(grantId, grantExpireTime);
            }
        } else {
            grantExpiredReported = false;
        }

        if (GrantState.AUTHORIZED.equals(this.state)) {
            if (transmitExpireTime != null && now.isAfter(transmitExpireTime)) {
                if (!grantTxExpiredReported) {
                    grantTxExpiredReported = true;
                    reporter.reportGrantTransmitExpired(grantId, transmitExpireTime);
                }
            }
        } else {
            grantTxExpiredReported = false;
        }
    }

    public synchronized boolean hasTransmitExpireTimeSet() {
        return transmitExpireTime != null;
    }

    public synchronized boolean isStale() {
        return GrantState.IDLE.equals(this.state) && isTransmitExpireTimeStale();
    }

    private boolean isTransmitExpireTimeStale() {
        if (transmitExpireTime == null) {
            return true;
        }
        return transmitExpireTime.plus(Duration.ofSeconds(90)).isBefore(Instant.now());
    }
}
