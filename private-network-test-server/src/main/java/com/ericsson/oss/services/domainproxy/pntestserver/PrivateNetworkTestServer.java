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

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PrivateNetworkTestServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrivateNetworkTestServer.class);
    private final String configurationFileName;
    private int domainCoordinatorsStarted = 0;

    public void start() {
        LOGGER.info("\n===== STARTING MOCK PRIVATE NETWORK TEST SERVER =====\n");
        try {
            final PrivateNetworkConfiguration configuration = loadConfiguration(configurationFileName);
            final Ipv4AddressProvider ipv4AddressProvider = Ipv4AddressProvider.from(configuration.getIpRange());
            final Set<String> ipAddresses = ipv4AddressProvider.getIpAddresses();
            final boolean verboseLogging = configuration.isVerboseLogging();
            ipAddresses.forEach(ipAddress -> startDomainCoordinator(ipAddress, verboseLogging));
            LOGGER.info("\n===== MOCK PRIVATE NETWORK TEST SERVER STARTED =====\n");
        } catch (final Exception ex) {
            LOGGER.error("Error starting mock private network server", ex);
        }
    }

    private PrivateNetworkConfiguration loadConfiguration(final String configurationFileName) throws IOException {
        final File configurationFile = new File(configurationFileName);
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        final PrivateNetworkConfiguration configuration = objectMapper.readValue(configurationFile, PrivateNetworkConfiguration.class);
        LOGGER.info("Configuration loaded. #configuration={}", configuration);
        return configuration;
    }

    private void startDomainCoordinator(final String ipAddress, final boolean verboseLogging) {
        try {
            final DomainCoordinator domainCoordinator = new DomainCoordinator(ipAddress, verboseLogging);
            domainCoordinator.start();
            domainCoordinatorsStarted++;
            LOGGER.info("{} domain coordinators started", domainCoordinatorsStarted);
        } catch (final Exception ex) {
            LOGGER.error("Error starting domain coordinator. #ipAddress={}", ipAddress, ex);
        }
    }
}
