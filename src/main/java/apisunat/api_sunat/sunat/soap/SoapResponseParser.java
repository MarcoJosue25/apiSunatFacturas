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
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lee las respuestas SOAP de la SUNAT.
@Component
public class SoapResponseParser {

    private static final Pattern CODIGO_EN_FAULTCODE = Pattern.compile("\\.(\\d{4})$");
    private static final Pattern CODIGO_EN_FAULTSTRING = Pattern.compile("\\(codigo:\\s*(\\d{4})\\)");

    public RespuestaStatus leerGetStatus(String xml) {
        Document doc = leer(xml);
        lanzarSiEsFault(doc);

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

    // El ZIP con la constancia de recepción (CDR), ya decodificado del base64
    public byte[] leerSendBill(String xml) {
        Document doc = leer(xml);
        lanzarSiEsFault(doc);

        Element respuesta = primero(doc, "sendBillResponse");
        String base64 = respuesta == null ? null : texto(respuesta, "applicationResponse");
        if (base64 == null || base64.isEmpty()) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no trae la constancia (sendBillResponse/applicationResponse)");
        }
        try {
            // Se quitan los saltos de línea que a veces trae y luego se decodifica de forma estricta: el decodificador "mime"
            // ignora en silencio los caracteres que no son base64 y podría devolver una constancia vacía o a medias
            return Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no es un base64 válido", e);
        }
    }

    // El número de ticket con el que luego se consulta el resultado del resumen
    public String leerSendSummary(String xml) {
        Document doc = leer(xml);
        lanzarSiEsFault(doc);

        Element respuesta = primero(doc, "sendSummaryResponse");
        String ticket = respuesta == null ? null : texto(respuesta, "ticket");
        if (ticket == null || !ticket.matches("\\d{1,30}")) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no trae un ticket válido (sendSummaryResponse/ticket)");
        }
        return ticket;
    }

    public RespuestaStatusCdr leerGetStatusCdr(String xml) {
        Document doc = leer(xml);
        lanzarSiEsFault(doc);

        Element respuesta = primero(doc, "getStatusCdrResponse");
        Element status = respuesta == null ? null : primero(respuesta, "statusCdr");
        if (status == null) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no trae el estado (getStatusCdrResponse/statusCdr)");
        }
        String statusCode = texto(status, "statusCode");
        if (statusCode == null || statusCode.isBlank()) {
            throw new SunatRespuestaInvalidaException("La respuesta de la SUNAT no trae el statusCode");
        }
        String base64 = texto(status, "content");
        byte[] constancia = null;
        if (base64 != null && !base64.isEmpty()) {
            try {
                constancia = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            } catch (IllegalArgumentException e) {
                throw new SunatRespuestaInvalidaException("La constancia de la SUNAT no es un base64 válido", e);
            }
        }
        return RespuestaStatusCdr.builder().statusCode(statusCode.trim()).statusMessage(texto(status, "statusMessage"))
                .constancia(constancia).build();
    }

    private void lanzarSiEsFault(Document doc) {
        Element fault = primero(doc, "Fault");
        if (fault != null) {
            String faultCode = texto(fault, "faultcode");
            String faultString = texto(fault, "faultstring");
            throw new SunatFaultException(faultCode, faultString, sacarCodigo(faultCode, faultString));
        }
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
