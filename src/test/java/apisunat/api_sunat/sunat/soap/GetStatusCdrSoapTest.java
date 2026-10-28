package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;

import java.util.Base64;

import static apisunat.api_sunat.util.XmlTest.leer;
import static apisunat.api_sunat.util.XmlTest.texto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// getStatusCdr: preguntar a la SUNAT por la constancia de una factura (billConsultService). Estructura sacada del manual del programador.
class GetStatusCdrSoapTest {

    private static final String URL_BETA = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static final String URL_CONSULTA = "https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService";
    private static final byte[] ZIP = {'P', 'K', 1, 2, 3, (byte) 250};

    private static SunatProperties propiedades() {
        SunatProperties p = new SunatProperties();
        p.setUrl(URL_BETA);
        p.setRuc("20100066603");
        p.setUsuario("MODDATOS");
        p.setClave("MODDATOS");
        p.setConexionTimeoutMs(5000);
        p.setLecturaTimeoutMs(30000);
        return p;
    }

    private static String respuesta(String codigo, String mensaje, String contenido) {
        return "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>"
                + "<ns0:getStatusCdrResponse xmlns:ns0=\"http://service.sunat.gob.pe\"><statusCdr>"
                + "<statusCode>" + codigo + "</statusCode>"
                + (contenido == null ? "" : "<content>" + contenido + "</content>")
                + "<statusMessage>" + mensaje + "</statusMessage>"
                + "</statusCdr></ns0:getStatusCdrResponse></S:Body></S:Envelope>";
    }

    // --- El sobre ---

    private final SoapEnvelopeBuilder sobres = new SoapEnvelopeBuilder(propiedades());

    @Test
    void elSobreTieneLaEstructuraDelManual() {
        String esperado = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>20100066603MODDATOS</wsse:Username><wsse:Password>MODDATOS</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body><ser:getStatusCdr><rucComprobante>20100066603</rucComprobante><tipoComprobante>01</tipoComprobante>"
                + "<serieComprobante>F001</serieComprobante><numeroComprobante>7782</numeroComprobante></ser:getStatusCdr></soapenv:Body>"
                + "</soapenv:Envelope>";
        assertThat(sobres.getStatusCdr("20100066603", "01", "F001", "7782")).isEqualTo(esperado);
    }

