package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.github.tomakehurst.wiremock.common.Json;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class Cell {
    private static final Logger logger = LoggerFactory.getLogger(Cell.class);
    private static final long MHZ_DIVISOR = 1000000;
    @JsonView(Json.PublicView.class)
    private final String cellId;
    @JsonIgnore
    private final Reporter reporter;
    @JsonView(Json.PublicView.class)
    private FrequencyRangeHz frequencyRangeHz;
    @JsonView(Json.PublicView.class)
    private long channelBandwidthHz;
    @JsonView(Json.PublicView.class)
    private long bSChannelBwDL;
    @JsonView(Json.PublicView.class)
    private long bSChannelBwUL;
    @JsonView(Json.PublicView.class)
    private long earfcn;
    @JsonView(Json.PublicView.class)
    private long arfcnDL;
    @JsonView(Json.PublicView.class)
    private long arfcnUL;
    @JsonView(Json.PublicView.class)
    private long ssbFrequency;
    @JsonView(Json.PublicView.class)
    private long ssbFrequencyAutoSelected;
    @JsonView(Json.PublicView.class)
    private long configuredMaxTxPower;
    @JsonView(Json.PublicView.class)
    private Instant txExpirationTime;
    @JsonView(Json.PublicView.class)
    private boolean cbrsEnabled;
    @JsonView(Json.PrivateView.class)
    private Instant authorizationTime;
    @JsonView(Json.PublicView.class)
    private Set<Cbsd> cbsds = new HashSet<>();
    @JsonView(Json.PrivateView.class)
    private boolean txInterruptionReported = false;
    @JsonView(Json.PrivateView.class)
    private boolean txBeyondPermittedReported = false;

    public synchronized void addCbsd(final Cbsd cbsd) {
        this.cbsds.add(cbsd);
    }

    public synchronized void removeCbsd(final Cbsd cbsd) {
        this.cbsds.removeIf(c -> Objects.equals(c.getCbsdId(), cbsd.getCbsdId()));
    }

    private synchronized boolean hasTxInterrupted() {
        if (authorizationTime == null) {
            // not authorized, so no need to check tx...
            return false;
        }
        final Instant now = Instant.now();
        if (txExpirationTime == null || txExpirationTime.isBefore(authorizationTime)) {
            // if TX was not set in 60 seconds return true
            return authorizationTime.plusSeconds(60).isBefore(now);
        }
        return txExpirationTime.isBefore(now);
    }

    public synchronized void updateTxExpireTime(final long amount) {
        if (amount > 0) {
            reportIfTxNotAuthorizedOrTxExpired();
            txExpirationTime = Instant.now().plusSeconds(amount);
            reportIfTxInterrupted();
            reportIfExpireTimeSetTooHigh();
            reporter.reportCellTxUpdated(cellId, amount);
        } else {
            txExpirationTime = Instant.MIN;
        }
        logger.debug("Cell tx-expire time updated. cell={}, expiration={}", cellId, txExpirationTime);
    }

    public synchronized Instant getTxExpirationTime() {
        if (txExpirationTime == Instant.MIN) {
            return null;
        }
        return txExpirationTime;
    }

    public synchronized void reportCellTransmittingState() {
        if (isNotTransmitting()) {
            reporter.reportCellNotTransmitting(cellId);
        } else {
            reporter.reportCellTransmitting(cellId);
        }
    }

    @JsonView(Json.PrivateView.class)
    public boolean isNotTransmitting() {
        return txExpirationTime == null || txExpirationTime.isBefore(Instant.now());
    }

    public boolean isTransmitting() {
        return !isNotTransmitting();
    }

    public synchronized void reportIfTransmissionInterrupted() {
        final boolean isCellAuthorized = isCellAuthorized();
        if (isCellAuthorized) {
            reportIfTxInterrupted();
        }
    }

    public synchronized void reportIfTransmissionBeyondPermitted() {
        final Instant now = Instant.now();
        if (txExpirationTime != null && txExpirationTime != Instant.MIN && !txExpirationTime.isBefore(now)) {
            boolean txPermitted = true;
            for (final Cbsd cbsd : cbsds) {
                if (!cbsd.isFrequencyRangeTransmissionPermitted(frequencyRangeHz, Duration.ofSeconds(59))) {
                    txPermitted = false;
                    break;
                }
            }

            if (txPermitted) {
                txBeyondPermittedReported = false;
            } else if (!txBeyondPermittedReported) {
                txBeyondPermittedReported = true;
                reporter.reportCellTxBeyondPermitted(cellId, txExpirationTime, frequencyRangeHz);
            }
        }
    }

    private synchronized void reportIfTxInterrupted() {
        if (hasTxInterrupted()) {
            if (!txInterruptionReported) {
                reporter.reportCellTxInterrupted(cellId);
                txInterruptionReported = true;
            }
        } else {
            txInterruptionReported = false;
        }
    }

    private void reportIfExpireTimeSetTooHigh() {
        final boolean validTxExpireTime = cbsds.stream()
                .allMatch(cbsd -> cbsd.isValidTxExpireTime(frequencyRangeHz, txExpirationTime, Duration.ofSeconds(5)));
        if (!validTxExpireTime) {
            reporter.reportCellTxSetTooHigh(cellId, txExpirationTime);
        }
    }

    public synchronized void setChannelBandwidthHz(final long channelBandwidthHz) {
        this.channelBandwidthHz = channelBandwidthHz;
        updateFrequencyRangeForLTE();
    }

    public synchronized void setCellEarfcn(final long earfcn) {
        this.earfcn = earfcn;
        updateFrequencyRangeForLTE();
    }

    public synchronized void setBSChannelBwDLHz(final long bSChannelBwDL) {
        this.bSChannelBwDL = bSChannelBwDL;
    }

    public synchronized void setBSChannelBwULHz(final long bSChannelBwUL) {
        this.bSChannelBwUL = bSChannelBwUL;
    }

    public synchronized void setCellArfcnUL(final long arfcnUL) {
        this.arfcnUL = arfcnUL;
        updateFrequencyRangeForNR();
    }

    public synchronized void setCellArfcnDL(final long arfcnDL) {
        this.arfcnDL = arfcnDL;
        updateFrequencyRangeForNR();
    }

    public synchronized void setCellSsbFrequency(long ssbFrequency) {
        this.ssbFrequency = ssbFrequency;
    }

    public synchronized void setCellSsbFrequencyAutoSelected(long ssbFrequencyAutoSelected) {
        this.ssbFrequencyAutoSelected = ssbFrequencyAutoSelected;
    }

    public synchronized void setCellconfiguredMaxTxPower(long configuredMaxTxPower) {
        this.configuredMaxTxPower = configuredMaxTxPower;
    }

    @JsonView(Json.PrivateView.class)
    public synchronized boolean isCellAuthorized() {
        boolean authorized = !cbsds.isEmpty();
        for (final Cbsd cbsd : cbsds) {
            if (!cbsd.isFrequencyRangeAuthorized(frequencyRangeHz)) {
                authorized = false;
                break;
            }
        }

        if (authorized) {
            if (this.authorizationTime == null) {
                this.authorizationTime = Instant.now();
            }
        } else {
            this.authorizationTime = null;
        }
        return authorized;
    }

    public synchronized void reset() {
        this.authorizationTime = null;
        this.txExpirationTime = null;
        this.txInterruptionReported = false;
        this.txBeyondPermittedReported = false;
    }

    private void updateFrequencyRangeForLTE() {
        long F_DL_lowMhz = 3550; // B48
        long N_Offs_DL = 55240; // B48
        if (this.getChannelBandwidthHz() > 0) {
            double midFreqHz = ((this.earfcn - N_Offs_DL) / 10 + F_DL_lowMhz) * MHZ_DIVISOR;
            final long halfBw = this.getChannelBandwidthHz() / 2;
            this.frequencyRangeHz = new FrequencyRangeHz((long) midFreqHz - halfBw, (long) midFreqHz + halfBw);
            logger.info("Cell frequency updated. cell={}, frequency={}", cellId, frequencyRangeHz);
        }
    }

    private void updateFrequencyRangeForNR() {
        long N_REF_Offs = 600_000;
        long F_REF_Offs_Hz = 3_000_000;
        long Delta_F_Global_kHz​ = 15;
        long arfcn = this.arfcnDL | this.arfcnUL;
        long channelBandwidthMHz = this.bSChannelBwDL | this.bSChannelBwUL;
        if (channelBandwidthMHz > 0) {
            long channelBandwidthKHz = channelBandwidthMHz * 1_000;
            double F_REF = F_REF_Offs_Hz + Delta_F_Global_kHz​ * (arfcn - N_REF_Offs);
            long centerFreqHz = Math.round(F_REF / 1000) * 1000;
            logger.info("Cell centerFreqHz. cell={}, centerFreqHz={}", cellId, centerFreqHz);
            final long halfBw = channelBandwidthKHz / 2;
            this.frequencyRangeHz = new FrequencyRangeHz((centerFreqHz - halfBw) * 1000, (centerFreqHz + halfBw) * 1000);
            logger.info("Cell frequency updated. cell={}, frequency range={}", cellId, frequencyRangeHz);
        }
    }

    private void reportIfTxNotAuthorizedOrTxExpired() {
        final boolean isCellAuthorized = isCellAuthorized();
        if (isCellAuthorized) {
            reportIfTxInterrupted();
        } else {
            boolean txPermitted = cbsds.stream().allMatch(cbsd -> cbsd.isFrequencyRangeTransmissionPermitted(frequencyRangeHz, Duration.ofSeconds(59)));
            if (!txPermitted) {
                reporter.reportTransmissionWithoutAuthorization(cellId);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        Cell cell = (Cell) obj;
        return this.cellId.equals(cell.getCellId());
    }

}
