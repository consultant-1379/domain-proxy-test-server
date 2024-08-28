package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import lombok.Value;

import java.util.List;
import java.util.function.BiConsumer;

@Value
public class ElementValueHolder {
    private final ElementValueProcessor valueProcessor;
    private final String parentFdn;
    private final String value;

    public void valuesWalker(final BiConsumer<Boolean, ElementValueHolder> walker) {
        final List<ElementValueHolder> nextElementValueHolders = valueProcessor.getAssociationsMatching(this);
        walker.accept(nextElementValueHolders.isEmpty(), this);
        nextElementValueHolders.forEach(nextValue -> nextValue.valuesWalker(walker));
    }

    public boolean isParent(final String fdn) {
        return parentFdn.equals(fdn);
    }

    public String getElementName() {
        return valueProcessor.getElementName();
    }
}
