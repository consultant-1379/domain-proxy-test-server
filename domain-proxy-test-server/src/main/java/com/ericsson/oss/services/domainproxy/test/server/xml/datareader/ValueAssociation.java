package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

@FunctionalInterface
public interface ValueAssociation {
    boolean isMatch(ElementValueHolder source, ElementValueHolder target);
}
