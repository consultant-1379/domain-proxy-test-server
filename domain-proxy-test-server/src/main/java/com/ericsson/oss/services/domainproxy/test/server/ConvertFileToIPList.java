package com.ericsson.oss.services.domainproxy.test.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConvertFileToIPList {

    private static final Logger logger = LoggerFactory.getLogger(ConvertFileToIPList.class);
    private final List<String> ipStringsList = new ArrayList<>();

    public List<String> readIpAddFile(final String ipAddPath) {
        final File ipAddFile = new File(ipAddPath);
        try (final Scanner scanner = new Scanner(ipAddFile)) {
            while(scanner.hasNextLine()) {
                ipStringsList.add(scanner.nextLine());
            }
        } catch (final Exception e) {
            logger.error("Exception thrown when attempting to read the IP address file.", e);
        }
        return ipStringsList;
    }
}
