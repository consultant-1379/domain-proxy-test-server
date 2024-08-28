package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import lombok.Value;

@Value
class FdnSegment {
    private final String element;
    private final String id;

    public String toString() {
        return element + "=" + id;
    }
}
