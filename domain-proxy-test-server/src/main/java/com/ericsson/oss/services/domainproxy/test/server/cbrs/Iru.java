package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class Iru extends Fru {
    private static final Logger logger = LoggerFactory.getLogger(Iru.class);
    private Map<Integer, String> rdiPortRef = new HashMap<>();
    private int highestPortNumber;

    public Iru(final String id) {
        super(id);
    }

    public void addPortMap(final String rdiPortId, final String rdiPortRefId) {
        final String id = rdiPortId.substring(rdiPortId.lastIndexOf('=') + 1);
        try {
            final Integer portNumber = Integer.valueOf(id);
            if (portNumber > highestPortNumber) {
                highestPortNumber = portNumber;
            }
            this.rdiPortRef.put(portNumber, rdiPortRefId);
        } catch (NumberFormatException e) {
            logger.error("RdiPort id is not numeric. RdiPort can't be added. rdiPortId={}", rdiPortId);
        }
    }

    @Override
    public RcvdPowerScanner generateScanData(final Duration scanDuration, final int bandwidthKhz, final int powerMin, final int powerMax) {
        final int currentScanId = getScanIdForDuration(scanDuration);
        final ScanResult[] results = new ScanResult[highestPortNumber];
        for (int i = 0; i < highestPortNumber; i++) {
            if (rdiPortRef.containsKey(i + 1)) {
                results[i] = generateScanResult(bandwidthKhz, powerMin, powerMax);
            } else {
                results[i] = generateScanResult(bandwidthKhz, -1, -1);
            }
        }
        return RcvdPowerScanner.forResults(currentScanId, results);
    }
}
