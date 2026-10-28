package apisunat.api_sunat;

import org.junit.jupiter.api.Test;
import apisunat.api_sunat.config.SunatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiSunatApplicationTests {

    @Value("${spring.application.name}")
    private String nombreApp;

    @Value("${server.port}")
    private int puerto;

    @Autowired
    private SunatProperties sunat;

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

    // Sin variables de entorno la aplicación apunta al beta con las credenciales públicas de pruebas
    @Test
    void porDefectoApuntaAlBetaDeLaSunat() {
        assertThat(sunat.getUrl()).startsWith("https://e-beta.sunat.gob.pe/");
        assertThat(sunat.getUsuario()).isEqualTo("MODDATOS");
        assertThat(sunat.getClave()).isEqualTo("MODDATOS");
        assertThat(sunat.getRuc()).hasSize(11);
    }

    @Test
    void porDefectoElEmisorEsElDePruebas() {
        assertThat(sunat.getEmisor().getRazonSocial()).isEqualTo("EMPRESA DE PRUEBAS SAC");
        assertThat(sunat.getEmisor().getNombreComercial()).isNull();
        assertThat(sunat.getEmisor().getDireccion()).isNull();
        assertThat(sunat.getEmisor().getCodigoEstablecimiento()).isEqualTo("0000");
    }
}
