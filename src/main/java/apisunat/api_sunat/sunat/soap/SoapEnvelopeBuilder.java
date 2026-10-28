package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Arma los mensajes SOAP que se mandan al billService de la SUNAT.
// La autenticación es WS-Security UsernameToken: el usuario es el RUC pegado al usuario SOL, y la clave es la clave SOL.
@Component
@RequiredArgsConstructor
public class SoapEnvelopeBuilder {

    private final SunatProperties propiedades;

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
