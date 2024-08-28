package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import joptsimple.internal.Strings;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

class FdnStack {
    private final Deque<FdnSegment> fndStack = new ArrayDeque<>();

    public void addSegment(final FdnSegment segment) {
        fndStack.push(segment);
    }

    public FdnSegment popSegment() {
        return fndStack.pop();
    }

    public FdnSegment last() {
        return fndStack.peek();
    }

    public boolean isLastElement(final String elementName) {
        if (fndStack.size() > 0) {
            final FdnSegment peek = fndStack.peek();
            return peek.getElement().equals(elementName);
        }
        return false;
    }

    public String toString() {
        final List<String> segmentsStrings = fndStack.stream().map(FdnSegment::toString).collect(Collectors.toList());
        Collections.reverse(segmentsStrings);
        return Strings.join(segmentsStrings, ",");
    }

    public boolean isEmpty() {
        return fndStack.isEmpty();
    }
}
