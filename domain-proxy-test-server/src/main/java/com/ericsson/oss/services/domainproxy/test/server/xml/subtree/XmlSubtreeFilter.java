package com.ericsson.oss.services.domainproxy.test.server.xml.subtree;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simple and limited XmlSubtreeFilter
 */
@RequiredArgsConstructor
public class XmlSubtreeFilter {
    @Getter
    private final String filterText;

    public String filter(final String xmlData) throws XMLStreamException {
        if (filterText == null) {
            return null;
        }
        final StringBuilder subtree = new StringBuilder();
        final XmlSubtreeNavigator filterNav = new XmlSubtreeNavigator(filterText);

        final List<PathElementMatcher> matchers = buildMatcher(filterNav);

        for (final PathElementMatcher matcher : matchers) {
            final XmlSubtreeNavigator dataNav = new XmlSubtreeNavigator(xmlData);
            matcher.navigate(dataNav);
            matcher.getElements().stream()
                    .map(this::generateXml)
                    .filter(content -> !content.trim().isEmpty())
                    .forEach(subtree::append);
        }

        return subtree.toString();
    }

    private String generateXml(final ElementRef elementRef) {
        final StringBuilder tree = new StringBuilder();
        generateXml(elementRef, tree);
        return tree.toString();
    }

    private void generateXml(final ElementRef elementRef, final StringBuilder tree) {
        if (elementRef.isValidDownStream()) {
            if (elementRef.isLeaf()) {
                tree.append(elementRef.getElementBody());
            } else {
                final String elementName = elementRef.getElement().getName().getLocalPart();
                tree.append("<").append(elementName).append(">");
                elementRef.getPathElementMatcher().getElementCriteriaList().forEach(criteria -> {
                    tree.append("<").append(criteria.getElementName()).append(">");
                    tree.append(criteria.requiredValue);
                    tree.append("</").append(criteria.getElementName()).append(">");
                });
                elementRef.getNextElements().forEach(next -> generateXml(next, tree));
                tree.append("</").append(elementName).append(">");
            }
        }
    }

    public boolean hasFilter() throws XMLStreamException {
        if (filterText == null || filterText.trim().isEmpty()) {
            return false;
        }

        final XmlSubtreeNavigator navigator = new XmlSubtreeNavigator(filterText);
        return navigator.stepIn() && navigator.stepIn();
    }


    private List<PathElementMatcher> buildMatcher(final XmlSubtreeNavigator filterNav) throws XMLStreamException {
        return buildMatcher(filterNav, null);
    }

    private List<PathElementMatcher> buildMatcher(final XmlSubtreeNavigator filterNav, final PathElementMatcher parentMatcher) throws XMLStreamException {
        final List<PathElementMatcher> matchers = new ArrayList<>();
        PathElementMatcher pathElementMatcher = null;
        if (filterNav.getCurrentLevel() > 0 || filterNav.moveNextStartElement()) {
            do {
                if (pathElementMatcher != null) {
                    matchers.add(pathElementMatcher);
                    if (parentMatcher != null) {
                        parentMatcher.getSubMatchers().add(pathElementMatcher);
                    }
                }
                pathElementMatcher = new PathElementMatcher(filterNav.currentElementName(), parentMatcher);
                if (filterNav.stepIn()) {
                    buildMatcher(filterNav, pathElementMatcher);
                } else if (filterNav.getLastElementText().isPresent()) {
                    if (parentMatcher != null) {
                        final ElementCriteria elementCriteria =
                                new ElementCriteria(pathElementMatcher.getElementName(), filterNav.getLastElementText().get());
                        parentMatcher.getElementCriteriaList().add(elementCriteria);
                        pathElementMatcher = null;
                    }
                }
            } while (filterNav.nextSibling());

            if (pathElementMatcher != null) {
                matchers.add(pathElementMatcher);
                if (parentMatcher != null) {
                    parentMatcher.getSubMatchers().add(pathElementMatcher);
                }
            }
        }
        return matchers;
    }

