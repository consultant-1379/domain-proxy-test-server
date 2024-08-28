package com.ericsson.oss.services.domainproxy.test.server;

import com.github.maltalex.ineter.base.IPv4Address;
import com.github.maltalex.ineter.range.IPv4Range;
import java.util.Iterator;

public class IPv4AddressProvider {
    private static Iterator<IPv4Address> addressIterator;
    private final IPv4Range iPv4Range;
    private final int portStart;
    private final int portEnd;
    private int currentPort;

    /**
     * Parses the given String into an {@link IPv4Range} The String can be either a
     * single address, a range such as "192.168.0.0-192.168.1.2" or a subnet such as
     * "192.168.0.0/16"
     *
     * @param addressRange - a String representation of a single IPv4 address, a range or a
     *             subnet
     * @return An {@link IPv4Range}
     */
    public static IPv4AddressProvider from(final String addressRange, final String portRange) {
        final IPv4Range iPv4Range = IPv4Range.parse(addressRange);
        final String[] ports = portRange.split("-");
        addressIterator = iPv4Range.iterator(addressRange.indexOf("-") > 0);
        if (ports.length == 1) {
            final int port = Integer.parseInt(ports[0]);
            return new IPv4AddressProvider(iPv4Range, port, port + 1);
        } else if (ports.length == 2) {
            final int portStart = Integer.parseInt(ports[0]);
            final int portEnd = Integer.parseInt(ports[1]);
            if (portStart >= portEnd) {
                throw new IllegalArgumentException("Port number start hast to be less than number port end");
            }
            return new IPv4AddressProvider(iPv4Range, portStart, portEnd);
        } else {
            throw new IllegalArgumentException("Port range is not a valid range format. 0000-9999");
        }
    }

    public IPv4AddressProvider(final IPv4Range iPv4Range, final int portStart, final int portEnd) {
        this.iPv4Range = iPv4Range;
        this.portStart = portStart;
        this.portEnd = portEnd;
        this.currentPort = portStart;
    }

    public ServerAddress nextAddress() {
        IPv4Address next;
        if (addressIterator.hasNext()) {
            next = addressIterator.next();
        } else {
            next = iPv4Range.getLast();
        }
        if (currentPort >= portEnd) {
            currentPort = portStart;
        }
        final ServerAddress serverAddress = new ServerAddress(next.toInetAddress(), currentPort);
        currentPort++;
        return serverAddress;
    }

}
