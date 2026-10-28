package apisunat.api_sunat;

import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.util.ZipUtil;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

// De punta a punta con todo real: nuestra API por HTTP, MySQL, la firma y el servicio BETA de la SUNAT (solo con -Pbeta)
@Tag("beta")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class SunatBetaApiRealTest {

    @LocalServerPort
    private int puerto;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    @Test
    void unPostRealQuedaAceptadoPorElBetaYGuardadoConSuConstancia() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
        RestClient http = RestClient.create("http://localhost:" + puerto);
        String idExterno = "beta-" + System.currentTimeMillis();

        ResponseEntity<String> r = http.post().uri("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"idExterno": "%s",
                         "cliente": {"ruc": "20123456786", "razonSocial": "CLIENTE DE PRUEBA SAC"},
                         "items": [{"descripcion": "Polo básico negro", "cantidad": 2, "precioUnitario": 35.00},
                                   {"descripcion": "Servicio de delivery", "cantidad": 1, "precioUnitario": 5.00, "unidadMedida": "ZZ"}]}"""
                        .formatted(idExterno))
                .retrieve().toEntity(String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(201);
        assertThat(JsonPath.<String>read(r.getBody(), "$.estado")).isEqualTo("ACEPTADO");
        assertThat(JsonPath.<String>read(r.getBody(), "$.numero")).isEqualTo("F001-1");
        assertThat(JsonPath.<String>read(r.getBody(), "$.descripcionSunat")).contains("ha sido aceptada");
        assertThat((Object) JsonPath.read(r.getBody(), "$.importeTotal")).isEqualTo(75.0);

        var guardado = comprobantes.findByIdExterno(idExterno).orElseThrow();
        assertThat(ZipUtil.leerXmlUnico(guardado.getCdrZip()).nombre()).startsWith("R-20100066603-01-F001-1");
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(guardado.getId())).hasSize(1);
    }
}
