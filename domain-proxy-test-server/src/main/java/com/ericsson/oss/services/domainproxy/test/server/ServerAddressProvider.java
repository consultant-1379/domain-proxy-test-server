package com.ericsson.oss.services.domainproxy.test.server;

import com.github.maltalex.ineter.range.IPv4Range;
import com.github.maltalex.ineter.range.IPv6Range;
import java.util.List;

public class ServerAddressProvider {
    private final IPListProvider iPListProvider;
    private final IPv4AddressProvider iPv4AddressProvider;
    private final IPv6AddressProvider iPv6AddressProvider;

    /**
     * Parses the given String into an {@link IPv4Range} The String can be either a
     * single address, a range such as "192.168.0.0-192.168.1.2" or a subnet such as
     * "192.168.0.0/16"
     * OR:
     * Parses the given String into an {@link IPv6Range} The String can be either a
     * single address, a range such as "fde9:a99c:770f:652:62::1-fde9:a99c:770f:652:62::7d1"
     *
     * @param addressList - a List<String> representing IP Addresses
     * @param v4AddressRange - a String representation of a single IPv4 address, a range or a
     *             subnet
     * @param v6AddressRange - a String representation of a single IPv6 address, a range or a
     *             subnet
     * @return An {@link ServerAddressProvider}
     */
    public static ServerAddressProvider from(final List<String> addressList, final String v4AddressRange, final String v4PortRange,
                                             final String v6AddressRange, final String v6PortRange) {
        IPListProvider iPListProvider = null;
        IPv4AddressProvider iPv4AddressProvider = null;
        IPv6AddressProvider iPv6AddressProvider = null;
        if (addressList != null && v4PortRange != null) {
            iPListProvider = IPListProvider.from(addressList, v4PortRange);
        }
        if (v4AddressRange != null && v4PortRange != null) {
            iPv4AddressProvider = IPv4AddressProvider.from(v4AddressRange, v4PortRange);
        }
        if (v6AddressRange != null && v6PortRange != null) {
            iPv6AddressProvider = IPv6AddressProvider.from(v6AddressRange, v6PortRange);
        }
        return new ServerAddressProvider(iPListProvider, iPv4AddressProvider, iPv6AddressProvider);
    }

    public ServerAddress nextIPListAddress() {
        if (iPListProvider == null) {
            throw new IllegalStateException("Missing IP Address List configuration");
        }
        return iPListProvider.nextAddress();
    }
    public ServerAddress nextV4Address() {
        if (iPv4AddressProvider == null) {
            throw new IllegalStateException("Missing IPv4 configuration");
        }
        return iPv4AddressProvider.nextAddress();
    }

    public ServerAddress nextV6Address() {
        if (iPv6AddressProvider == null) {
            throw new IllegalStateException("Missing IPv6 configuration");
        }
        return iPv6AddressProvider.nextAddress();
    }

    public ServerAddressProvider(final IPListProvider iPListProvider, final IPv4AddressProvider iPv4AddressProvider,
                                 final IPv6AddressProvider iPv6AddressProvider) {
        this.iPListProvider = iPListProvider;
        this.iPv4AddressProvider = iPv4AddressProvider;
        this.iPv6AddressProvider = iPv6AddressProvider;
    }

    public IPListProvider getIPListProvider() {
        return iPListProvider;
    }
}