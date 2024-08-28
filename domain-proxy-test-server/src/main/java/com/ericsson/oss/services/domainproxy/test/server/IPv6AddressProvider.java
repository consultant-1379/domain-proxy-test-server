package com.ericsson.oss.services.domainproxy.test.server;

import com.github.maltalex.ineter.base.IPv6Address;
import com.github.maltalex.ineter.range.IPv6Range;

import java.util.Iterator;

public class IPv6AddressProvider {
    private static Iterator<IPv6Address> addressIterator;
    private final IPv6Range iPv6Range;
    private final int portStart;
    private final int portEnd;
    private int currentPort;

    /**
     * Parses the given String into an {@link IPv6Range} The String can be either a
     * single address, a range such as "fde9:a99c:770f:652:62::1-fde9:a99c:770f:652:62::7d1"
     *
     * @param addressRange - a String representation of a single IPv6 address, a range or a
     *             subnet
     * @return An {@link IPv6Range}
     */
    public static IPv6AddressProvider from(final String addressRange, final String portRange) {
        final IPv6Range iPv6Range = IPv6Range.parse(addressRange);
        final String[] ports = portRange.split("-");
        addressIterator = iPv6Range.iterator(true);
        if (ports.length == 1) {
            final int port = Integer.parseInt(ports[0]);
            return new IPv6AddressProvider(iPv6Range, port, port + 1);
        } else if (ports.length == 2) {
            final int portStart = Integer.parseInt(ports[0]);
            final int portEnd = Integer.parseInt(ports[1]);
            if (portStart >= portEnd) {
                throw new IllegalArgumentException("Port number start hast to be less than number port end");
            }
            return new IPv6AddressProvider(iPv6Range, portStart, portEnd);
        } else {
            throw new IllegalArgumentException("Port range is not a valid range format. 0000-9999");
        }
    }

    public IPv6AddressProvider(final IPv6Range iPv6Range, final int portStart, final int portEnd) {
        this.iPv6Range = iPv6Range;
        this.portStart = portStart;
        this.portEnd = portEnd;
        this.currentPort = portStart;
    }

    public ServerAddress nextAddress() {
        IPv6Address next;
        if (addressIterator.hasNext()) {
            next = addressIterator.next();
        } else {
            next = iPv6Range.getLast();
        }
        if (currentPort >= portEnd) {
            currentPort = portStart;
        }
        final ServerAddress serverAddress = new ServerAddress(next.toInetAddress(), currentPort);
        currentPort++;
        return serverAddress;
    }
}
