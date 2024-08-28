package com.ericsson.oss.services.domainproxy.test.server;

import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.net.InetAddress;

@RequiredArgsConstructor
@Value
public class ServerAddress {
    private final InetAddress inetAddress;
    private final int port;
}