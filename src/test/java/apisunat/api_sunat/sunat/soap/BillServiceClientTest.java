package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.util.Fixtures;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BillServiceClientTest {

    private static final String URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    private static final String CLAVE = "clave-super-secreta-123";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SunatProperties propiedades = propiedades();
    private final SoapEnvelopeBuilder sobres = new SoapEnvelopeBuilder(propiedades);

    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    private static SunatProperties propiedades() {
        SunatProperties p = new SunatProperties();
        p.setUrl(URL);
        p.setRuc("20100066603");
        p.setUsuario("MODDATOS");
        p.setClave(CLAVE);
        p.setConexionTimeoutMs(5000);
        p.setLecturaTimeoutMs(30000);
        return p;
    }

    private BillServiceClient cliente() {
        return new BillServiceClient(builder.build(), propiedades, sobres, new SoapResponseParser());
    }

    private static String respuesta(String statusCode, String content) {
        return "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>"
                + "<ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\"><status><content>" + content
                + "</content><statusCode>" + statusCode + "</statusCode></status></ns2:getStatusResponse></S:Body></S:Envelope>";
    }

    @BeforeEach
    void capturarLogs() {
        logger = (Logger) LoggerFactory.getLogger("apisunat");
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltarLogs() {
        logger.detachAppender(logs);
        logger.setLevel(null);
    }

    // --- Lo que se manda ---

    @Test
    void mandaUnPostALaUrlConfiguradaConLasCabecerasYElCuerpoCorrectos() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("SOAPAction", "urn:getStatus"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "text/xml;charset=UTF-8"))
                .andExpect(content().string(sobres.getStatus("201100000011227")))
                .andRespond(withSuccess(Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml"), MediaType.TEXT_XML));

        cliente().getStatus("201100000011227");

        server.verify();
    }

    @Test
    void hacePocosIntentosUnaSolaLlamadaPorConsulta() {
        server.expect(requestTo(URL)).andRespond(withSuccess(respuesta("98", ""), MediaType.TEXT_XML));
        cliente().getStatus("1");
        server.verify();
    }

    // --- Respuestas buenas ---

    @Test
    void leeLaRespuestaRealDelBetaParaUnTicketQueNoExiste() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml"), MediaType.TEXT_XML));

        RespuestaStatus r = cliente().getStatus("201100000011227");

        assertThat(r.getStatusCode()).isEqualTo("0127");
        assertThat(r.getContent()).isEqualTo("El ticket no existe");
    }

    @Test
    void leeLasTildesEnUtf8AunqueElContentTypeNoDigaElCharset() {
        server.expect(requestTo(URL)).andRespond(
                withSuccess(respuesta("0151", "El número añadió ñandú").getBytes(StandardCharsets.UTF_8), MediaType.TEXT_XML));
        assertThat(cliente().getStatus("1").getContent()).isEqualTo("El número añadió ñandú");
    }

    @Test
    void aceptaCualquierCodigoDeExito2xx() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.ACCEPTED).body(respuesta("98", "")));
        assertThat(cliente().getStatus("1").getStatusCode()).isEqualTo("98");
    }

    // --- Errores SOAP: llegan con HTTP 200 (¡o con 500!) y hay que mirarlos igual ---

    @Test
    void unErrorSoapConHttp200SeDetecta() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(Fixtures.texto("sunat/beta/fault-sin-seguridad.xml"), MediaType.TEXT_XML));

        assertThatThrownBy(() -> cliente().getStatus("1"))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0200"));
    }

    @Test
    void unErrorSoapConHttp500TambienSeDetectaYNoSeConfundeConCaidaDeLaSunat() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(Fixtures.texto("sunat/beta/fault-sin-seguridad.xml")));

        assertThatThrownBy(() -> cliente().getStatus("1"))
                .isInstanceOfSatisfying(SunatFaultException.class, e -> assertThat(e.getCodigo()).isEqualTo("0200"));
    }

    // --- Errores HTTP sin detalle SOAP: la SUNAT no está disponible ---

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 502, 503, 504})
    void unErrorHttpSinCuerpoUtilEsSunatNoDisponible(int codigo) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(codigo)));

        assertThatThrownBy(() -> cliente().getStatus("1"))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> {
                    assertThat(e.isTimeout()).isFalse();
                    assertThat(e.getMessage()).contains(String.valueOf(codigo));
                });
    }

    @Test
    void unErrorHttpConUnaPaginaHtmlDeUnProxyEsSunatNoDisponible() {
        server.expect(requestTo(URL)).andRespond(
                withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html><body>502 Bad Gateway</body></html>"));

        assertThatThrownBy(() -> cliente().getStatus("1")).isInstanceOf(SunatNoDisponibleException.class);
    }

    // --- Respuestas con HTTP 200 pero inservibles ---

    @Test
    void conHttp200PeroUnaPaginaHtmlLaRespuestaEsInvalida() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html><body>Mantenimiento</body></html>", MediaType.TEXT_HTML));
        assertThatThrownBy(() -> cliente().getStatus("1")).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void conHttp200YCuerpoVacioLaRespuestaEsInvalida() {
        server.expect(requestTo(URL)).andRespond(withSuccess("", MediaType.TEXT_XML));
        assertThatThrownBy(() -> cliente().getStatus("1")).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void conHttp200YXmlCortadoLaRespuestaEsInvalida() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<S:Envelope><S:Body><getStatusResponse>", MediaType.TEXT_XML));
        assertThatThrownBy(() -> cliente().getStatus("1")).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    // --- Tamaño máximo de la respuesta ---

    @Test
    void unaRespuestaJustoDelTamanoMaximoSeAcepta() {
        String base = respuesta("0", "");
        String exacta = respuesta("0", "A".repeat(1000 - base.length()));
        assertThat(exacta.getBytes(StandardCharsets.UTF_8)).hasSize(1000);
        server.expect(requestTo(URL)).andRespond(withSuccess(exacta, MediaType.TEXT_XML));

        BillServiceClient cliente = new BillServiceClient(builder.build(), propiedades, sobres, new SoapResponseParser(), 1000);

        assertThat(cliente.getStatus("1").getStatusCode()).isEqualTo("0");
    }

    @Test
    void unaRespuestaUnByteMasGrandeQueElMaximoSeRechaza() {
        String base = respuesta("0", "");
        String pasada = respuesta("0", "A".repeat(1001 - base.length()));
        assertThat(pasada.getBytes(StandardCharsets.UTF_8)).hasSize(1001);
        server.expect(requestTo(URL)).andRespond(withSuccess(pasada, MediaType.TEXT_XML));

        BillServiceClient cliente = new BillServiceClient(builder.build(), propiedades, sobres, new SoapResponseParser(), 1000);

        assertThatThrownBy(() -> cliente.getStatus("1"))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("grande");
    }

    // --- Fallas de red ---

    @Test
    void sinConexionEsSunatNoDisponibleSinTimeout() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new ConnectException("Connection refused");
        });

        assertThatThrownBy(() -> cliente().getStatus("1"))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> {
                    assertThat(e.isTimeout()).isFalse();
                    assertThat(e.getMessage()).contains("conectar");
                });
    }

    @Test
    void unTimeoutSeMarcaComoTimeout() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        assertThatThrownBy(() -> cliente().getStatus("1"))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> {
                    assertThat(e.isTimeout()).isTrue();
                    assertThat(e.getMessage()).contains("a tiempo");
                });
    }

    @Test
    void cualquierOtraFallaDeEntradaSalidaEsSunatNoDisponible() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("Connection reset");
        });

        assertThatThrownBy(() -> cliente().getStatus("1")).isInstanceOf(SunatNoDisponibleException.class);
    }

    // --- Entradas inválidas: ni siquiera se llama a la SUNAT ---

    @Test
    void conUnTicketVacioNoSeLlamaALaSunat() {
        assertThatThrownBy(() -> cliente().getStatus(" ")).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    // --- Secretos ---

    @Test
    void laClaveNuncaApareceEnLosLogsNiEnLosMensajesDeError() {
        server.expect(requestTo(URL)).andRespond(withSuccess(respuesta("0127", "x"), MediaType.TEXT_XML));
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(URL)).andRespond(withSuccess("basura", MediaType.TEXT_XML));
        BillServiceClient cliente = cliente();

        cliente.getStatus("1");
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cliente.getStatus("1"))
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CLAVE));
        }

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(ev -> assertThat(ev.getFormattedMessage()).doesNotContain(CLAVE).doesNotContain("Password"));
    }

    @Test
    void sinCredencialesNiSobresEnLosLogsNiSiquieraEnDebug() {
        server.expect(requestTo(URL)).andRespond(withSuccess(respuesta("0127", "x"), MediaType.TEXT_XML));
        cliente().getStatus("201100000011227");
        assertThat(logs.list).allSatisfy(ev -> assertThat(ev.getFormattedMessage())
                .doesNotContain("UsernameToken").doesNotContain("20100066603MODDATOS").doesNotContain("Envelope"));
    }
}
