package apisunat.api_sunat;

import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.ZipUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// El camino completo por HTTP de verdad: POST, base de datos MySQL, firma, ZIP y la SUNAT simulada
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class FacturaPuntaAPuntaTest {

    private static final String CUERPO = """
            {"idExterno": "%s",
             "cliente": {"ruc": "20123456786", "razonSocial": "CLIENTE SAC"},
             "items": [{"descripcion": "Camión ñandú", "cantidad": 2, "precioUnitario": 35.00}]}""";

    @LocalServerPort
    private int puerto;

    @MockitoBean
    private BillServiceClient cliente;

    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    private RestClient http;

    @BeforeEach
    void preparar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
        http = RestClient.builder().baseUrl("http://localhost:" + puerto)
                .defaultStatusHandler(codigo -> true, (peticion, respuesta) -> { }).build();
    }

    private ResponseEntity<String> post(String idExterno) {
        return http.post().uri("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON)
                .body(CUERPO.formatted(idExterno)).retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> get(String ruta) {
        return http.get().uri(ruta).retrieve().toEntity(String.class);
    }

    private static byte[] constancia(String nombreZip, String fixture, String codigo) {
        String numero = nombreZip.substring(nombreZip.indexOf("-01-") + 4, nombreZip.length() - 4);
        String xml = Fixtures.texto(fixture).replace("FA01-981", numero).replace("FT01-982", numero).replace(">2047<", ">" + codigo + "<");
        return ZipUtil.comprimir("R-" + nombreZip.replace(".zip", ".xml"), xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void unaFacturaAceptadaSeCreaSeConsultaYSeDescargaSuXmlFirmado() {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0")).when(cliente).sendBill(anyString(), any());

        ResponseEntity<String> creada = post("pedido-1");

        assertThat(creada.getStatusCode().value()).isEqualTo(201);
        assertThat(JsonPath.<String>read(creada.getBody(), "$.estado")).isEqualTo("ACEPTADO");
        assertThat(JsonPath.<String>read(creada.getBody(), "$.numero")).isEqualTo("F001-1");
        int id = JsonPath.read(creada.getBody(), "$.id");

        ResponseEntity<String> consultada = get("/api/v1/facturas/" + id);
        assertThat(consultada.getStatusCode().value()).isEqualTo(200);
        assertThat(JsonPath.<String>read(consultada.getBody(), "$.estado")).isEqualTo("ACEPTADO");
        assertThat(JsonPath.<String>read(consultada.getBody(), "$.descripcionSunat")).isEqualTo("La Factura numero F001-1, ha sido aceptada");

        ResponseEntity<byte[]> xml = http.get().uri("/api/v1/facturas/" + id + "/xml").retrieve().toEntity(byte[].class);
        assertThat(xml.getStatusCode().value()).isEqualTo(200);
        assertThat(xml.getHeaders().getContentType().toString()).containsIgnoringCase("ISO-8859-1");
        assertThat(xml.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment; filename=\"20100066603-01-F001-1.xml\"");
        assertThat(verifica(xml.getBody())).isTrue();
        assertThat(new String(xml.getBody(), StandardCharsets.ISO_8859_1)).contains("Camión ñandú");
    }

    @Test
    void unaFacturaRechazadaQuedaGuardadaComoRechazadaYElSiguienteNumeroEsElOtro() {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/rechazado.xml", "3270")).when(cliente).sendBill(anyString(), any());
        ResponseEntity<String> rechazada = post("pedido-1");
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0")).when(cliente).sendBill(anyString(), any());
        ResponseEntity<String> aceptada = post("pedido-2");

        assertThat(rechazada.getStatusCode().value()).isEqualTo(201);
        assertThat(JsonPath.<String>read(rechazada.getBody(), "$.estado")).isEqualTo("RECHAZADO");
        assertThat(JsonPath.<String>read(rechazada.getBody(), "$.codigoSunat")).isEqualTo("3270");
        assertThat(JsonPath.<String>read(rechazada.getBody(), "$.numero")).isEqualTo("F001-1");
        assertThat(JsonPath.<String>read(aceptada.getBody(), "$.numero")).isEqualTo("F001-2");
    }

    @Test
    void conLaSunatCaidaLaFacturaQuedaGuardadaYElPostDa202() {
        doThrow(new SunatNoDisponibleException("La SUNAT no respondió a tiempo", true, null)).when(cliente).sendBill(anyString(), any());

        ResponseEntity<String> r = post("pedido-1");

        assertThat(r.getStatusCode().value()).isEqualTo(202);
        assertThat(JsonPath.<String>read(r.getBody(), "$.estado")).isEqualTo("ERROR_TEMPORAL");
        assertThat(JsonPath.<String>read(r.getBody(), "$.numero")).isEqualTo("F001-1");
        int id = JsonPath.read(r.getBody(), "$.id");
        assertThat(JsonPath.<String>read(get("/api/v1/facturas/" + id).getBody(), "$.estado")).isEqualTo("ERROR_TEMPORAL");
        assertThat(get("/api/v1/facturas/" + id + "/xml").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void repetirElPostConElMismoIdExternoDa200ConLaMismaFacturaYNoEnviaOtraVez() {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0")).when(cliente).sendBill(anyString(), any());

        ResponseEntity<String> primera = post("pedido-1");
        ResponseEntity<String> segunda = post("pedido-1");

        assertThat(primera.getStatusCode().value()).isEqualTo(201);
        assertThat(segunda.getStatusCode().value()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(segunda.getBody(), "$.id")).isEqualTo(JsonPath.<Integer>read(primera.getBody(), "$.id"));
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void unaPeticionInvalidaDa400ConUnMensajeYNoGuardaNada() {
        ResponseEntity<String> r = http.post().uri("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON)
                .body("{\"idExterno\": \"p1\", \"cliente\": {\"ruc\": \"123\", \"razonSocial\": \"X\"}, \"items\": []}")
                .retrieve().toEntity(String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(400);
        assertThat(JsonPath.<String>read(r.getBody(), "$.mensaje")).isNotBlank();
        assertThat(comprobantes.count()).isZero();
    }

    @Test
    void unIdQueNoExisteDa404() {
        assertThat(get("/api/v1/facturas/999999").getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/v1/facturas/999999/xml").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void laRespuestaNoExponeElXmlNiLaConstanciaNiLasCredenciales() {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0")).when(cliente).sendBill(anyString(), any());

        String cuerpo = post("pedido-1").getBody();

        assertThat(cuerpo).doesNotContain("xml").doesNotContain("cdr").doesNotContain("MODDATOS").doesNotContain("prueba123")
                .doesNotContain("valido.p12").doesNotContain("Signature");
    }
}
