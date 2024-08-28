package com.ericsson.oss.services.domainproxy.test.server.testevent;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.FrequencyRangeHz;

import java.time.Instant;
import java.util.Map;

public interface Report {

    void initialize(final Map<String, String> parameters) throws Exception;

    void terminate();

    void reportUntrackedCbsd(final String cbsdSerial, final String cbsdId);

    void reportUntrackedCell(final String cellId);

    void reportGrantExpired(final String grantId, final Instant grantExpireTime);

    void reportGrantTransmitExpired(final String grantId, final Instant transmitExpireTime);

    void reportGrantUnexpectedHeartbeat(final String grantId);

    void reportGrantLateHeartbeat(final String grantId, final Instant lastHeartbeat, final Instant now);

    void reportGrantUnregisteredHeartbeat(final String grantId);

    void reportGrantUnknownHeartbeat(final String grantId);

    void reportGrantUnknown(final String grantId);

    void reportCellNotTransmitting(final String cellId);

    void reportCellTransmitting(final String cellId);

    void reportTransmissionWithoutAuthorization(final String cellId);

    void reportCellTxInterrupted(final String cellId);

    void reportCellTxUpdated(final String cellId, final long amount);

    void reportCellTxSetTooHigh(String cellId, Instant txExpirationTime);

    void reportCellTxBeyondPermitted(String cellId, Instant txExpirationTime,
                                     final FrequencyRangeHz frequencyRangeHz);

    void reportRegistrationRequestReceived();

    void reportSpectrumInquiryRequestReceived();

    void reportGrantRequestReceived();

    void reportHeartbeatRequestReceived();

    void reportRelinquishmentRequestReceived();

    void reportDeregistrationRequestReceived();

    void reportTotalGrantsByState(Map<String, Long> grantsByState);

    void reportCbsdRegistered(String cbsdSerial, String cbsdId);

    void reportCbsdUnregistered(String cbsdSerial);

    void reset();
}
