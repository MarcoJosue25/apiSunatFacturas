package apisunat.api_sunat;

import apisunat.api_sunat.dto.EstadoTicketResponse;
import apisunat.api_sunat.service.SunatService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// Llaman al servicio BETA de la SUNAT de verdad (necesitan internet). No corren con ./mvnw test, solo con ./mvnw test -Pbeta
@Tag("beta")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SunatBetaRealTest {

    @Autowired
    private SunatService sunatService;

    @LocalServerPort
    private int puerto;

    @Test
    void unTicketQueNoExisteDevuelve0127() {
        EstadoTicketResponse r = sunatService.consultarTicket("201100000011227");

        assertThat(r.getStatusCode()).isEqualTo("0127");
        assertThat(r.getContent()).isEqualTo("El ticket no existe");
    }

    @Test
    void desdeNuestraApiHastaElBetaYDeVuelta() {
        String cuerpo = RestClient.create("http://localhost:" + puerto).get()
                .uri("/api/v1/sunat/tickets/201100000011227")
                .retrieve()
                .body(String.class);

        assertThat(JsonPath.<String>read(cuerpo, "$.statusCode")).isEqualTo("0127");
        assertThat(JsonPath.<String>read(cuerpo, "$.content")).isEqualTo("El ticket no existe");
    }

    @Test
    void variosTicketsDistintosSeConsultanSinProblema() {
        for (String ticket : new String[]{"1", "99999999999", "201100000011227", "1".repeat(30)}) {
            assertThat(sunatService.consultarTicket(ticket).getStatusCode()).isEqualTo("0127");
        }
    }

    @Test
    void laRespuestaDelBetaSeLeeEnUtf8() {
        EstadoTicketResponse r = sunatService.consultarTicket("7");
        assertThat(new String(r.getContent().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(r.getContent());
    }
}