    @Data
    private static class PathElementMatcher {
        private final String elementName;
        private final PathElementMatcher parent;
        private final List<PathElementMatcher> subMatchers = new ArrayList<>();
        private final List<ElementCriteria> elementCriteriaList = new ArrayList<>();
        private final List<ElementRef> elements = new ArrayList<>();
        private ElementRef currentElement = null;

        List<PathElementMatcher> getLeafMatchers() {
            if (isLeaf()) {
                return Collections.singletonList(this);
            }
            return subMatchers.stream().flatMap(sub -> sub.getLeafMatchers().stream()).collect(Collectors.toList());
        }

        void navigate(final XmlSubtreeNavigator navigator) throws XMLStreamException {
            final int matcherLevel = getMatcherLevel();
            while (navigator.getCurrentLevel() < matcherLevel) {
                if (!navigator.moveNextStartElement()) {
                    return;
                }
            }

            do {
                if (navigator.isCurrentElmentName(elementName)) {
                    setCurrentElement(navigator.getCurrentElement());
                    if (!subMatchers.isEmpty() || !elementCriteriaList.isEmpty()) {
                        if (navigator.stepIn()) {
                            do {
                                final Optional<PathElementMatcher> subMatcher = subMatchers.stream()
                                        .filter(matcher -> navigator.isCurrentElmentName(matcher.getElementName()))
                                        .findAny();
                                if (subMatcher.isPresent()) {
                                    subMatcher.get().navigate(navigator);
                                } else {
                                    elementCriteriaList.forEach(criteria -> {
                                        try {
                                            criteria.verify(navigator, getCurrentElement());
                                        } catch (XMLStreamException e) {
                                            e.printStackTrace();
                                        }
                                    });
                                }
                            } while (navigator.getCurrentLevel() >= matcherLevel && navigator.nextSibling());
                        }
                    }
                    if (isLeaf()) {
                        currentElement.setElementBody(navigator.currentElementBody(false));
                        break;
                    }
                }
            } while (navigator.nextSibling());
            addCurrentToElementsList();
        }

        int getMatcherLevel() {
            if (parent == null) {
                return 1;
            } else {
                return parent.getMatcherLevel() + 1;
            }
        }

        private void setCurrentElement(final StartElement element) {
            addCurrentToElementsList();
            currentElement = new ElementRef(this, element, Optional.ofNullable(getParent()).map(PathElementMatcher::getCurrentElement).orElse(null));
            if (getParent() != null) {
                getParent().getCurrentElement().addNextElementRef(currentElement);
            }
        }

        private void addCurrentToElementsList() {
            if (currentElement != null) {
                elements.add(currentElement);
            }
        }

        private boolean isLeaf() {
            return getSubMatchers().isEmpty();
        }

        private boolean isRoot() {
            return getParent() == null;
        }
    }

    @Data
    private static class ElementCriteria {
        private final String elementName;
        private final String requiredValue;

        public void verify(final XmlSubtreeNavigator navigator, final ElementRef elementRef) throws XMLStreamException {
            if (navigator.isCurrentElmentName(elementName) && navigator.isElementStart()) {
                if (navigator.stepOut() && navigator.lastElmentTextMatches(requiredValue)) {
                    elementRef.updateCriteriaResult(this, Boolean.TRUE);
                } else {
                    elementRef.updateCriteriaResult(this, Boolean.FALSE);
                }
            }
        }
    }

    @Data
    private static class ElementRef {
        private final PathElementMatcher pathElementMatcher;
        private final StartElement element;
        private final ElementRef parentElement;
        private final List<ElementRef> nextElements = new ArrayList<>();
        private final Map<ElementCriteria, Boolean> criteriaResults = new HashMap<>();
        private String elementBody;

        void addNextElementRef(final ElementRef next) {
            nextElements.add(next);
        }

