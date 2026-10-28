package apisunat.api_sunat;

import apisunat.api_sunat.util.Fixtures;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// La aplicación completa, con su cableado real, hablando por HTTP con una "SUNAT" falsa en localhost.
// Comprueba que controller, service, cliente SOAP, parser y manejador de errores están bien conectados entre sí.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SunatTicketsPuntaAPuntaTest {

    private static HttpServer sunatFalsa;
    private static final AtomicReference<HttpHandler> manejador = new AtomicReference<>();
    private static final AtomicInteger llamadas = new AtomicInteger();
    private static final AtomicReference<String> ultimoCuerpo = new AtomicReference<>();
    private static final AtomicReference<String> ultimaAccion = new AtomicReference<>();

    @LocalServerPort
    private int puerto;

    @BeforeAll
    static void levantarSunatFalsa() throws IOException {
        sunatFalsa = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sunatFalsa.createContext("/billService", ex -> {
            llamadas.incrementAndGet();
            ultimaAccion.set(ex.getRequestHeaders().getFirst("SOAPAction"));
            ultimoCuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            manejador.get().handle(ex);
        });
        sunatFalsa.start();
    }

    @AfterAll
    static void apagarSunatFalsa() {
        sunatFalsa.stop(0);
    }

    @DynamicPropertySource
    static void apuntarALaSunatFalsa(DynamicPropertyRegistry registro) {
        registro.add("sunat.url", () -> "http://127.0.0.1:" + sunatFalsa.getAddress().getPort() + "/billService");
        // Holgado a propósito: la primera consulta con la aplicación sin calentar tarda más y no debe dar un falso timeout
        registro.add("sunat.lectura-timeout-ms", () -> 1500);
    }

    @BeforeEach
    void reiniciar() {
        llamadas.set(0);
        ultimoCuerpo.set(null);
        ultimaAccion.set(null);
        manejador.set(ex -> responder(ex, 200, Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml")));
    }

    private static void responder(HttpExchange ex, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
        ex.sendResponseHeaders(estado, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private record Resultado(int estado, String cuerpo) {
    }

    private Resultado get(String ruta) {
        return RestClient.create("http://localhost:" + puerto).get().uri(ruta)
                .exchange((peticion, respuesta) -> new Resultado(
                        respuesta.getStatusCode().value(),
                        new String(respuesta.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    @Test
    void unaConsultaDeTicketLlegaALaSunatYVuelveConLaRespuesta() {
        Resultado r = get("/api/v1/sunat/tickets/201100000011227");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.statusCode")).isEqualTo("0127");
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.content")).isEqualTo("El ticket no existe");
        assertThat(llamadas.get()).isEqualTo(1);
        assertThat(ultimaAccion.get()).isEqualTo("urn:getStatus");
        assertThat(ultimoCuerpo.get())
                .contains("<ticket>201100000011227</ticket>")
                .contains("<wsse:Username>20100066603MODDATOS</wsse:Username>");
    }

    @Test
    void unTicketTerminadoTraeElZipEnBase64() {
        manejador.set(ex -> responder(ex, 200,
                "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><ns2:getStatusResponse xmlns:ns2=\"http://service.sunat.gob.pe\">"
                        + "<status><content>UEsDBAoAAAAA</content><statusCode>0</statusCode></status></ns2:getStatusResponse></S:Body></S:Envelope>"));

        Resultado r = get("/api/v1/sunat/tickets/55");

        assertThat(r.estado()).isEqualTo(200);
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.statusCode")).isEqualTo("0");
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.content")).isEqualTo("UEsDBAoAAAAA");
    }

    @Test
    void unErrorSoapDeLaSunatSeConvierteEn502ConSuCodigo() {
        manejador.set(ex -> responder(ex, 200, Fixtures.texto("sunat/beta/fault-sin-seguridad.xml")));

        Resultado r = get("/api/v1/sunat/tickets/1");

        assertThat(r.estado()).isEqualTo(502);
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.codigoSunat")).isEqualTo("0200");
    }

    @Test
    void siLaSunatTardaDemasiadoLaApiResponde504() {
        manejador.set(ex -> {
            try {
                Thread.sleep(3000);
                responder(ex, 200, "<x/>");
            } catch (Exception ignorada) {
                // el cliente ya se fue
            }
        });

        Resultado r = get("/api/v1/sunat/tickets/1");

        assertThat(r.estado()).isEqualTo(504);
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.mensaje")).contains("a tiempo");
    }

    @Test
    void siLaSunatDevuelveUnErrorHttpSinDetalleLaApiResponde502() {
        manejador.set(ex -> responder(ex, 503, "Service Unavailable"));

        Resultado r = get("/api/v1/sunat/tickets/1");

        assertThat(r.estado()).isEqualTo(502);
        assertThat(JsonPath.<String>read(r.cuerpo(), "$.mensaje")).contains("503");
    }

    @Test
    void siLaSunatDevuelveBasuraLaApiResponde502() {
        manejador.set(ex -> responder(ex, 200, "<html>Mantenimiento</html>"));

        assertThat(get("/api/v1/sunat/tickets/1").estado()).isEqualTo(502);
    }

    @Test
    void unTicketInvalidoSeRechazaSinLlamarALaSunat() {
        Resultado r = get("/api/v1/sunat/tickets/abc");

        assertThat(r.estado()).isEqualTo(400);
        assertThat(llamadas.get()).isZero();
    }

    @Test
    void unaRutaQueNoExisteEs404YNoLlamaALaSunat() {
        assertThat(get("/api/v1/otra-cosa").estado()).isEqualTo(404);
        assertThat(llamadas.get()).isZero();
    }

    @Test
    void laRespuestaDeErrorNuncaDejaVerLasCredencialesNiLaUrlDeLaSunat() {
        manejador.set(ex -> responder(ex, 500, "boom"));

        String cuerpo = get("/api/v1/sunat/tickets/1").cuerpo();

        assertThat(cuerpo).doesNotContain("MODDATOS").doesNotContain("127.0.0.1").doesNotContain("billService");
    }

    @Test
    void variasConsultasSeguidasSeAtiendenBien() {
        for (int i = 1; i <= 15; i++) {
            assertThat(get("/api/v1/sunat/tickets/" + i).estado()).isEqualTo(200);
        }
        assertThat(llamadas.get()).isEqualTo(15);
    }
}
