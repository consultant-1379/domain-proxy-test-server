package com.ericsson.oss.services.domainproxy.test.server.xml.datareader;

import com.ericsson.oss.services.domainproxy.test.server.xml.XmlFactories;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
public class XmlDataReader {
    private static final Logger logger = LoggerFactory.getLogger(XmlDataReader.class);

    public static ElementValueProcessor newElement(final String parentElementName, final String elementName) {
        return new ElementValueProcessor(parentElementName, elementName);
    }

    private final List<ElementValueProcessor> elementProcessors;

    public void processXmlData(final String xmlData) throws XMLStreamException {
        final FdnStack fdnStack = new FdnStack();
        String currentElement = null;
        String currentStruct = null;
        boolean isIdElement = false;
        Set<String> notAnElement = new HashSet<>();

        XMLEventReader eventReader = XmlFactories.newXmlEventReader(new StringReader(xmlData));
        while (eventReader.hasNext()) {
            XMLEvent xmlEvent = eventReader.nextEvent();

            if (xmlEvent.getEventType() == XMLEvent.START_ELEMENT) {
                final StartElement startElemntEvent = (StartElement) xmlEvent;
                final String element = startElemntEvent.getName().getLocalPart();
                final Attribute structAttribute = startElemntEvent.getAttributeByName(new QName("struct"));
                if (currentStruct == null && structAttribute != null) {
                    currentStruct = element;
                }

                if (currentElement == null) {
                    currentElement = element;
                } else if (element.equalsIgnoreCase(currentElement + "id")) {
                    isIdElement = true;
                } else if (currentStruct != null) {
                    currentElement = element;
                } else {
                    if (structAttribute == null) {
                        logger.warn("Element missing id: {}", currentElement);
                        //fdnStack.addSegment(new FdnSegment(currentElement, "1"));
                        notAnElement.add(currentElement);
                    }
                    currentElement = element;
                }
            } else if (xmlEvent.getEventType() == XMLEvent.CHARACTERS && currentElement != null) {
                final Characters textEvent = (Characters) xmlEvent;
                if (!textEvent.isWhiteSpace()) {
                    final String value = textEvent.getData();
                    if (isIdElement) {
                        isIdElement = false;
                        fdnStack.addSegment(new FdnSegment(currentElement, value));
                    } else if (!fdnStack.isEmpty()){
                        final ValueElement valueElement = new ValueElement(currentElement, value);
                        elementProcessors.forEach(p -> p.processElement(valueElement, fdnStack));
                    }
                    final XMLEvent discard = eventReader.nextEvent();
                    currentElement = null;
                }
            } else if (xmlEvent.getEventType() == XMLEvent.END_ELEMENT) {
                final String elementName = ((EndElement) xmlEvent).getName().getLocalPart();
                if (currentStruct == null) {
                    if (!notAnElement.remove(elementName) && fdnStack.isLastElement(elementName)) {
                        fdnStack.popSegment();
                    }
                } else {
                    if (elementName.equals(currentStruct)) {
                        currentStruct = null;
                    }
                }
                currentElement = null;
            }
        }
    }

    public void walkValues(final BiConsumer<Boolean, ElementValueHolder> walker) {
        for (final ElementValueProcessor elementProcessor : elementProcessors) {
            elementProcessor.getElementValueHolders().forEach(elementValueHolder -> elementValueHolder
                    .valuesWalker(walker));
        }
    }

    public void mapHeadToLeafValue(final BiConsumer<ElementValueHolder, ElementValueHolder> headAndLeafConsumer) {
        for (final ElementValueProcessor elementProcessor : elementProcessors) {
            elementProcessor.getElementValueHolders().forEach(elementValueHolder -> elementValueHolder
                    .valuesWalker((isLeaf, value) -> {
                        if (isLeaf) {
                            headAndLeafConsumer.accept(elementValueHolder, value);
                        }
                    }));
        }
    }

    public static ReaderBuilder builder() {
        return new ReaderBuilder();
    }

    public static class ReaderBuilder {
        private List<ElementValueProcessor> elementValueProcessors = new ArrayList<>();

        public ElementReaderBuilder readElement(final String parentName, final String elementName) {
            final ElementReaderBuilder elementReaderBuilder = ElementReaderBuilder.fromName(parentName, elementName, this);
            elementValueProcessors.add(elementReaderBuilder.getElementValueProcessor());
            return elementReaderBuilder;
        }

        public XmlDataReader build() {
            return new XmlDataReader(elementValueProcessors);
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ElementReaderBuilder {
        @Getter
        private final ElementValueProcessor elementValueProcessor;
        private final ReaderBuilder readerBuilder;

        static ElementReaderBuilder fromName(final String parentName, final String elementName, final ReaderBuilder readerBuilder) {
            return new ElementReaderBuilder(XmlDataReader.newElement(parentName, elementName), readerBuilder);
        }

        static ElementReaderBuilder fromElement(final ElementValueProcessor elementValueProcessor, final ReaderBuilder readerBuilder) {
            return new ElementReaderBuilder(elementValueProcessor, readerBuilder);
        }

        public ElementReaderBuilder linkingToNewElement(final String parentName, final String elementName, final ValueAssociation association) {
            final ElementValueProcessor linkTarget = XmlDataReader.newElement(parentName, elementName);
            this.elementValueProcessor.addLinkTarget(linkTarget, association);
            return fromElement(linkTarget, readerBuilder);
        }

        public ElementReaderBuilder linkingToElement(final ElementReaderBuilder linkTarget, final ValueAssociation association) {
            this.elementValueProcessor.addLinkTarget(linkTarget.elementValueProcessor, association);
            return linkTarget;
        }

        public ElementReaderBuilder linkingToElement(final ElementValueProcessor linkTarget, final ValueAssociation association) {
            this.elementValueProcessor.addLinkTarget(linkTarget, association);
            return fromElement(linkTarget, readerBuilder);
        }

        public ElementReaderBuilder readElement(final String parentName, final String elementName) {
            return readerBuilder.readElement(parentName, elementName);
        }

        public XmlDataReader build() {
            return this.readerBuilder.build();
        }
    }

}
