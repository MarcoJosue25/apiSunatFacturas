package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.util.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.Random;

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

// sendBill: el envío de una factura. Cubre el sobre, la lectura de la respuesta y el cliente con un servidor simulado.
class SendBillSoapTest {

    private static final String URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static final byte[] ZIP = new byte[]{'P', 'K', 3, 4, 10, 20, 30, (byte) 200, (byte) 255, 0, 1};

    private static SunatProperties propiedades() {
        SunatProperties p = new SunatProperties();
        p.setUrl(URL);
        p.setRuc("20100066603");
        p.setUsuario("MODDATOS");
        p.setClave("MODDATOS");
        p.setConexionTimeoutMs(5000);
        p.setLecturaTimeoutMs(30000);
        return p;
    }

    private static String respuestaOk(String base64) {
        return "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>"
                + "<br:sendBillResponse xmlns:br=\"http://service.sunat.gob.pe\"><applicationResponse>" + base64
                + "</applicationResponse></br:sendBillResponse></S:Body></S:Envelope>";
    }

    // --- El sobre ---

    private final SoapEnvelopeBuilder sobres = new SoapEnvelopeBuilder(propiedades());

    @Test
    void elSobreDeSendBillTraeElNombreYElZipEnBase64() {
        Document doc = leer(sobres.sendBill("20100066603-01-F001-1.zip", ZIP));

        assertThat(texto(doc, "fileName")).isEqualTo("20100066603-01-F001-1.zip");
        assertThat(Base64.getDecoder().decode(texto(doc, "contentFile"))).isEqualTo(ZIP);
    }

    @Test
    void elSobreDeSendBillUsaLasMismasCredencialesQueGetStatus() {
        Document doc = leer(sobres.sendBill("a.zip", ZIP));
        assertThat(texto(doc, "Username")).isEqualTo("20100066603MODDATOS");
        assertThat(texto(doc, "Password")).isEqualTo("MODDATOS");
    }

    @Test
    void elSobreDeSendBillTieneLaMismaEstructuraQueElDelManual() {
        String esperado = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>20100066603MODDATOS</wsse:Username>"
                + "<wsse:Password>MODDATOS</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body><ser:sendBill><fileName>20100066603-01-F001-1.zip</fileName>"
                + "<contentFile>" + Base64.getEncoder().encodeToString(ZIP) + "</contentFile></ser:sendBill></soapenv:Body>"
                + "</soapenv:Envelope>";
        assertThat(sobres.sendBill("20100066603-01-F001-1.zip", ZIP)).isEqualTo(esperado);
    }

