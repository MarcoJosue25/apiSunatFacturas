package apisunat.api_sunat.util;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;

// Ayuda para los tests: leer un XML (sin resolver entidades externas) y consultarlo con XPath ignorando los prefijos
public final class XmlTest {

    private XmlTest() {
    }

    public static Document leer(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException("El XML no se pudo leer: " + e.getMessage(), e);
        }
    }

    // Ejemplo: texto(doc, "Username") busca //*[local-name()='Username']
    public static String texto(Document doc, String nombreLocal) {
        try {
            return XPathFactory.newInstance().newXPath()
                    .evaluate("//*[local-name()='" + nombreLocal + "']", doc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static int cantidad(Document doc, String nombreLocal) {
        return doc.getElementsByTagNameNS("*", nombreLocal).getLength();
    }
}
