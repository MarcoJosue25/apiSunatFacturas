package apisunat.api_sunat;

import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.RelojMutable;
import apisunat.api_sunat.util.ZipUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// "Apago la conexión a la SUNAT, emito, la restauro y el comprobante se envía solo": con el cliente HTTP de verdad contra un servidor
// SOAP local que primero no existe (conexión rechazada) y después arranca, MySQL real y el planificador encendido.
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123",
        "sunat.reintentos.habilitado=true", "sunat.reintentos.intervalo-segundos=1", "sunat.conexion-timeout-ms=500"})
class ReintentoPuntaAPuntaTest {

    private static final int PUERTO = libre();

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @DynamicPropertySource
    static void apuntarALaSunatLocal(DynamicPropertyRegistry registro) {
        registro.add("sunat.url", () -> "http://localhost:" + PUERTO + "/billService");
    }

    @Autowired
    private RelojMutable reloj;
    @Autowired
    private ComprobanteService comprobanteService;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    private HttpServer sunatLocal;
    private final AtomicInteger envios = new AtomicInteger();

    private static int libre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void preparar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
        reloj.fijar(Instant.parse("2026-10-28T15:00:00Z"));
        envios.set(0);
    }

    @AfterEach
    void apagar() {
        if (sunatLocal != null) {
            sunatLocal.stop(0);
            sunatLocal = null;
        }
    }

    private void encenderLaSunat() throws IOException {
        sunatLocal = HttpServer.create(new InetSocketAddress("localhost", PUERTO), 0);
        sunatLocal.createContext("/billService", exchange -> {
            String cuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("<fileName>(.*?)</fileName>").matcher(cuerpo);
            m.find();
            String nombre = m.group(1);
            String numero = nombre.substring(nombre.indexOf("-01-") + 4, nombre.length() - 4);
            String cdr = Fixtures.texto("sunat/cdr/aceptado.xml").replace("FA01-981", numero);
            byte[] zip = ZipUtil.comprimir("R-" + nombre.replace(".zip", ".xml"), cdr.getBytes(StandardCharsets.ISO_8859_1));
            String respuesta = "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>"
                    + "<br:sendBillResponse xmlns:br=\"http://service.sunat.gob.pe\"><applicationResponse>"
                    + Base64.getEncoder().encodeToString(zip) + "</applicationResponse></br:sendBillResponse></S:Body></S:Envelope>";
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            envios.incrementAndGet();
            exchange.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        sunatLocal.start();
    }

    private static FacturaRequest factura(String id) {
        return FacturaRequest.builder().idExterno(id)
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(List.of(ItemRequest.builder().descripcion("Polo").cantidad(new BigDecimal("1")).precioUnitario(new BigDecimal("10.00")).build()))
                .build();
    }

    @Test
    void conLaSunatSinConexionLaFacturaQuedaGuardadaYAlVolverLaSunatSeEnviaSola() throws Exception {
        // 1. La SUNAT no existe: la conexión es rechazada
        FacturaResponse r = comprobanteService.emitir(factura("pedido-1")).factura();
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(r.getNumero()).isEqualTo("F001-1");
        assertThat(envios.get()).isZero();

        // 2. La SUNAT vuelve y pasa la espera de 1 minuto (en el reloj de la aplicación): el planificador manda la factura
        encenderLaSunat();
        reloj.avanzar(Duration.ofMinutes(2));

        long limite = System.currentTimeMillis() + 30_000;
        EstadoComprobante estado = EstadoComprobante.ERROR_TEMPORAL;
        while (System.currentTimeMillis() < limite && estado != EstadoComprobante.ACEPTADO) {
            Thread.sleep(250);
            estado = comprobantes.findById(r.getId()).orElseThrow().getEstado();
        }

        assertThat(estado).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(envios.get()).isEqualTo(1);   // una sola vez
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(r.getId())).extracting(i -> i.getResultado())
                .containsExactly("SIN_RESPUESTA", "ACEPTADO");
    }
}