    @Test
    void elBase64NoLlevaSaltosDeLineaAunqueElZipSeaGrande() {
        byte[] grande = new byte[500_000];
        new Random(7).nextBytes(grande);
        String sobre = sobres.sendBill("a.zip", grande);
        assertThat(sobre).doesNotContain("\n").doesNotContain("\r");
        assertThat(Base64.getDecoder().decode(texto(leer(sobre), "contentFile"))).isEqualTo(grande);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "sin-extension", "a.xml", "a.ZIP", "a.zip.zip", ".zip", "con espacio.zip", "a<b>.zip", "a&b.zip",
            "a\"b.zip", "../a.zip", "carpeta/a.zip", "a.zip\n", "ñandu.zip", "a.zipx"})
    void unNombreDeZipInvalidoSeRechazaYNuncaVaAlXml(String nombre) {
        assertThatThrownBy(() -> sobres.sendBill(nombre, ZIP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".zip");
    }

    @Test
    void unZipVacioONuloSeRechaza() {
        assertThatThrownBy(() -> sobres.sendBill("a.zip", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sobres.sendBill("a.zip", new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    // --- La respuesta ---

    private final SoapResponseParser parser = new SoapResponseParser();

    @Test
    void leeLaConstanciaYLaDecodificaDelBase64() {
        assertThat(parser.leerSendBill(respuestaOk(Base64.getEncoder().encodeToString(ZIP)))).isEqualTo(ZIP);
    }

    @Test
    void leeUnBase64PartidoEnLineasComoLoMandaAVecesLaSunat() {
        String base64 = Base64.getMimeEncoder().encodeToString(new byte[300]);
        assertThat(base64).contains("\r\n");
        assertThat(parser.leerSendBill(respuestaOk(base64))).isEqualTo(new byte[300]);
    }

    @Test
    void unErrorSoapEnSendBillSeDetectaConSuCodigo() {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Client.0111</faultcode><faultstring>No tiene el perfil para enviar comprobantes electronicos</faultstring>"
                + "</S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerSendBill(fault))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0111"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0151", "0306", "0109", "1033"})
    void ElCodigoDeLosErroresSoapComunesSeExtrae(String codigo) {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>soap-env:Client." + codigo + "</faultcode><faultstring>texto</faultstring></S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerSendBill(fault))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo(codigo));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "<html/>", "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body/></S:Envelope>"})
    void unaRespuestaSinLaConstanciaEsInvalida(String xml) {
        assertThatThrownBy(() -> parser.leerSendBill(xml)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void unaConstanciaVaciaEsInvalida(String contenido) {
        assertThatThrownBy(() -> parser.leerSendBill(respuestaOk(contenido)))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("applicationResponse");
    }

    @ParameterizedTest
    @ValueSource(strings = {"esto no es base64!!", "a", "@@@@", "PK=", "AAAA=A"})
    void unaConstanciaQueNoEsBase64EsInvalida(String contenido) {
        assertThatThrownBy(() -> parser.leerSendBill(respuestaOk(contenido)))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void unaRespuestaDeGetStatusNoSirveParaSendBill() {
        String getStatus = Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml");
        assertThatThrownBy(() -> parser.leerSendBill(getStatus)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void unaRespuestaConDoctypeSeRechaza() {
        String malo = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>";
        assertThatThrownBy(() -> parser.leerSendBill(malo)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    // --- El cliente con un servidor simulado ---

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private BillServiceClient cliente() {
        return new BillServiceClient(builder.build(), propiedades(), sobres, parser);
    }

    @Test
    void sendBillMandaUnPostConLaAccionYElSobreCorrectosYDevuelveElZipDeLaConstancia() {
        byte[] constancia = {'P', 'K', 9, 9, 9};
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "urn:sendBill"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "text/xml;charset=UTF-8"))
                .andExpect(content().string(sobres.sendBill("20100066603-01-F001-1.zip", ZIP)))
                .andRespond(withSuccess(respuestaOk(Base64.getEncoder().encodeToString(constancia)), MediaType.TEXT_XML));

        assertThat(cliente().sendBill("20100066603-01-F001-1.zip", ZIP)).isEqualTo(constancia);
        server.verify();
    }

    @Test
    void sendBillHaceUnaSolaLlamadaYNoReintentaSolo() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> cliente().sendBill("a.zip", ZIP)).isInstanceOf(SunatNoDisponibleException.class);
        server.verify();
    }

    @Test
    void sendBillConErrorSoapYHttp500LanzaElFaultYNoCaidaDeLaSunat() {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault>"
                + "<faultcode>S:Client.0151</faultcode><faultstring>El archivo ZIP no contiene el comprobante</faultstring>"
                + "</S:Fault></S:Body></S:Envelope>";
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(fault));

        assertThatThrownBy(() -> cliente().sendBill("a.zip", ZIP))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0151"));
    }

    @Test
    void sendBillConHttp500SinDetalleEsSunatNoDisponible() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("<html>Error</html>"));
        assertThatThrownBy(() -> cliente().sendBill("a.zip", ZIP))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.isTimeout()).isFalse());
    }

    @Test
    void sendBillConTimeoutLanzaSunatNoDisponibleConLaMarcaDeTimeout() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });
        assertThatThrownBy(() -> cliente().sendBill("a.zip", ZIP))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.isTimeout()).isTrue());
    }

    @Test
    void sendBillSinConexionLanzaSunatNoDisponibleSinMarcaDeTimeout() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("Connection refused");
        });
        assertThatThrownBy(() -> cliente().sendBill("a.zip", ZIP))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.isTimeout()).isFalse());
    }

    @Test
    void sendBillConUnNombreInvalidoFallaAntesDeLlamarALaSunat() {
        assertThatThrownBy(() -> cliente().sendBill("mal nombre.zip", ZIP)).isInstanceOf(IllegalArgumentException.class);
        server.verify();   // no se esperaba ninguna llamada
    }

    @Test
    void laClaveSolNuncaApareceEnElMensajeDeUnErrorDeSendBill() {
        SunatProperties conClave = propiedades();
        conClave.setClave("clave-super-secreta-123");
        MockRestServiceServer otro = MockRestServiceServer.bindTo(builder).build();
        otro.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        BillServiceClient c = new BillServiceClient(builder.build(), conClave, new SoapEnvelopeBuilder(conClave), parser);

        assertThatThrownBy(() -> c.sendBill("a.zip", ZIP))
                .isInstanceOf(SunatNoDisponibleException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("clave-super-secreta-123"));
    }
}
