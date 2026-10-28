package apisunat.api_sunat;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import apisunat.api_sunat.util.RelojMutable;
import apisunat.api_sunat.util.ZipUtil;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;

// El camino completo de una boleta por HTTP de verdad: POST de la boleta, resumen diario, el cliente SOAP real contra una SUNAT local
// que contesta el ticket primero "en proceso" y después "terminado", y la boleta que acaba aceptada.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class BoletaResumenPuntaAPuntaTest {

    private static final int PUERTO = libre();
    private static final String BOLETA = """
            {"idExterno": "%s",
             "cliente": {"tipoDocumento": "1", "numeroDocumento": "46237547", "nombre": "Luana Pazos"},
             "items": [{"descripcion": "Camión ñandú", "cantidad": 2, "precioUnitario": 35.00}]}""";

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

    @LocalServerPort
    private int puerto;
    @Autowired
    private RelojMutable reloj;
    @Autowired
    private SunatProperties propiedades;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private ResumenDiarioRepository resumenes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    private RestClient http;
    private HttpServer sunatLocal;
    private final List<String> cuerpos = new CopyOnWriteArrayList<>();
    private final AtomicInteger consultas = new AtomicInteger();

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
        resumenes.deleteAll();
        series.deleteAll();
        reloj.fijar(Instant.parse("2026-10-28T15:00:00Z"));
        cuerpos.clear();
        consultas.set(0);
        http = RestClient.builder().baseUrl("http://localhost:" + puerto)
                .defaultStatusHandler(codigo -> true, (peticion, respuesta) -> { }).build();
        encenderLaSunat();
    }

    @AfterEach
    void apagar() {
        sunatLocal.stop(0);
    }

    private static String sobre(String interior) {
        return "<S:Envelope xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body>" + interior + "</S:Body></S:Envelope>";
    }

    private void encenderLaSunat() throws IOException {
        sunatLocal = HttpServer.create(new InetSocketAddress("localhost", PUERTO), 0);
        sunatLocal.createContext("/billService", exchange -> {
            String cuerpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            cuerpos.add(cuerpo);
            String respuesta;
            if (cuerpo.contains("<ser:sendSummary>")) {
                respuesta = sobre("<br:sendSummaryResponse xmlns:br=\"http://service.sunat.gob.pe\"><ticket>202610280001</ticket></br:sendSummaryResponse>");
            } else if (consultas.incrementAndGet() == 1) {
                respuesta = sobre("<br:getStatusResponse xmlns:br=\"http://service.sunat.gob.pe\"><status><content>En proceso</content>"
                        + "<statusCode>98</statusCode></status></br:getStatusResponse>");
            } else {
                respuesta = sobre("<br:getStatusResponse xmlns:br=\"http://service.sunat.gob.pe\"><status><content>" + constancia()
                        + "</content><statusCode>0</statusCode></status></br:getStatusResponse>");
            }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        sunatLocal.start();
    }

    private static String constancia() {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><ar:ApplicationResponse"
                + " xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>RC-20261028-1</cbc:ReferenceID><cbc:ResponseCode>0</cbc:ResponseCode>"
                + "<cbc:Description>El Resumen diario RC-20261028-1 ha sido aceptado</cbc:Description></cac:Response></cac:DocumentResponse>"
                + "</ar:ApplicationResponse>";
        return Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-RC-20261028-1.xml", xml.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private ResponseEntity<String> post(String ruta, String cuerpo) {
        var peticion = http.post().uri(ruta);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
        }
        return peticion.retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> get(String ruta) {
        return http.get().uri(ruta).retrieve().toEntity(String.class);
    }

    private String estadoDeLaBoleta(int id) {
        return JsonPath.read(get("/api/v1/boletas/" + id).getBody(), "$.estado");
    }

    @Test
    void laBoletaPasaDePendienteDeResumenAEnResumenYAAceptadaSinTocarLaSunatAlEmitir() {
        // 1. Emitir no habla con la SUNAT
        ResponseEntity<String> creada = post("/api/v1/boletas", BOLETA.formatted("venta-1"));
        assertThat(creada.getStatusCode().value()).isEqualTo(201);
        int id = JsonPath.read(creada.getBody(), "$.id");
        assertThat(JsonPath.<String>read(creada.getBody(), "$.numero")).isEqualTo("B001-1");
        assertThat(JsonPath.<String>read(creada.getBody(), "$.estado")).isEqualTo("PENDIENTE_DE_RESUMEN");
        assertThat(cuerpos).isEmpty();

        // 2. Repetir el pedido devuelve la misma boleta
        ResponseEntity<String> repetida = post("/api/v1/boletas", BOLETA.formatted("venta-1"));
        assertThat(repetida.getStatusCode().value()).isEqualTo(200);
        assertThat((int) JsonPath.read(repetida.getBody(), "$.id")).isEqualTo(id);

        // 3. Armar y enviar el resumen ahora
        ResponseEntity<String> generado = post("/api/v1/resumenes/generar", null);
        assertThat(generado.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonPath.<String>read(generado.getBody(), "$[0].nombre")).isEqualTo("RC-20261028-1");
        assertThat(JsonPath.<String>read(generado.getBody(), "$[0].estado")).isEqualTo("ENVIADO");
        assertThat(JsonPath.<String>read(generado.getBody(), "$[0].ticket")).isEqualTo("202610280001");
        int idResumen = JsonPath.read(generado.getBody(), "$[0].id");
        assertThat(estadoDeLaBoleta(id)).isEqualTo("EN_RESUMEN");

        // 4. Lo que llegó a la SUNAT: un sendSummary con el nombre de archivo de la SUNAT, usuario SOL y un ZIP con el resumen firmado y válido
        String envio = cuerpos.get(0);
        String nombreZip = propiedades.getRuc() + "-RC-20261028-1.zip";
        assertThat(envio).contains("<fileName>" + nombreZip + "</fileName>")
                .contains("<wsse:Username>" + propiedades.getRuc() + propiedades.getUsuario() + "</wsse:Username>");
        Matcher contenido = Pattern.compile("<contentFile>(.*?)</contentFile>").matcher(envio);
        assertThat(contenido.find()).isTrue();
        ZipUtil.Entrada entrada = ZipUtil.leerXmlUnico(Base64.getDecoder().decode(contenido.group(1)));
        assertThat(entrada.nombre()).isEqualTo(propiedades.getRuc() + "-RC-20261028-1.xml");
        assertThat(verifica(entrada.contenido())).isTrue();
        assertThat(new XsdValidator().validarResumen(entrada.contenido())).isEmpty();
        assertThat(new String(entrada.contenido(), StandardCharsets.ISO_8859_1)).contains("B001-1");

        // 5. Consultar el ticket antes de tiempo no hace nada; a su hora la SUNAT dice "en proceso" y luego "terminado"
        assertThat(JsonPath.<String>read(get("/api/v1/resumenes/" + idResumen).getBody(), "$.estado")).isEqualTo("ENVIADO");
        reloj.avanzar(Duration.ofMinutes(1));
        consultar();
        assertThat(JsonPath.<String>read(get("/api/v1/resumenes/" + idResumen).getBody(), "$.estado")).isEqualTo("ENVIADO");
        reloj.avanzar(Duration.ofMinutes(1));
        consultar();

        // 6. Resumen y boleta aceptados
        String resumen = get("/api/v1/resumenes/" + idResumen).getBody();
        assertThat(JsonPath.<String>read(resumen, "$.estado")).isEqualTo("ACEPTADO");
        assertThat(JsonPath.<Integer>read(resumen, "$.cantidadBoletas")).isEqualTo(1);
        ResponseEntity<String> boleta = get("/api/v1/boletas/" + id);
        assertThat(JsonPath.<String>read(boleta.getBody(), "$.estado")).isEqualTo("ACEPTADO");
        assertThat(JsonPath.<String>read(boleta.getBody(), "$.codigoSunat")).isEqualTo("0");

        // 7. El XML de la boleta se descarga con su nombre y la factura no la ve
        ResponseEntity<String> xml = get("/api/v1/boletas/" + id + "/xml");
        assertThat(xml.getStatusCode().value()).isEqualTo(200);
        assertThat(xml.getHeaders().getFirst("Content-Disposition")).contains(propiedades.getRuc() + "-03-B001-1.xml");
        assertThat(get("/api/v1/facturas/" + id).getStatusCode().value()).isEqualTo(404);

        // 8. Un resumen aceptado no se reenvía
        assertThat(post("/api/v1/resumenes/" + idResumen + "/reenviar", null).getStatusCode().value()).isEqualTo(409);
    }

    // El planificador de consulta hace esto solo; aquí se llama al servicio porque los planificadores están apagados en los tests
    @Autowired
    private apisunat.api_sunat.service.ResumenDiarioService resumenDiarioService;

    private void consultar() {
        resumenDiarioService.procesarPendientes();
    }

    @Test
    void unaBoletaAnonimaDeMasDe700SolesNoSeAceptaYNoGastaNumero() {
        String cuerpo = """
                {"idExterno": "grande", "items": [{"descripcion": "TV", "cantidad": 1, "precioUnitario": 900}]}""";

        ResponseEntity<String> r = post("/api/v1/boletas", cuerpo);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(comprobantes.count()).isZero();
        assertThat(JsonPath.<String>read(post("/api/v1/boletas", BOLETA.formatted("v1")).getBody(), "$.numero")).isEqualTo("B001-1");
    }
}
