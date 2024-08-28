package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
class ElementAssociation {
    private final ValueAssociation association;
    private final ElementValueProcessor nextProcessor;

    void processElement(final ValueElement element, final FdnStack fdnStack){
        nextProcessor.processElement(element, fdnStack);
    }

    List<ElementValueHolder> getValuesAssociatedTo(final ElementValueHolder source) {
        return nextProcessor.selectValues(target -> association.isMatch(source, target));
    }
}
