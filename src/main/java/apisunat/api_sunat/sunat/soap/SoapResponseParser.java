package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lee las respuestas SOAP de la SUNAT.
@Component
public class SoapResponseParser {

    private static final Pattern CODIGO_EN_FAULTCODE = Pattern.compile("\\.(\\d{4})$");
    private static final Pattern CODIGO_EN_FAULTSTRING = Pattern.compile("\\(codigo:\\s*(\\d{4})\\)");

    public RespuestaStatus leerGetStatus(String xml) {
        Document doc = leer(xml);

        Element fault = primero(doc, "Fault");
        if (fault != null) {
            String faultCode = texto(fault, "faultcode");
            String faultString = texto(fault, "faultstring");
            throw new SunatFaultException(faultCode, faultString, sacarCodigo(faultCode, faultString));
        }

        Element respuesta = primero(doc, "getStatusResponse");
        Element status = respuesta == null ? null : primero(respuesta, "status");
        if (status == null) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no trae el estado (getStatusResponse/status)");
        }
        String statusCode = texto(status, "statusCode");
        if (statusCode == null || statusCode.isBlank()) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no trae el statusCode");
        }
        return RespuestaStatus.builder()
                .statusCode(statusCode.trim())
                .content(texto(status, "content"))
                .build();
    }

    // No se permiten DOCTYPE: así una respuesta maliciosa no puede leer archivos ni inflar la memoria (XXE, billion laughs)
    private Document leer(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT está vacía");
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no es un XML válido", e);
        }
    }

    private static Element primero(Node padre, String nombreLocal) {
        Node n = padre instanceof Document d
                ? d.getElementsByTagNameNS("*", nombreLocal).item(0)
                : ((Element) padre).getElementsByTagNameNS("*", nombreLocal).item(0);
        return (Element) n;
    }

    // null si el elemento no existe; "" si existe pero está vacío
    private static String texto(Element padre, String nombreLocal) {
        Element e = primero(padre, nombreLocal);
        return e == null ? null : e.getTextContent().trim();
    }

    private static String sacarCodigo(String faultCode, String faultString) {
        if (faultCode != null) {
            Matcher m = CODIGO_EN_FAULTCODE.matcher(faultCode);
            if (m.find()) {
                return m.group(1);
            }
        }
        if (faultString != null) {
            Matcher m = CODIGO_EN_FAULTSTRING.matcher(faultString);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }
}
