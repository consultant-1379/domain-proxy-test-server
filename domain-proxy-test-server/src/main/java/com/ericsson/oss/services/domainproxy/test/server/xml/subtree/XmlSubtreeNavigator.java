package com.ericsson.oss.services.domainproxy.test.server.xml.subtree;

import lombok.Getter;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class XmlSubtreeNavigator {
    private static final XMLInputFactory INPUT_FACTORY = XMLInputFactory.newInstance();
    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newInstance();
    private static final XMLEventFactory EVENT_FACTORY = XMLEventFactory.newInstance();

    private final String xml;
    private final XMLEventReader eventReader;
    @Getter
    private int currentLevel;
    private XMLEvent currentEvent;
    private Deque<StartElement> currentStack = new ArrayDeque<>();
    private StartElement matchingStartElement;
    private String lastElmentText;

    public XmlSubtreeNavigator(final String xml) throws XMLStreamException {
        eventReader = INPUT_FACTORY.createXMLEventReader(new StringReader(xml));
        this.xml = xml;
    }

    public boolean findInto(final String element) throws XMLStreamException {
        if (stepIn()) {
            while (!isCurrentElmentName(element)) {
                if (!nextSibling()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public boolean find(final String element) throws XMLStreamException {
        while (!isCurrentElmentName(element)) {
            if (!nextSibling()) {
                return false;
            }
        }
        return true;
    }

    public Optional<String> getLastElementText() {
        return Optional.ofNullable(lastElmentText);
    }

    public boolean lastElmentTextMatches(final String value) {
        if (lastElmentText == value) {
            return true;
        }
        return lastElmentText != null && lastElmentText.equalsIgnoreCase(value);
    }

    public String currentElementName() {
        if (currentStack.isEmpty()) {
            return null;
        }
        return currentStack.peek().getName().getLocalPart();
    }

    public StartElement getCurrentElement() {
        if (currentStack.isEmpty()) {
            return null;
        }
        return currentStack.peek();
    }

    public List<StartElement> getCurrentPath() {
        final ArrayList<StartElement> pathElements = new ArrayList<>(this.currentStack);
        Collections.reverse(pathElements);
        return pathElements;
    }

    public String currentElementBody(boolean includeParentTree) throws XMLStreamException {
        if (currentEvent == null) {
            return "";
        }

        if (currentEvent.isStartElement()) {
            final StringWriter sw = new StringWriter();
            final XMLEventWriter eventWriter = OUTPUT_FACTORY.createXMLEventWriter(sw);

            if (includeParentTree) {
                for (StartElement startElement : currentStack) {
                    eventWriter.add(startElement);
                }
            } else {
                eventWriter.add(currentEvent);
            }

            final int startLevel = this.currentLevel;
            while (moveNextElement() && currentLevel >= startLevel) {
                if (currentEvent.isEndElement()) {
                    final Optional<String> elementText = getLastElementText();
                    if (elementText.isPresent()) {
                        eventWriter.add(EVENT_FACTORY.createCharacters(elementText.get()));
                    }
                }
                eventWriter.add(currentEvent);
            }

            if (currentEvent != null && currentEvent.isEndElement()) {
                final Optional<String> elementText = getLastElementText();
                if (elementText.isPresent()) {
                    eventWriter.add(EVENT_FACTORY.createCharacters(elementText.get()));
                }
                eventWriter.add(currentEvent);
            }
            if (includeParentTree) {
                final List<StartElement> startElements = new ArrayList<>(currentStack);
                Collections.reverse(startElements);
                for (final StartElement startElement : startElements) {
                    final EndElement endElement = EVENT_FACTORY.createEndElement(startElement.getName(), startElement.getNamespaces());
                    eventWriter.add(endElement);
                }
            }
            eventWriter.close();
            return sw.toString();
        } else {
            final XmlSubtreeNavigator navigator = new XmlSubtreeNavigator(xml);
            final List<StartElement> currentPath = getCurrentPath();
            currentPath.add(matchingStartElement);
            for (final StartElement startElement : currentPath) {
                if (navigator.findInto(startElement.getName().getLocalPart())) {
                    while (!isSameLocation(navigator.currentEvent, startElement)) {
                        if (!navigator.nextSibling()) {
                            throw new IllegalStateException("Missing element: " + startElement);
                        }
                    }
                }
            }
            return navigator.currentElementBody(includeParentTree);
        }
    }

    private boolean isSameLocation(final XMLEvent eventA, final XMLEvent eventB) {
        return Objects.equals(eventA.getLocation().getLineNumber(), eventB.getLocation().getLineNumber()) &&
                Objects.equals(eventA.getLocation().getColumnNumber(), eventB.getLocation().getColumnNumber()) &&
                Objects.equals(eventA.getLocation().getCharacterOffset(), eventB.getLocation().getCharacterOffset());
    }

    public boolean nextSibling() throws XMLStreamException {
        final int siblingLevel = currentEvent.isStartElement() ? this.currentLevel : this.currentLevel + 1;
        while (moveNextElement()) {
            if (this.currentLevel == siblingLevel && isElementStart()) {
                return true;
            }
            if (isElementStart()) {
                if (this.currentLevel < siblingLevel) {
                    return false;
                }
            } else if (this.currentLevel < siblingLevel - 1) {
                return false;
            }
        }
        return false;
    }

    public boolean stepIn() throws XMLStreamException {
        final int startLevel = this.currentLevel;
        final int nextLevel = startLevel + 1;
        if (moveNextElement()) {
            if (currentEvent.isStartElement()) {
                if (currentLevel <= startLevel) {
                    return false;
                }
                return currentLevel == nextLevel;
            }
        }
        return false;
    }

    public boolean stepOut() throws XMLStreamException {
        final int previousLevel = this.currentLevel - 1;
        while (moveNextElement()) {
            if (currentLevel == previousLevel) {
                return true;
            }
        }
        return false;
    }

    public boolean hasMoreElements() {
        return this.eventReader.hasNext();
    }

    public boolean isCurrentElmentName(final String elementName) {
        if (!currentStack.isEmpty()) {
            return currentStack.peek().getName().getLocalPart().equalsIgnoreCase(elementName);
        }
        return false;
    }

    public boolean isElementStart() {
        return currentEvent != null && currentEvent.isStartElement();
    }

    public boolean isCurrentLevel(final int level) {
        return currentLevel == level;
    }

    public boolean moveNextStartElement() throws XMLStreamException {
        while (moveNextElement()) {
            if (currentEvent.getEventType() == XMLEvent.START_ELEMENT) {
                return true;
            }
        }
        return false;
    }

    public boolean moveNextEndElement() throws XMLStreamException {
        while (moveNextElement()) {
            if (currentEvent.getEventType() == XMLEvent.END_ELEMENT) {
                return true;
            }
        }
        return false;
    }

    private boolean moveNextElement() throws XMLStreamException {
        String textBuffer = null;
        while (eventReader.hasNext()) {
            currentEvent = eventReader.nextEvent();
            if (currentEvent.getEventType() == XMLEvent.START_ELEMENT) {
                currentLevel++;
                currentStack.push((StartElement) currentEvent);
                lastElmentText = null;
                matchingStartElement = null;
                return true;
            } else if (currentEvent.getEventType() == XMLEvent.END_ELEMENT) {
                currentLevel--;
                matchingStartElement = currentStack.pop();
                lastElmentText = textBuffer;
                return true;
            } else if (currentEvent.getEventType() == XMLEvent.CHARACTERS) {
                final Characters textEvent = (Characters) currentEvent;
                if (!textEvent.isWhiteSpace()) {
                    textBuffer = textEvent.getData();
                }
            }
        }
        return false;
    }
}