        void updateCriteriaResult(final ElementCriteria criteria, final Boolean result) {
            if (result != null) {
                criteriaResults.put(criteria, result);
            }
        }

        List<StartElement> getElementPath() {
            List<StartElement> elementPath = null;
            if (parentElement == null) {
                elementPath = new ArrayList<>();
            } else {
                elementPath = parentElement.getElementPath();
            }
            elementPath.add(element);
            return elementPath;
        }

        boolean elementMatchesCriteria() {
            final List<ElementCriteria> elementCriteriaList = getPathElementMatcher().getElementCriteriaList();
            if (elementCriteriaList.isEmpty()) {
                return true;
            }
            if (criteriaResults.size() != elementCriteriaList.size()) {
                return false;
            }
            return criteriaResults.values().stream().allMatch(Boolean.TRUE::equals);
        }

        boolean pathMatchesCriteria() {
            return isValidUpStream() && isValidDownStream();
        }

        boolean isLeaf() {
            return nextElements.isEmpty();
        }

        private boolean isValidUpStream() {
            return elementMatchesCriteria() && (parentElement == null || parentElement.isValidUpStream());
        }

        private boolean isValidDownStream() {
            return elementMatchesCriteria() && (nextElements.isEmpty() || nextElements.stream().anyMatch(ElementRef::isValidDownStream));
        }
    }

    public static void main(String[] args) throws XMLStreamException {
        String xmlData = "<top xmlns=\"http://example.com/schema/1.2/config\">\n" +
                "<users>\n" +
                " <user>\n" +
                "   <name>root</name>\n" +
                "   <type>superuser</type>\n" +
                "   <full-name>Charlie Root</full-name>\n" +
                "   <company-info>\n" +
                "\t <dept>1</dept>\n" +
                "\t <id>1</id>\n" +
                "   </company-info>\n" +
                " </user>\n" +
                " <user>\n" +
                "   <name>fred</name>\n" +
                "   <type>admin</type>\n" +
                "   <full-name>Fred Flintstone</full-name>\n" +
                "   <company-info>\n" +
                "\t <dept>2</dept>\n" +
                "\t <id>2</id>\n" +
                "   </company-info>\n" +
                " </user>\n" +
                " <user>\n" +
                "   <name>barney</name>\n" +
                "   <type>admin</type>\n" +
                "   <full-name>Barney Rubble</full-name>\n" +
                "   <company-info>\n" +
                "\t <dept>2</dept>\n" +
                "\t <id>3</id>\n" +
                "   </company-info>\n" +
                " </user>\n" +
                "</users>\n" +
                "</top>\n";

        String filter1 = "<top xmlns=\"http://example.com/schema/1.2/config\">\n" +
                " <users>\n" +
                "   <user/>\n" +
                " </users>\n" +
                "</top>\n";

        XmlSubtreeFilter xsf1 = new XmlSubtreeFilter(filter1);
        System.out.println("filter1: " + xsf1.filter(xmlData));

        String filter2 = "<top xmlns=\"http://example.com/schema/1.2/config\">\n" +
                " <users>\n" +
                "   <user>\n" +
                "   <name>barney</name>\n" +
                "   </user>\n" +
                " </users>\n" +
                "</top>\n";

        XmlSubtreeFilter xsf2 = new XmlSubtreeFilter(filter2);
        System.out.println("filter2: " + xsf2.filter(xmlData));


        String filter3 = "<top xmlns=\"http://example.com/schema/1.2/config\">\n" +
                " <users>\n" +
                "   <user>\n" +
                "   <name>barney</name>\n" +
                "   <company-info></company-info>\n" +
                "   </user>\n" +
                " </users>\n" +
                "</top>\n";

        XmlSubtreeFilter xsf3 = new XmlSubtreeFilter(filter3);
        System.out.println("filter3: " + xsf3.filter(xmlData));
    }
}
