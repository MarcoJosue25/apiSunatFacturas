package apisunat.api_sunat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiSunatApplicationTests {

    @Value("${spring.application.name}")
    private String nombreApp;

    @Value("${server.port}")
    private int puerto;

    @Test
    void elContextoArranca() {
    }

    @Test
    void laAplicacionSeLlamaApiSunat() {
        assertThat(nombreApp).isEqualTo("api-sunat");
    }

    @Test
    void usaUnPuertoDistintoAlDelAgente() {
        assertThat(puerto).isNotEqualTo(8080);
    }
}
