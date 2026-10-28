package apisunat.api_sunat.sunat.soap;

import apisunat.api_sunat.config.SunatClientConfig;
import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.util.Fixtures;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Estas pruebas usan un servidor HTTP de verdad (el del JDK, en localhost) para comprobar lo que un simulador no puede:
// timeouts reales, conexiones cortadas y redirecciones. El RestClient es el mismo que se arma en producción.
class BillServiceClientServidorRealTest {

    private HttpServer servidor;

    @AfterEach
    void apagar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private String levantar(HttpHandler manejador) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/billService", manejador);
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/billService";
    }

    private BillServiceClient cliente(String url, int conexionMs, int lecturaMs, int maxBytes) {
        SunatProperties p = new SunatProperties();
        p.setUrl(url);
        p.setRuc("20100066603");
        p.setUsuario("MODDATOS");
        p.setClave("MODDATOS");
        p.setConexionTimeoutMs(conexionMs);
        p.setLecturaTimeoutMs(lecturaMs);
        return new BillServiceClient(new SunatClientConfig().sunatRestClient(p), p, new SoapEnvelopeBuilder(p), new SoapResponseParser(), maxBytes);
    }

    private BillServiceClient cliente(String url) {
        return cliente(url, 2000, 2000, 20 * 1024 * 1024);
    }

    private static void responder(HttpExchange ex, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
        ex.sendResponseHeaders(estado, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void porElCableSeMandaLoQueDebeYSeLeeLaRespuestaReal() throws Exception {
        AtomicReference<String> metodo = new AtomicReference<>();
        AtomicReference<String> soapAction = new AtomicReference<>();
        AtomicReference<String> tipo = new AtomicReference<>();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        String url = levantar(ex -> {
            metodo.set(ex.getRequestMethod());
            soapAction.set(ex.getRequestHeaders().getFirst("SOAPAction"));
            tipo.set(ex.getRequestHeaders().getFirst("Content-Type"));
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(ex, 200, Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml"));
        });

        RespuestaStatus r = cliente(url).getStatus("201100000011227");

        assertThat(r.getStatusCode()).isEqualTo("0127");
        assertThat(metodo.get()).isEqualTo("POST");
        assertThat(soapAction.get()).isEqualTo("urn:getStatus");
        assertThat(tipo.get()).isEqualToIgnoringCase("text/xml;charset=UTF-8");
        assertThat(cuerpo.get()).contains("<ticket>201100000011227</ticket>").contains("<wsse:Username>20100066603MODDATOS</wsse:Username>");
    }

    @Test
    void siElServidorTardaMasQueElTimeoutDeLecturaEsTimeout() throws Exception {
        String url = levantar(ex -> {
            try {
                Thread.sleep(1500);
                responder(ex, 200, "<x/>");
            } catch (Exception ignorada) {
                // el cliente ya se fue
            }
        });
        BillServiceClient cliente = cliente(url, 2000, 300, 1_000_000);

        long inicio = System.currentTimeMillis();
        assertThatThrownBy(() -> cliente.getStatus("1"))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.isTimeout()).isTrue());

        assertThat(System.currentTimeMillis() - inicio).as("no debe esperar toda la respuesta").isLessThan(1400);
    }

    @Test
    void siElServidorRespondeDentroDelTimeoutNoHayProblema() throws Exception {
        String url = levantar(ex -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            responder(ex, 200, Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml"));
        });

        assertThat(cliente(url, 2000, 1500, 1_000_000).getStatus("1").getStatusCode()).isEqualTo("0127");
    }

    @Test
    void sinNadieEscuchandoEnElPuertoEsSunatNoDisponibleSinTimeout() throws Exception {
        int puertoLibre;
        try (ServerSocket s = new ServerSocket(0)) {
            puertoLibre = s.getLocalPort();
        }
        BillServiceClient cliente = cliente("http://127.0.0.1:" + puertoLibre + "/billService");

        assertThatThrownBy(() -> cliente.getStatus("1"))
                .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.isTimeout()).isFalse());
    }

    @Test
    void siElServidorCortaLaConexionAMitadDeLaRespuestaEsSunatNoDisponible() throws Exception {
        String url = levantar(ex -> {
            ex.sendResponseHeaders(200, 5000);
            ex.getResponseBody().write("<S:Envelope>".getBytes());
            ex.getResponseBody().flush();
            ex.close();
        });

        assertThatThrownBy(() -> cliente(url).getStatus("1")).isInstanceOf(SunatNoDisponibleException.class);
    }

    @Test
    void noSigueLasRedireccionesParaNoMandarLasCredencialesAOtroLado() throws Exception {
        AtomicReference<Boolean> visitado = new AtomicReference<>(false);
        HttpServer otro = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        otro.createContext("/", ex -> {
            visitado.set(true);
            responder(ex, 200, "<x/>");
        });
        otro.start();
        try {
            String destino = "http://127.0.0.1:" + otro.getAddress().getPort() + "/robo";
            String url = levantar(ex -> {
                ex.getResponseHeaders().add("Location", destino);
                ex.sendResponseHeaders(302, -1);
                ex.close();
            });

            assertThatThrownBy(() -> cliente(url).getStatus("1"))
                    .isInstanceOfSatisfying(SunatNoDisponibleException.class, e -> assertThat(e.getMessage()).contains("302"));
            assertThat(visitado.get()).isFalse();
        } finally {
            otro.stop(0);
        }
    }

    @Test
    void unErrorSoapConHttp500DeUnServidorRealSeDetecta() throws Exception {
        String url = levantar(ex -> responder(ex, 500, Fixtures.texto("sunat/beta/fault-sin-seguridad.xml")));

        assertThatThrownBy(() -> cliente(url).getStatus("1")).isInstanceOf(SunatFaultException.class);
    }

    @Test
    void unaRespuestaMasGrandeQueElTopeSeRechazaSinTraerselaToda() throws Exception {
        String url = levantar(ex -> responder(ex, 200, "<a>" + "A".repeat(3_000_000) + "</a>"));

        assertThatThrownBy(() -> cliente(url, 2000, 5000, 1_000_000).getStatus("1"))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("grande");
    }

    @Test
    void variasConsultasSeguidasFuncionanSinColgarse() throws Exception {
        String url = levantar(ex -> responder(ex, 200, Fixtures.texto("sunat/beta/getStatus-ticket-inexistente.xml")));
        BillServiceClient cliente = cliente(url);
        for (int i = 0; i < 25; i++) {
            assertThat(cliente.getStatus(String.valueOf(i + 1)).getStatusCode()).isEqualTo("0127");
        }
    }
}
