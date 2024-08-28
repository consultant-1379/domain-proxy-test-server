package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class Fru {
    private static final Logger logger = LoggerFactory.getLogger(Fru.class);
    private final String id;
    private String serialNumber;
    private final AtomicInteger scanId = new AtomicInteger(1);
    private Instant scanStartTime = null;
    private int frequencyStartKhz;
    private int frequencyEndKhz;
    private boolean rcvdPowerScanCapability = true;

    public int startScanRcvdPower(final int frequencyStartKhz, final int frequencyEndKhz) {
        if (!rcvdPowerScanCapability) {
            logger.error("FRU does not have rcvd power scan capability. fru={}", id);
            return -3;
        }
        this.frequencyStartKhz = frequencyStartKhz;
        this.frequencyEndKhz = frequencyEndKhz;
        this.scanStartTime = Instant.now();
        return scanId.incrementAndGet();
    }

    public RcvdPowerScanner generateScanData(final Duration scanDuration, final int bandwidthKhz, final int powerMin, final int powerMax) {
        final int currentScanId = getScanIdForDuration(scanDuration);
        final ScanResult scanResult = generateScanResult(bandwidthKhz, powerMin, powerMax);
        return RcvdPowerScanner.forResults(currentScanId, scanResult);
    }

    protected ScanResult generateScanResult(final int bandwidthKhz, final int powerMin, final int powerMax) {
        final Random powerReader = new Random();
        final int powerBound = Math.abs(powerMax - powerMin);
        final int resultSize = (frequencyEndKhz - frequencyStartKhz) / bandwidthKhz;
        final ScanResult scanResult = ScanResult.resultWithSize(resultSize);
        int resultIndex = 0;
        for (int freq = frequencyStartKhz; freq < frequencyEndKhz; freq += bandwidthKhz) {
            scanResult.getFreqKhz()[resultIndex] = freq;
            scanResult.getBandwidthKhz()[resultIndex] = bandwidthKhz;
            if (powerBound > 0) {
                scanResult.getRcvdPower()[resultIndex] = powerMin + powerReader.nextInt(powerBound);
            } else {
                scanResult.getRcvdPower()[resultIndex] = powerMin;
            }
            resultIndex++;
        }
        return scanResult;
    }

    protected int getScanIdForDuration(final Duration scanDuration) {
        if (scanStartTime == null || Instant.now().isBefore(scanStartTime.plus(scanDuration))) {
            return scanId.get() - 1;
        }
        return scanId.get();
    }

    @XmlRootElement(name = "RcvdPowerScanner")
    @XmlAccessorType(XmlAccessType.FIELD)
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RcvdPowerScanner {
        @XmlElement
        private String rcvdPowerScannerId = "1";
        @XmlElement
        private int scanId;
        @XmlElement(name = "scanResult")
        private ScanResult [] scanResult;

        public static RcvdPowerScanner forResults(final int scanId, final ScanResult... results) {
            return new RcvdPowerScanner("1", scanId, results);
        }
    }

    @Data
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ScanResult {

        public static ScanResult resultWithSize(final int resultSize) {
            final ScanResult scanResult = new ScanResult();
            scanResult.setBandwidthKhz(new int[resultSize]);
            scanResult.setFreqKhz(new int[resultSize]);
            scanResult.setRcvdPower(new int[resultSize]);
            return scanResult;
        }

        @XmlAttribute(name = "struct")
        private String struct="RcvdPowerReport";
        @XmlElement(name = "freq")
        private int [] freqKhz;
        @XmlElement(name = "bandwidth")
        private int [] bandwidthKhz;
        @XmlElement(name = "rcvdPower")
        private int [] rcvdPower;
    }

}
