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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.github.maltalex.ineter.base.IPv4Address;
import com.github.maltalex.ineter.range.IPv4Range;

public class Ipv4AddressProvider {
    private final Iterator<IPv4Address> addressIterator;

    private Ipv4AddressProvider(final Iterator<IPv4Address> addressIterator) {
        this.addressIterator = addressIterator;
    }

    public static Ipv4AddressProvider from(final String ipAddressRange) {
        final IPv4Range iPv4Range = IPv4Range.parse(ipAddressRange);
        return new Ipv4AddressProvider(iPv4Range.iterator());
    }

    public Set<String> getIpAddresses() {
        final Set<String> ipAddresses = new HashSet<>();
        while (addressIterator.hasNext()) {
            final IPv4Address iPv4Address = addressIterator.next();
            ipAddresses.add(iPv4Address.toString());
        }
        return ipAddresses;
    }
}
