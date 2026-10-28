package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.util.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SoapResponseParserTest {

    private final SoapResponseParser parser = new SoapResponseParser();

    private static String respuesta(String interior) {
        return "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>"
                + "<ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\">" + interior + "</ns2:getStatusResponse>"
                + "</S:Body></S:Envelope>";
    }

    // --- Respuestas reales capturadas del beta ---

    @Test
    void leeLaRespuestaRealDeUnTicketQueNoExiste() {
        RespuestaStatus r = parser.leerGetStatus(Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml"));
        assertThat(r.getStatusCode()).isEqualTo("0127");
        assertThat(r.getContent()).isEqualTo("El ticket no existe");
    }

    @Test
    void elErrorSoapRealDelBetaSeDetectaAunqueLlegueConHttp200() {
        String xml = Fixtures.texto("sunat/beta/fault-sin-seguridad.xml");
        assertThatThrownBy(() -> parser.leerGetStatus(xml))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> {
                    assertThat(e.getFaultCode()).isEqualTo("soap-env:Client.0200");
                    assertThat(e.getCodigo()).isEqualTo("0200");
                    assertThat(e.getFaultString()).contains("No se pudo procesar su solicitud");
                });
    }

    // --- Estados normales ---

    @Test
    void statusCodeCeroTraeElZipEnBase64() {
        String zip = Base64.getEncoder().encodeToString("PK-contenido-de-prueba".getBytes());
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><content>" + zip + "</content><statusCode>0</statusCode></status>"));
        assertThat(r.getStatusCode()).isEqualTo("0");
        assertThat(r.getContent()).isEqualTo(zip);
    }

    @Test
    void statusCode98EstaEnProcesoYPuedeNoTraerContent() {
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><statusCode>98</statusCode></status>"));
        assertThat(r.getStatusCode()).isEqualTo("98");
        assertThat(r.getContent()).isNull();
    }

    @Test
    void statusCode99TerminoConErroresYTraeElDetalle() {
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><content>detalle del error</content><statusCode>99</statusCode></status>"));
        assertThat(r.getStatusCode()).isEqualTo("99");
        assertThat(r.getContent()).isEqualTo("detalle del error");
    }

    @Test
    void unContentVacioEsVacioNoNulo() {
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><content/><statusCode>98</statusCode></status>"));
        assertThat(r.getContent()).isEmpty();
    }

    @Test
    void elOrdenDeLosElementosDelStatusNoImporta() {
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><statusCode>0127</statusCode><content>x</content></status>"));
        assertThat(r.getStatusCode()).isEqualTo("0127");
        assertThat(r.getContent()).isEqualTo("x");
    }

    @Test
    void ignoraLosEspaciosYSaltosDeLineaAlrededorDelStatusCode() {
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><content>x</content><statusCode>\n  0  \n</statusCode></status>"));
        assertThat(r.getStatusCode()).isEqualTo("0");
    }

    @Test
    void respetaLasTildesYLasEnies() {
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><content>El número de teléfono añadió ñandú €</content><statusCode>0151</statusCode></status>"));
        assertThat(r.getContent()).isEqualTo("El número de teléfono añadió ñandú €");
    }

    @Test
    void leeUnContentGrandeDeVariosMegas() {
        String grande = "A".repeat(4_000_000);
        RespuestaStatus r = parser.leerGetStatus(respuesta("<status><content>" + grande + "</content><statusCode>0</statusCode></status>"));
        assertThat(r.getContent()).hasSize(4_000_000);
    }

    @Test
    void funcionaConCualquierPrefijoDeNamespace() {
        String xml = "<a:Envelope xmlns:a=\"http://schemas.xmlsoap.org/soap/envelope/\"><a:Body>"
                + "<z:getStatusResponse xmlns:z=\"http://service.sunat.gob.pe\"><status><statusCode>98</statusCode></status></z:getStatusResponse>"
                + "</a:Body></a:Envelope>";
        assertThat(parser.leerGetStatus(xml).getStatusCode()).isEqualTo("98");
    }

    // --- Errores SOAP (Fault) ---

    @Test
    void faultConElCodigoEnElFaultcode() {
        String xml = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Client.0109</faultcode><faultstring>El servicio de autenticación no está disponible</faultstring>"
                + "</S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOfSatisfying(SunatFaultException.class, e -> {
            assertThat(e.getCodigo()).isEqualTo("0109");
            assertThat(e.getFaultString()).contains("autenticación");
        });
    }

    @Test
    void faultConElCodigoSoloEnElTextoDelMensaje() {
        String xml = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Server</faultcode><faultstring>Falló algo (codigo: 0100) y más</faultstring>"
                + "</S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatus(xml))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0100"));
    }

    @Test
    void faultSinNingunCodigoDejaElCodigoNulo() {
        String xml = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Server</faultcode><faultstring>Error raro</faultstring>"
                + "</S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOfSatisfying(SunatFaultException.class, e -> {
            assertThat(e.getCodigo()).isNull();
            assertThat(e.getFaultCode()).isEqualTo("S:Server");
        });
    }

    @Test
    void faultSinFaultstringNoRompeElParser() {
        String xml = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault><faultcode>S:Server</faultcode></S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOf(SunatFaultException.class);
    }

    @Test
    void faultVacioTambienSeDetecta() {
        String xml = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault/></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOf(SunatFaultException.class);
    }

    // --- Respuestas inválidas ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void unaRespuestaVaciaEsInvalida(String xml) {
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Bad Gateway",
            "<html><body><h1>502 Bad Gateway</h1></body></html>",
            "<S:Envelope><S:Body>",
            "{\"error\": \"no es xml\"}",
            "<a/><b/>"})
    void loQueNoEsXmlSoapValidoEsInvalido(String xml) {
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void unXmlBienFormadoPeroQueNoEsSoapEsInvalido() {
        assertThatThrownBy(() -> parser.leerGetStatus("<cualquier><cosa/></cualquier>"))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("estado");
    }

    @Test
    void sinCuerpoEsInvalido() {
        assertThatThrownBy(() -> parser.leerGetStatus("<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"/>"))
                .isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void conGetStatusResponseSinStatusEsInvalido() {
        assertThatThrownBy(() -> parser.leerGetStatus(respuesta("")))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("status");
    }

    @Test
    void sinStatusCodeEsInvalido() {
        assertThatThrownBy(() -> parser.leerGetStatus(respuesta("<status><content>algo</content></status>")))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("statusCode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<statusCode/>", "<statusCode>   </statusCode>"})
    void unStatusCodeVacioEsInvalido(String statusCode) {
        assertThatThrownBy(() -> parser.leerGetStatus(respuesta("<status>" + statusCode + "</status>")))
                .isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    // --- Seguridad: una respuesta maliciosa no debe leer archivos ni agotar la memoria ---

    @Test
    void noResuelveEntidadesExternasNiLeeArchivosDelServidor() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + respuesta("<status><content>&e;</content><statusCode>0</statusCode></status>");
        assertThatThrownBy(() -> parser.leerGetStatus(xml))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain("root:"));
    }

    @Test
    void rechazaLaExplosionDeEntidadesBillionLaughs() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">"
                + "<!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">]>"
                + respuesta("<status><content>&lol3;</content><statusCode>0</statusCode></status>");
        assertThatThrownBy(() -> parser.leerGetStatus(xml)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }
}
