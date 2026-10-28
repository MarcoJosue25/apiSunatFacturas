package apisunat.api_sunat;

import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.EstadoResumen;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.ResumenDiarioService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

// Una boleta real: POST, resumen diario enviado al BETA de la SUNAT y ticket consultado hasta que termina (solo con -Pbeta).
// Ojo: el número de la boleta (B001-1) y el nombre del resumen (RC-hoy-1) se repiten en cada corrida porque la base se limpia; si el beta
// recuerda una corrida anterior del mismo día puede contestar distinto (ver docs/OBSERVACIONES.md).
@Tag("beta")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class SunatBetaResumenRealTest {

    @LocalServerPort
    private int puerto;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private ResumenDiarioRepository resumenes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;
    @Autowired
    private ResumenDiarioService resumenDiarioService;

    @Test
    void unaBoletaRealQuedaInformadaEnUnResumenQueElBetaProcesa() throws Exception {
        intentos.deleteAll();
        comprobantes.deleteAll();
        resumenes.deleteAll();
        series.deleteAll();
        RestClient http = RestClient.create("http://localhost:" + puerto);

        ResponseEntity<String> boleta = http.post().uri("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"idExterno": "beta-%d",
                         "cliente": {"tipoDocumento": "1", "numeroDocumento": "46237547", "nombre": "LUANA PAZOS"},
                         "items": [{"descripcion": "Polo básico negro", "cantidad": 2, "precioUnitario": 35.00}]}"""
                        .formatted(System.currentTimeMillis()))
                .retrieve().toEntity(String.class);
        assertThat(boleta.getStatusCode().value()).isEqualTo(201);
        int idBoleta = JsonPath.read(boleta.getBody(), "$.id");

        ResponseEntity<String> generado = http.post().uri("/api/v1/resumenes/generar").retrieve().toEntity(String.class);
        assertThat(generado.getStatusCode().value()).isEqualTo(200);
        System.out.println("Resumen generado: " + generado.getBody());
        long idResumen = ((Number) JsonPath.read(generado.getBody(), "$[0].id")).longValue();
        assertThat(JsonPath.<String>read(generado.getBody(), "$[0].estado")).isEqualTo("ENVIADO");

        // Se consulta el ticket (sin esperar el backoff: se adelanta la fecha de la próxima consulta) hasta que el beta termine
        long limite = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
        ResumenDiario r;
        do {
            Thread.sleep(2000);
            r = resumenes.findById(idResumen).orElseThrow();
            if (r.getEstado() == EstadoResumen.ENVIADO) {
                resumenDiarioService.consultar(idResumen);
                r = resumenes.findById(idResumen).orElseThrow();
            }
        } while (r.getEstado() == EstadoResumen.ENVIADO && System.currentTimeMillis() < limite);

        System.out.println("Resumen final: " + r.getEstado() + " " + r.getCodigoSunat() + " " + r.getDescripcionSunat());
        System.out.println("Boleta final: " + http.get().uri("/api/v1/boletas/" + idBoleta).retrieve().body(String.class));
        assertThat(r.getEstado()).isIn(EstadoResumen.ACEPTADO, EstadoResumen.ACEPTADO_CON_OBSERVACIONES);
        assertThat(r.getCdrZip()).isNotEmpty();
        assertThat(JsonPath.<String>read(http.get().uri("/api/v1/boletas/" + idBoleta).retrieve().body(String.class), "$.estado"))
                .isIn("ACEPTADO", "ACEPTADO_CON_OBSERVACIONES");
    }
}
