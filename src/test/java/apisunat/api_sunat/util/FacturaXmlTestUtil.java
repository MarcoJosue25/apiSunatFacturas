package apisunat.api_sunat.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// Ayudas para probar el XML de la factura: leerlo, consultarlo con XPath, ponerle una firma de relleno y compararlo sin importar
// los espacios ni el orden de los atributos.
public final class FacturaXmlTestUtil {

    private static final Map<String, String> ESPACIOS = Map.of(
            "inv", "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "cac", "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
            "cbc", "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
            "ext", "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2",
            "ds", "http://www.w3.org/2000/09/xmldsig#");

    private FacturaXmlTestUtil() {
    }

    public static Document leer(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalStateException("El XML no se pudo leer: " + e.getMessage(), e);
        }
    }

    public static Document leer(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static XPath xpath() {
        XPath xp = XPathFactory.newInstance().newXPath();
        xp.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefijo) {
                return ESPACIOS.getOrDefault(prefijo, XMLConstants.NULL_NS_URI);
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return List.<String>of().iterator();
            }
        });
        return xp;
    }

    // El texto del primer nodo que encuentre, o null si no existe. Prefijos: inv, cac, cbc, ext, ds
    public static String valor(Document doc, String expresion) {
        try {
            Node n = (Node) xpath().evaluate(expresion, doc, XPathConstants.NODE);
            return n == null ? null : n.getTextContent();
        } catch (Exception e) {
            throw new IllegalStateException(expresion, e);
        }
    }

    public static List<String> valores(Document doc, String expresion) {
        try {
            NodeList nodos = (NodeList) xpath().evaluate(expresion, doc, XPathConstants.NODESET);
            List<String> lista = new ArrayList<>();
            for (int i = 0; i < nodos.getLength(); i++) {
                lista.add(nodos.item(i).getTextContent());
            }
            return lista;
        } catch (Exception e) {
            throw new IllegalStateException(expresion, e);
        }
    }

    public static int cantidad(Document doc, String expresion) {
        try {
            return ((Number) xpath().evaluate("count(" + expresion + ")", doc, XPathConstants.NUMBER)).intValue();
        } catch (Exception e) {
            throw new IllegalStateException(expresion, e);
        }
    }

    // Le pone al documento la firma de relleno del archivo de referencia, para poder validarlo contra el XSD antes de tener la firma real
    public static byte[] conFirmaDeRelleno(byte[] xml) {
        Document doc = leer(xml);
        Document referencia = leer(Fixtures.texto("xml/factura-minima-valida.xml"));
        try {
            Node firma = (Node) xpath().evaluate("//ds:Signature", referencia, XPathConstants.NODE);
            Node contenido = (Node) xpath().evaluate("//ext:ExtensionContent", doc, XPathConstants.NODE);
            contenido.appendChild(doc.importNode(firma, true));
            return escribir(doc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] escribir(Document doc) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "ISO-8859-1");
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        t.transform(new DOMSource(doc), new StreamResult(salida));
        return salida.toByteArray();
    }

    // Una forma comparable del documento: sin espacios entre elementos, con los atributos ordenados y sin la firma.
    public static String normalizar(Document doc) {
        StringBuilder sb = new StringBuilder();
        normalizar(doc.getDocumentElement(), sb);
        return sb.toString();
    }

    private static void normalizar(Node nodo, StringBuilder sb) {
        if (nodo.getNodeType() == Node.TEXT_NODE || nodo.getNodeType() == Node.CDATA_SECTION_NODE) {
            String t = nodo.getTextContent().strip();
            if (!t.isEmpty()) {
                sb.append(t);
            }
            return;
        }
        if (nodo.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element e = (Element) nodo;
        sb.append('<').append(e.getNamespaceURI()).append('}').append(e.getLocalName());
        Map<String, String> atributos = new TreeMap<>();
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) {
                atributos.put(a.getNodeName(), a.getNodeValue());
            }
        }
        atributos.forEach((k, v) -> sb.append(' ').append(k).append("=\"").append(v).append('"'));
        sb.append('>');
        // la firma de relleno no se compara: lo que importa es lo que arma el builder
        if (!"ExtensionContent".equals(e.getLocalName())) {
            NodeList hijos = e.getChildNodes();
            for (int i = 0; i < hijos.getLength(); i++) {
                normalizar(hijos.item(i), sb);
            }
        }
        sb.append("</").append(e.getLocalName()).append('>');
    }

    public static String comoLatin1(byte[] xml) {
        return new String(xml, StandardCharsets.ISO_8859_1);
    }
}
