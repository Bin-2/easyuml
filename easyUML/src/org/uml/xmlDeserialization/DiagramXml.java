package org.uml.xmlDeserialization;

import java.io.InputStream;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Reads diagrams without DTDs, external entities, or network access.
 */
public final class DiagramXml {

    private DiagramXml() {
    }

    public static Document read(InputStream input) throws IOException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            XMLReader reader = factory.newSAXParser().getXMLReader();
            return new SAXReader(reader).read(input);
        } catch (ParserConfigurationException | SAXException | DocumentException ex) {
            throw new IOException("Cannot read diagram XML: " + ex.getMessage(), ex);
        }
    }
}
