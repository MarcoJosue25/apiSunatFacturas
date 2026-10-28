package apisunat.api_sunat.seguridad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

// La aplicación completa con la llave puesta: sin llave y con llave incorrecta 401, con la llave correcta pasa
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sunat.api-key=llave-de-prueba-con-un-largo-razonable-0123456789"})
class ApiKeyPuntaAPuntaTest {

    private static final String LLAVE = "llave-de-prueba-con-un-largo-razonable-0123456789";

    @LocalServerPort
    private int puerto;

    private RestClient http;

    @BeforeEach
    void preparar() {
        http = RestClient.builder().baseUrl("http://localhost:" + puerto).defaultStatusHandler(codigo -> true, (p, r) -> { }).build();
    }

    private ResponseEntity<String> get(String ruta, String llave) {
        var peticion = http.get().uri(ruta);
        if (llave != null) {
            peticion.header("X-API-Key", llave);
        }
        return peticion.retrieve().toEntity(String.class);
    }

    @Test
    void sinLlaveTodosLosEndpointsDeLaApiDan401() {
        for (String ruta : new String[]{"/api/v1/facturas/1", "/api/v1/boletas/1", "/api/v1/resumenes/1", "/api/v1/sunat/tickets/1"}) {
            assertThat(get(ruta, null).getStatusCode().value()).as(ruta).isEqualTo(401);
        }
    }

    @Test
    void sinLlaveElPostTambienDa401YNoGuardaNada() {
        ResponseEntity<String> r = http.post().uri("/api/v1/resumenes/generar").retrieve().toEntity(String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void conLlaveIncorrectaDa401() {
        assertThat(get("/api/v1/facturas/1", "otra").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void conLaLlaveCorrectaPasaYLlegaAlControlador() {
        ResponseEntity<String> r = get("/api/v1/facturas/999999", LLAVE);

        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody()).contains("No existe el comprobante 999999");
    }

    @Test
    void conLaLlaveCorrectaUnPostLlegaAlControlador() {
        ResponseEntity<String> r = http.post().uri("/api/v1/resumenes/generar").header("X-API-Key", LLAVE).retrieve().toEntity(String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).isEqualTo("[]");
    }

    @Test
    void elJsonDeUnPostSinLlaveNoSeLee() {
        ResponseEntity<String> r = http.post().uri("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).body("{ esto no es json").retrieve().toEntity(String.class);

        // 401 y no 400: primero se pide la llave, después se mira el cuerpo
        assertThat(r.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void lasRutasQueNoSonDeLaApiNoPidenLlave() {
        // No existen, pero el rechazo es el 404 normal y no el 401 de la llave
        assertThat(get("/algo-que-no-existe", null).getStatusCode().value()).isEqualTo(404);
    }
}