    @Test
    void elSobreEsXmlBienFormadoConLosCuatroDatos() {
        Document doc = leer(sobres.getStatusCdr("20100066603", "01", "FA01", "99999999"));
        assertThat(texto(doc, "rucComprobante")).isEqualTo("20100066603");
        assertThat(texto(doc, "tipoComprobante")).isEqualTo("01");
        assertThat(texto(doc, "serieComprobante")).isEqualTo("FA01");
        assertThat(texto(doc, "numeroComprobante")).isEqualTo("99999999");
        assertThat(texto(doc, "Username")).isEqualTo("20100066603MODDATOS");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2010006660", "201000666033", "2010006660a", " 20100066603", "20100066603\n"})
    void unRucInvalidoSeRechaza(String ruc) {
        assertThatThrownBy(() -> sobres.getStatusCdr(ruc, "01", "F001", "1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("RUC");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "03", "09", "001", "ab", "0 1"})
    void unTipoDeComprobanteInvalidoSeRechaza(String tipo) {
        assertThatThrownBy(() -> sobres.getStatusCdr("20100066603", tipo, "F001", "1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tipo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "07", "08"})
    void losTiposDeComprobanteQueSePuedenConsultarSon01_07_y_08(String tipo) {
        assertThat(sobres.getStatusCdr("20100066603", tipo, "F001", "1")).contains("<tipoComprobante>" + tipo + "</tipoComprobante>");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"F00", "F0011", "f001", "F 01", "F<01", "FÑ01"})
    void unaSerieInvalidaSeRechaza(String serie) {
        assertThatThrownBy(() -> sobres.getStatusCdr("20100066603", "01", serie, "1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("serie");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "01", "00000001", "123456789", "-1", "1.5", "abc", " 1", "1\n", "1</numeroComprobante>"})
    void unNumeroInvalidoSeRechazaYNuncaVaAlXml(String numero) {
        assertThatThrownBy(() -> sobres.getStatusCdr("20100066603", "01", "F001", numero)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("número");
    }

    // --- La respuesta ---

    private final SoapResponseParser parser = new SoapResponseParser();

    @Test
    void leeUnComprobanteAceptadoConSuConstancia() {
        RespuestaStatusCdr r = parser.leerGetStatusCdr(respuesta("0001", "El comprobante existe y está aceptado.", Base64.getEncoder().encodeToString(ZIP)));

        assertThat(r.getStatusCode()).isEqualTo("0001");
        assertThat(r.getStatusMessage()).isEqualTo("El comprobante existe y está aceptado.");
        assertThat(r.getConstancia()).isEqualTo(ZIP);
    }

    @Test
    void sinContenidoLaConstanciaEsNula() {
        RespuestaStatusCdr r = parser.leerGetStatusCdr(respuesta("0011", "El comprobante de pago electrónico no existe.", null));
        assertThat(r.getStatusCode()).isEqualTo("0011");
        assertThat(r.getConstancia()).isNull();
        assertThat(parser.leerGetStatusCdr(respuesta("0011", "x", "  ")).getConstancia()).isNull();
    }

    @Test
    void leeLaConstanciaAunqueElBase64VengaPartidoEnLineas() {
        String partido = Base64.getMimeEncoder().encodeToString(new byte[300]);
        assertThat(parser.leerGetStatusCdr(respuesta("0001", "ok", partido)).getConstancia()).isEqualTo(new byte[300]);
    }

    @Test
    void unaConstanciaQueNoEsBase64EsInvalida() {
        assertThatThrownBy(() -> parser.leerGetStatusCdr(respuesta("0001", "ok", "@@@@")))
                .isInstanceOf(SunatRespuestaInvalidaException.class).hasMessageContaining("base64");
    }

    @Test
    void unErrorSoapSeDetectaConSuCodigo() {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Client.0111</faultcode><faultstring>No tiene el perfil</faultstring></S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatusCdr(fault))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0111"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"<html/>", "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body/></S:Envelope>"})
    void unaRespuestaSinElEstadoEsInvalida(String xml) {
        assertThatThrownBy(() -> parser.leerGetStatusCdr(xml)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void sinStatusCodeEsInvalida() {
        assertThatThrownBy(() -> parser.leerGetStatusCdr(respuesta("  ", "x", null)))
                .isInstanceOf(SunatRespuestaInvalidaException.class).hasMessageContaining("statusCode");
    }

    @Test
    void unaRespuestaDeGetStatusNoSirveParaGetStatusCdr() {
        String getStatus = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\">"
                + "<status><statusCode>0</statusCode></status></ns2:getStatusResponse></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerGetStatusCdr(getStatus)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    // --- El cliente ---

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private BillServiceClient cliente() {
        return new BillServiceClient(builder.build(), propiedades(), sobres, parser);
    }

    @Test
    void laConsultaVaALaUrlDelServicioDeConsultaYNoALaDelBill() {
        server.expect(requestTo(URL_CONSULTA))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "urn:getStatusCdr"))
                .andExpect(content().string(sobres.getStatusCdr("20100066603", "01", "F001", "7")))
                .andRespond(withSuccess(respuesta("0001", "ok", Base64.getEncoder().encodeToString(ZIP)), MediaType.TEXT_XML));

        RespuestaStatusCdr r = cliente().getStatusCdr("20100066603", "01", "F001", "7");

        assertThat(r.getStatusCode()).isEqualTo("0001");
        assertThat(r.getConstancia()).isEqualTo(ZIP);
        server.verify();
    }

    @Test
    void laUrlDeLaConsultaSaleDeLaConfiguracion() {
        SunatProperties p = propiedades();
        p.getConsultaCdr().setUrl("http://localhost:9999/consulta");
        MockRestServiceServer otro = MockRestServiceServer.bindTo(builder).build();
        otro.expect(requestTo("http://localhost:9999/consulta"))
                .andRespond(withSuccess(respuesta("0011", "no existe", null), MediaType.TEXT_XML));

        new BillServiceClient(builder.build(), p, new SoapEnvelopeBuilder(p), parser).getStatusCdr("20100066603", "01", "F001", "7");

        otro.verify();
    }

    @Test
    void unErrorHttpSinDetalleEsSunatNoDisponible() {
        server.expect(requestTo(URL_CONSULTA)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> cliente().getStatusCdr("20100066603", "01", "F001", "7"))
                .isInstanceOf(SunatNoDisponibleException.class);
    }

    @Test
    void unErrorSoapConHttp500DaElFault() {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Client.0109</faultcode><faultstring>autenticación</faultstring></S:Fault></S:Body></S:Envelope>";
        server.expect(requestTo(URL_CONSULTA)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(fault));
        assertThatThrownBy(() -> cliente().getStatusCdr("20100066603", "01", "F001", "7"))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0109"));
    }

    @Test
    void unDatoInvalidoFallaAntesDeLlamar() {
        assertThatThrownBy(() -> cliente().getStatusCdr("123", "01", "F001", "7")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }
}
