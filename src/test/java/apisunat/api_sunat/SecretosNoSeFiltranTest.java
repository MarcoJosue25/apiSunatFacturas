package apisunat.api_sunat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Los secretos (clave SOL, clave del certificado, llave de la API) no deben aparecer en ningún log ni en ninguna respuesta, ni cuando todo sale bien
// ni cuando la SUNAT falla, el cuerpo es inválido o la llave es incorrecta. Se mira todo lo que se escribe en el log, a nivel DEBUG.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "sunat.usuario=UsuarioSecundario", "sunat.clave=ClaveSolUnicaZ9q", "sunat.api-key=LlaveDeLaApiUnicaK7w-0123456789abcdef",
        "sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123",
        "logging.level.root=DEBUG", "logging.level.apisunat=TRACE", "sunat.conexion-timeout-ms=500"})
class SecretosNoSeFiltranTest {

    private static final int PUERTO = libre();
    private static final List<String> SECRETOS = List.of("ClaveSolUnicaZ9q", "LlaveDeLaApiUnicaK7w-0123456789abcdef", "prueba123");
    private static final String LLAVE = "LlaveDeLaApiUnicaK7w-0123456789abcdef";
    private static final String FACTURA = """
            {"idExterno": "%s",
             "cliente": {"ruc": "20123456786", "razonSocial": "CLIENTE SAC"},
             "items": [{"descripcion": "Polo", "cantidad": 1, "precioUnitario": 10.00}]}""";

    @DynamicPropertySource
    static void apuntarALaSunatLocal(DynamicPropertyRegistry registro) {
        registro.add("sunat.url", () -> "http://localhost:" + PUERTO + "/billService");
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final List<String> respuestas = new ArrayList<>();
    private final AtomicInteger modo = new AtomicInteger();   // 0: error HTTP 500, 1: basura, 2: fault SOAP de acceso denegado
    private HttpServer sunatLocal;
    private RestClient http;

    private static int libre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void preparar() throws IOException {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
        sunatLocal = HttpServer.create(new InetSocketAddress("localhost", PUERTO), 0);
        sunatLocal.createContext("/billService", exchange -> {
            String cuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // El sobre que llega a la SUNAT sí lleva la clave (es lo normal); lo que no debe pasar es que se escriba en un log nuestro
            assertThat(cuerpo).contains("ClaveSolUnicaZ9q");
            String respuesta = switch (modo.get()) {
                case 1 -> "esto no es xml <<<";
                case 2 -> "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><S:Fault><faultcode>S:Client</faultcode>"
                        + "<faultstring>El usuario o la clave son incorrectos</faultstring></S:Fault></S:Body></S:Envelope>";
                default -> "Service Unavailable";
            };
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(modo.get() == 0 ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        sunatLocal.start();
        http = RestClient.builder().baseUrl("http://localhost:" + puerto).defaultStatusHandler(codigo -> true, (p, r) -> { })
                .defaultHeader("X-API-Key", LLAVE).build();
    }

    @AfterEach
    void apagar() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        logs.stop();
        sunatLocal.stop(0);
    }

    private void emitir(String id) {
        ResponseEntity<String> r = http.post().uri("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).body(FACTURA.formatted(id))
                .retrieve().toEntity(String.class);
        respuestas.add(r.getHeaders() + "\n" + r.getBody());
    }

    private String todoElLog() {
        StringBuilder todo = new StringBuilder();
        for (ILoggingEvent e : new ArrayList<>(logs.list)) {
            todo.append(e.getFormattedMessage()).append('\n');
            for (IThrowableProxy t = e.getThrowableProxy(); t != null; t = t.getCause()) {
                todo.append(t.getClassName()).append(": ").append(t.getMessage()).append('\n');
            }
        }
        return todo.toString();
    }

    @Test
    void ningunSecretoApareceEnLosLogsNiEnLasRespuestasEnNingunCaso() {
        // La SUNAT contesta un error HTTP, basura, y un error SOAP que repite la clave; luego una llave incorrecta
        for (int caso = 0; caso <= 2; caso++) {
            modo.set(caso);
            emitir("pedido-" + caso);
        }
        ResponseEntity<String> sinLlave = RestClient.builder().baseUrl("http://localhost:" + puerto).defaultStatusHandler(c -> true, (p, r) -> { }).build()
                .post().uri("/api/v1/facturas").header("X-API-Key", "intento-de-adivinar-la-llave").contentType(MediaType.APPLICATION_JSON)
                .body(FACTURA.formatted("x")).retrieve().toEntity(String.class);
        respuestas.add(sinLlave.getBody());

        assertThat(logs.list).as("el log tiene que estar capturando algo").isNotEmpty();
        String log = todoElLog();
        assertThat(log).contains("SUNAT sendBill");
        for (String secreto : SECRETOS) {
            assertThat(log).as("log con " + secreto).doesNotContain(secreto);
            for (String respuesta : respuestas) {
                assertThat(respuesta).as("respuesta con " + secreto).doesNotContain(secreto);
            }
        }
        assertThat(log).doesNotContain("intento-de-adivinar-la-llave");
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR && e.getFormattedMessage().contains("Clave"));
    }
}
