package com.ericsson.oss.services.domainproxy.test.server.xml;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.Reader;

public class XmlFactories {

    private static final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    private static final XMLInputFactory inputFactory = XMLInputFactory.newInstance();

    static {
        saxParserFactory.setNamespaceAware(true);
    }

    public static XMLReader newXmlReader() throws ParserConfigurationException, SAXException {
        return saxParserFactory.newSAXParser().getXMLReader();
    }

    public static XMLEventReader newXmlEventReader(final Reader reader) throws XMLStreamException {
        return inputFactory.createXMLEventReader(reader);
    }
}
