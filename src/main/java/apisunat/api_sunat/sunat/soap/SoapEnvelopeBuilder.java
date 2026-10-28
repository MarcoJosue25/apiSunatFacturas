package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.regex.Pattern;

// Arma los mensajes SOAP que se mandan al billService de la SUNAT.
// La autenticación es WS-Security UsernameToken: el usuario es el RUC pegado al usuario SOL, y la clave es la clave SOL.
@Component
@RequiredArgsConstructor
public class SoapEnvelopeBuilder {

    // El nombre del ZIP: letras, números y guiones, terminado en .zip (por ejemplo 20100066603-01-F001-1.zip)
    private static final Pattern NOMBRE_ZIP = Pattern.compile("[A-Za-z0-9-]{1,100}\\.zip");
    private static final Pattern RUC = Pattern.compile("\\d{11}");
    private static final Pattern TIPO_COMPROBANTE = Pattern.compile("0[178]");
    private static final Pattern SERIE = Pattern.compile("[A-Z0-9]{4}");
    private static final Pattern NUMERO = Pattern.compile("[1-9]\\d{0,7}");

    private final SunatProperties propiedades;

    // Envía un comprobante (factura o nota) dentro de un ZIP, en base64. La SUNAT contesta en el mismo momento.
    public String sendBill(String nombreZip, byte[] zip) {
        exigirNombreZip(nombreZip);
        exigirContenido(zip);
        return envolver("<ser:sendBill><fileName>" + nombreZip + "</fileName><contentFile>"
                + Base64.getEncoder().encodeToString(zip) + "</contentFile></ser:sendBill>");
    }

    // Pregunta a la SUNAT por la constancia de un comprobante ya enviado (servicio billConsultService, solo en producción).
    // El número va sin ceros a la izquierda, como en el manual (F022 / 7782).
    public String getStatusCdr(String rucEmisor, String tipoComprobante, String serie, String numero) {
        if (rucEmisor == null || !RUC.matcher(rucEmisor).matches()) {
            throw new IllegalArgumentException("El RUC del comprobante debe tener 11 dígitos");
        }
        if (tipoComprobante == null || !TIPO_COMPROBANTE.matcher(tipoComprobante).matches()) {
            throw new IllegalArgumentException("El tipo de comprobante debe ser 01, 07 u 08");
        }
        if (serie == null || !SERIE.matcher(serie).matches()) {
            throw new IllegalArgumentException("La serie debe tener 4 caracteres: mayúsculas y números");
        }
        if (numero == null || !NUMERO.matcher(numero).matches()) {
            throw new IllegalArgumentException("El número debe ser de 1 a 8 dígitos y sin ceros a la izquierda");
        }
        return envolver("<ser:getStatusCdr><rucComprobante>" + rucEmisor + "</rucComprobante><tipoComprobante>" + tipoComprobante
                + "</tipoComprobante><serieComprobante>" + serie + "</serieComprobante><numeroComprobante>" + numero
                + "</numeroComprobante></ser:getStatusCdr>");
    }

    public String getStatus(String ticket) {
        exigirTexto("El ticket", ticket);
        return envolver("<ser:getStatus><ticket>" + escapar(ticket) + "</ticket></ser:getStatus>");
    }

    private String envolver(String cuerpo) {
        String usuario = propiedades.getRuc() + propiedades.getUsuario();
        return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>" + escapar(usuario) + "</wsse:Username>"
                + "<wsse:Password>" + escapar(propiedades.getClave()) + "</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body>" + cuerpo + "</soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    private static void exigirNombreZip(String nombre) {
        if (nombre == null || !NOMBRE_ZIP.matcher(nombre).matches()) {
            throw new IllegalArgumentException("El nombre del ZIP debe llevar solo letras, números y guiones, y terminar en .zip");
        }
    }

    private static void exigirContenido(byte[] zip) {
        if (zip == null || zip.length == 0) {
            throw new IllegalArgumentException("El ZIP no puede estar vacío");
        }
    }

    private static void exigirTexto(String nombre, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede estar vacío");
        }
    }

    // Un XML 1.0 no admite caracteres de control (salvo tab, salto de línea y retorno): no se pueden escapar, se rechazan
    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                throw new IllegalArgumentException("El texto tiene un carácter de control que no es válido en XML");
            }
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
