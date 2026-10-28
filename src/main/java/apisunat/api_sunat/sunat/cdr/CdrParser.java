package apisunat.api_sunat.sunat.cdr;

import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.util.ZipUtil;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lee la constancia de recepción (CDR) que devuelve la SUNAT: un ZIP con un ApplicationResponse (UBL 2.0).
// No verifica la firma de la SUNAT: la constancia llegó por la conexión segura con la SUNAT y no se le exige validez legal aquí.
@Component
public class CdrParser {

    private static final String NS_CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final String NS_AR = "urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2";

    // Las notas vienen como "4031 - Debe indicar el nombre comercial"
    private static final Pattern NOTA = Pattern.compile("^(\\d{4})\\s*-\\s*(.*)$", Pattern.DOTALL);

    public CdrResultado leer(byte[] zip) {
        ZipUtil.Entrada xml;
        try {
            xml = ZipUtil.leerXmlUnico(zip);
        } catch (IllegalArgumentException e) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no se puede leer: " + e.getMessage(), e);
        }
        Document doc = parsear(xml.contenido());

        Element raiz = doc.getDocumentElement();
        if (!NS_AR.equals(raiz.getNamespaceURI()) || !"ApplicationResponse".equals(raiz.getLocalName())) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no es un ApplicationResponse");
        }
        Element respuesta = respuesta(raiz);
        String codigo = texto(respuesta, "ResponseCode");
        String referencia = texto(respuesta, "ReferenceID");
        if (codigo == null || referencia == null) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no trae el código ni el documento de la respuesta");
        }

        CdrResultado.CdrResultadoBuilder resultado = CdrResultado.builder()
                .idDocumento(referencia)
                .codigo(codigo)
                .descripcion(texto(respuesta, "Description"));
        for (Node n = raiz.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && NS_CBC.equals(e.getNamespaceURI()) && "Note".equals(e.getLocalName())) {
                String nota = limpiar(e.getTextContent());
                if (!nota.isEmpty()) {
                    resultado.observacion(observacion(nota));
                }
            }
        }
        return resultado.build();
    }

    private static CdrResultado.Observacion observacion(String nota) {
        Matcher m = NOTA.matcher(nota);
        return m.matches() ? new CdrResultado.Observacion(m.group(1), m.group(2).strip()) : new CdrResultado.Observacion(null, nota);
    }

    private static Element respuesta(Element raiz) {
        NodeList documentos = raiz.getElementsByTagNameNS(NS_CAC, "DocumentResponse");
        if (documentos.getLength() == 0) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no trae la respuesta al documento (DocumentResponse)");
        }
        NodeList respuestas = ((Element) documentos.item(0)).getElementsByTagNameNS(NS_CAC, "Response");
        if (respuestas.getLength() == 0) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no trae la respuesta al documento (Response)");
        }
        return (Element) respuestas.item(0);
    }

    // null si no existe o está vacío
    private static String texto(Element padre, String nombreLocal) {
        NodeList nodos = padre.getElementsByTagNameNS(NS_CBC, nombreLocal);
        if (nodos.getLength() == 0) {
            return null;
        }
        String texto = limpiar(nodos.item(0).getTextContent());
        return texto.isEmpty() ? null : texto;
    }

    // Las descripciones vienen partidas en varias líneas: se dejan con un solo espacio entre palabras
    private static String limpiar(String texto) {
        return texto.strip().replaceAll("\\s+", " ");
    }

    // No se permiten DOCTYPE: la constancia viene de afuera y no debe poder leer archivos ni inflar la memoria (XXE)
    private static Document parsear(byte[] xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no es un XML válido", e);
        }
    }
}
