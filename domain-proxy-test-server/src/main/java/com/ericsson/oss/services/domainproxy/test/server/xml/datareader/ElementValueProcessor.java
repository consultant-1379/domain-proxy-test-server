package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@ToString(of = {"parentElement", "elementName"})
public class ElementValueProcessor {
    private final String parentElement;
    @Getter(AccessLevel.PACKAGE)
    private final String elementName;
    @Getter
    private final Set<ElementValueHolder> elementValueHolders = new HashSet<>();
    private final List<ElementAssociation> nextProcessors = new ArrayList<>();

    public ElementValueProcessor addLinkTarget(final ElementValueProcessor next, final ValueAssociation association) {
        nextProcessors.add(new ElementAssociation(association, next));
        return next;
    }

    public void processElement(final ValueElement element, final FdnStack fdnStack) {
        final FdnSegment parent = fdnStack.last();
        if (parent != null && Objects.equals(parent.getElement(), parentElement) && element.getElement().equals(elementName)) {
            elementValueHolders.add(new ElementValueHolder(this, fdnStack.toString(), element.getValue()));
        } else {
            nextProcessors.forEach(next -> next.processElement(element, fdnStack));
        }
    }

    public List<ElementValueHolder> selectValues(final Predicate<ElementValueHolder> selector) {
        return elementValueHolders.stream().filter(selector).collect(Collectors.toList());
    }

    public List<ElementValueHolder> getAssociationsMatching(final ElementValueHolder elementValueHolder) {
        return nextProcessors.stream().flatMap(next -> next.getValuesAssociatedTo(elementValueHolder).stream()).collect(Collectors.toList());
    }
}
