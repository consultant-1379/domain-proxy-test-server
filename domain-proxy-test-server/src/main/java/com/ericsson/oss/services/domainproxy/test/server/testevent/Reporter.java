package com.ericsson.oss.services.domainproxy.test.server.testevent;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.FrequencyRangeHz;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Reporter {
    private static final Logger logger = LoggerFactory.getLogger(Reporter.class);
    private final ExecutorService reportExecutor = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10000), new ThreadPoolExecutor.CallerRunsPolicy());
    private final List<Report> reports;

    public Reporter(final @NonNull Collection<Report> reports) {
        this.reports = new ArrayList<>(reports);
    }

    public void start() {
        logger.debug("Starting Reporter: reports={}", reports);
    }

    public void stop() {
        logger.debug("Stopping Reporter");
        reportExecutor.shutdownNow();
        try {
            reportExecutor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.info("Reporter shutdown interruped.");
            Thread.currentThread().interrupt();
        } finally {
            for (final Report report : reports) {
                try {
                    report.terminate();
                } catch (Exception e) {
                    logger.error("Failure during report termination.", e);
                }
            }
        }
    }

    public void reportCellTransmitting(final String cellId) {
        doReportSync(report -> report.reportCellTransmitting(cellId));
    }

    public void reportCellNotTransmitting(final String cellId) {
        doReportSync(report -> report.reportCellNotTransmitting(cellId));
    }

    public void reportTransmissionWithoutAuthorization(final String cellId) {
        doReportAsync(report -> report.reportTransmissionWithoutAuthorization(cellId));
    }

    public void reportUntrackedCbsd(final String cbsdSerial, final String cbsdId) {
        doReportAsync(report -> report.reportUntrackedCbsd(cbsdSerial, cbsdId));
    }

    public void reportUntrackedCell(final String cellId) {
        doReportAsync(report -> report.reportUntrackedCell(cellId));
    }

    public void reportGrantExpired(final String grantId, final Instant grantExpireTime) {
        doReportAsync(report -> report.reportGrantExpired(grantId, grantExpireTime));
    }

    public void reportGrantTransmitExpired(final String grantId, final Instant transmitExpireTime) {
        doReportAsync(report -> report.reportGrantTransmitExpired(grantId, transmitExpireTime));
    }

    public void reportGrantUnexpectedHeartbeat(final String grantId) {
        doReportAsync(report -> report.reportGrantUnexpectedHeartbeat(grantId));
    }

    public void reportGrantLateHeartbeat(final String grantId, final Instant lastHeartbeat, final Instant now) {
        doReportAsync(report -> report.reportGrantLateHeartbeat(grantId, lastHeartbeat, now));
    }

    public void reportGrantUnregisteredHeartbeat(final String grantId) {
        doReportAsync(report -> report.reportGrantUnregisteredHeartbeat(grantId));
    }

    public void reportGrantUnknownHeartbeat(final String grantId) {
        doReportAsync(report -> report.reportGrantUnknownHeartbeat(grantId));
    }

    public void reportGrantUnknown(final String grantId) {
        doReportAsync(report -> report.reportGrantUnknown(grantId));
    }

    public void reportCellTxInterrupted(final String cellId) {
        doReportAsync(report -> report.reportCellTxInterrupted(cellId));
    }

    public void reportCellTxUpdated(final String cellId, final long amount) {
        doReportAsync(report -> report.reportCellTxUpdated(cellId, amount));
    }

    public void reportCellTxBeyondPermitted(final String cellId, final Instant txExpirationTime,
                                            final FrequencyRangeHz frequencyRangeHz) {
        doReportAsync(report -> report.reportCellTxBeyondPermitted(cellId, txExpirationTime, frequencyRangeHz));
    }

    public void reportCellTxSetTooHigh(final String cellId, final Instant txExpirationTime) {
        doReportAsync(report -> report.reportCellTxSetTooHigh(cellId, txExpirationTime));
    }

    public void reportRegistrationRequestReceived() {
        doReportAsync(Report::reportRegistrationRequestReceived);
    }

    public void reportSpectrumInquiryRequestReceived() {
        doReportAsync(Report::reportSpectrumInquiryRequestReceived);
    }

    public void reportGrantRequestReceived() {
        doReportAsync(Report::reportGrantRequestReceived);
    }

    public void reportHeartbeatRequestReceived() {
        doReportAsync(Report::reportHeartbeatRequestReceived);
    }

    public void reportRelinquishmentRequestReceived() {
        doReportAsync(Report::reportRelinquishmentRequestReceived);
    }

    public void reportDeregistrationRequestReceived() {
        doReportAsync(Report::reportDeregistrationRequestReceived);
    }

    public void reportTotalGrantsByState(final Map<String, Long> grantsByState) {
        doReportAsync(report -> report.reportTotalGrantsByState(grantsByState));
    }

    public void reportCbsdRegistered(final String cbsdSeria, final String cbsdId) {
        doReportAsync(report -> report.reportCbsdRegistered(cbsdSeria, cbsdId));
    }

    public void reportCbsdUnregistered(final String cbsdSeria) {
        doReportAsync(report -> report.reportCbsdUnregistered(cbsdSeria));
    }

    public void reset() {
        for (final Report report : reports) {
            try {
                report.reset();
            } catch (Exception e) {
                logger.error("Failure during report reset.", e);
            }
        }
    }

    private void doReportAsync(final Consumer<Report> reportConsumer) {
        Runnable toReport = () -> this.reports.forEach(reportConsumer);
        reportExecutor.submit(toReport);
    }

    private void doReportSync(final Consumer<Report> reportConsumer) {
        this.reports.forEach(reportConsumer);
    }
}
