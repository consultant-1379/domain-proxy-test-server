/*------------------------------------------------------------------------------
 *******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 *******************************************************************************
 -----------------------------------------------------------------------------*/

package com.ericsson.oss.services.domainproxy.pntestserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DomainCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DomainCoordinator.class);
    private static final String MAPPINGS_DIR = "wiremockroot";

    private final String ipAddress;
    private final boolean verboseLogging;

    public void start() {
        LOGGER.info("Starting domain coordinator. #ipAddress={}, #verboseLogging={}", ipAddress, verboseLogging);
        final WireMockConfiguration configuration = WireMockConfiguration.wireMockConfig()
                .bindAddress(ipAddress)
                .usingFilesUnderClasspath(MAPPINGS_DIR)
                .notifier(new Slf4jNotifier(verboseLogging));
        final WireMockServer wireMockServer = new WireMockServer(configuration);
        wireMockServer.start();
    }
}
