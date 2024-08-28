package com.ericsson.oss.services.domainproxy.test.server;

import com.github.maltalex.ineter.base.IPAddress;
import java.util.Iterator;
import java.util.List;

public class IPListProvider {

    private static Iterator<String> addressIterator;
    private final List<String> iPRange;
    private final int portStart;
    private final int portEnd;
    private int currentPort;

    public static IPListProvider from(final List<String> addressRange, final String portRange) {
        final String[] ports = portRange.split("-");
        addressIterator = addressRange.iterator();
        if (ports.length == 1) {
            final int port = Integer.parseInt(ports[0]);
            return new IPListProvider(addressRange, port, port + 1);
        } else if (ports.length == 2) {
            final int portStart = Integer.parseInt(ports[0]);
            final int portEnd = Integer.parseInt(ports[1]);
            if (portStart >= portEnd) {
                throw new IllegalArgumentException("Port number start has to be less than number port end");
            }
            return new IPListProvider(addressRange, portStart, portEnd);
        } else {
            throw new IllegalArgumentException("Port range is not a valid range format. Valid range is: 0000-9999");
        }
    }

    public ServerAddress nextAddress() {
        IPAddress next;
        if (addressIterator.hasNext()) {
            next = IPAddress.of(addressIterator.next());
        } else {
            next = IPAddress.of(iPRange.get(iPRange.size()));
        }
        if (currentPort >= portEnd) {
            currentPort = portStart;
        }
        final ServerAddress serverAddress = new ServerAddress(next.toInetAddress(), currentPort);
        currentPort++;
        return serverAddress;
    }

    public IPListProvider(final List<String> iPRange, final int portStart, final int portEnd) {
        this.iPRange = iPRange;
        this.portStart = portStart;
        this.portEnd = portEnd;
        this.currentPort = portStart;
    }

}


