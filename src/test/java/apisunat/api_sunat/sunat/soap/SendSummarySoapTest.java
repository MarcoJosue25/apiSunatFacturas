package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
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

// sendSummary: enviar un resumen diario de boletas. La SUNAT contesta con un ticket.
class SendSummarySoapTest {

    private static final String URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static final byte[] ZIP = {'P', 'K', 3, 4, 9, 8, 7, (byte) 255};

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

    private static String respuesta(String ticket) {
        return "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>"
                + "<br:sendSummaryResponse xmlns:br=\"http://service.sunat.gob.pe\"><ticket>" + ticket + "</ticket></br:sendSummaryResponse></S:Body></S:Envelope>";
    }

    private final SoapEnvelopeBuilder sobres = new SoapEnvelopeBuilder(propiedades());
    private final SoapResponseParser parser = new SoapResponseParser();

    // --- El sobre ---

    @Test
    void elSobreTieneElNombreYElZipEnBase64() {
        Document d = leer(sobres.sendSummary("20100066603-RC-20261028-1.zip", ZIP));
        assertThat(texto(d, "fileName")).isEqualTo("20100066603-RC-20261028-1.zip");
        assertThat(Base64.getDecoder().decode(texto(d, "contentFile"))).isEqualTo(ZIP);
        assertThat(texto(d, "Username")).isEqualTo("20100066603MODDATOS");
    }

    @Test
    void elSobreEsExactamenteElDelManualConLaOperacionSendSummary() {
        String esperado = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">"
                + "<soapenv:Header><wsse:Security><wsse:UsernameToken>"
                + "<wsse:Username>20100066603MODDATOS</wsse:Username><wsse:Password>MODDATOS</wsse:Password>"
                + "</wsse:UsernameToken></wsse:Security></soapenv:Header>"
                + "<soapenv:Body><ser:sendSummary><fileName>20100066603-RC-20261028-1.zip</fileName><contentFile>"
                + Base64.getEncoder().encodeToString(ZIP) + "</contentFile></ser:sendSummary></soapenv:Body></soapenv:Envelope>";
        assertThat(sobres.sendSummary("20100066603-RC-20261028-1.zip", ZIP)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"sin-extension", "a.xml", "a b.zip", "a<b>.zip", "../a.zip", "a.ZIP"})
    void unNombreInvalidoSeRechaza(String nombre) {
        assertThatThrownBy(() -> sobres.sendSummary(nombre, ZIP)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unZipVacioSeRechaza() {
        assertThatThrownBy(() -> sobres.sendSummary("a.zip", new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sobres.sendSummary("a.zip", null)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- La respuesta ---

    @ParameterizedTest
    @ValueSource(strings = {"1793158812535", "1", "201100000011227", "123456789012345678901234567890"})
    void leeElTicket(String ticket) {
        assertThat(parser.leerSendSummary(respuesta(ticket))).isEqualTo(ticket);
    }

    @Test
    void elTicketConEspaciosAlrededorSeLimpia() {
        assertThat(parser.leerSendSummary(respuesta("  1793158812535\n"))).isEqualTo("1793158812535");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", "   ", "abc", "12 34", "12.5", "-1", "1234567890123456789012345678901"})
    void unTicketInvalidoOVacioEsUnaRespuestaInvalida(String ticket) {
        assertThatThrownBy(() -> parser.leerSendSummary(respuesta(ticket == null ? "" : ticket)))
                .isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void unaRespuestaSinTicketOQueNoEsDeSendSummaryEsInvalida() {
        assertThatThrownBy(() -> parser.leerSendSummary("<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body/></S:Envelope>"))
                .isInstanceOf(SunatRespuestaInvalidaException.class);
        assertThatThrownBy(() -> parser.leerSendSummary("<html/>")).isInstanceOf(SunatRespuestaInvalidaException.class);
        assertThatThrownBy(() -> parser.leerSendSummary(null)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void unErrorSoapSeDetectaConSuCodigo() {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault><faultcode>S:Client.0151</faultcode>"
                + "<faultstring>El nombre del archivo ZIP es incorrecto</faultstring></S:Fault></S:Body></S:Envelope>";
        assertThatThrownBy(() -> parser.leerSendSummary(fault))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0151"));
    }

    // --- El cliente ---

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private BillServiceClient cliente() {
        return new BillServiceClient(builder.build(), propiedades(), sobres, parser);
    }

    @Test
    void mandaUnPostConLaAccionSendSummaryYDevuelveElTicket() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "urn:sendSummary"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "text/xml;charset=UTF-8"))
                .andExpect(content().string(sobres.sendSummary("20100066603-RC-20261028-1.zip", ZIP)))
                .andRespond(withSuccess(respuesta("1793158812535"), MediaType.TEXT_XML));

        assertThat(cliente().sendSummary("20100066603-RC-20261028-1.zip", ZIP)).isEqualTo("1793158812535");
        server.verify();
    }

    @Test
    void sinRespuestaEsSunatNoDisponibleYNoSeReintentaSolo() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> cliente().sendSummary("a.zip", ZIP)).isInstanceOf(SunatNoDisponibleException.class);
        server.verify();
    }

    @Test
    void unErrorSoapConHttp500DaElFault() {
        String fault = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault><faultcode>S:Client.0306</faultcode>"
                + "<faultstring>xml</faultstring></S:Fault></S:Body></S:Envelope>";
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(fault));
        assertThatThrownBy(() -> cliente().sendSummary("a.zip", ZIP))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0306"));
    }
}
