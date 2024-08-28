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

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            LOGGER.error("Configuration file not provided");
            System.exit(1);
        }
        final String configurationFileName = args[0];
        final PrivateNetworkTestServer privateNetworkTestServer = new PrivateNetworkTestServer(configurationFileName);
        privateNetworkTestServer.start();
    }
}
