package apisunat.api_sunat.config;

import apisunat.api_sunat.ApiSunatApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// La aplicación de verdad arrancando con el perfil prod: con la configuración por defecto (que apunta al beta) no arranca
class PerfilProduccionArranqueTest {

    private static final String LLAVE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    // Los argumentos de la línea de comandos pesan más que application.yml; las propiedades por defecto del builder pesan menos
    private ConfigurableApplicationContext arrancar(String... propiedades) {
        return new SpringApplicationBuilder(ApiSunatApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod")
                .run(Stream.concat(Stream.of("sunat.reintentos.habilitado=false", "sunat.resumen.habilitado=false"), Stream.of(propiedades))
                        .map(p -> "--" + p).toArray(String[]::new));
    }

    @Test
    void conLaConfiguracionPorDefectoQueApuntaAlBetaNoArranca() {
        assertThatThrownBy(() -> arrancar())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil prod")
                .hasMessageContaining("servicio de pruebas (beta)")
                .hasMessageContaining("MODDATOS")
                .hasMessageContaining("Falta la llave de la API");
    }

    @Test
    void conUnCertificadoDePruebaAutofirmadoNoArranca() {
        assertThatThrownBy(() -> arrancar(
                "sunat.url=https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService", "sunat.ruc=20100066603", "sunat.usuario=SECUNDARIO",
                "sunat.clave=claveReal1", "sunat.emisor.razon-social=MI EMPRESA SAC", "sunat.api-key=" + LLAVE,
                "sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123")
        ).rootCause().hasMessageContaining("autofirmado");
    }

    @Test
    void conTodoEnProduccionArrancaYCargaElValidador() {
        try (ConfigurableApplicationContext contexto = arrancar(
                "sunat.url=https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService", "sunat.ruc=20512345678", "sunat.usuario=SECUNDARIO",
                "sunat.clave=claveReal1", "sunat.emisor.razon-social=MI EMPRESA SAC", "sunat.api-key=" + LLAVE,
                "sunat.certificado.ruta=src/test/resources/certificados/emitido-por-ca.p12", "sunat.certificado.clave=prueba123")) {
            assertThat(contexto.getBeansOfType(ValidadorProduccion.class)).hasSize(1);
        }
    }

    @Test
    void sinElPerfilProdElValidadorNoExisteYSeArrancaConLoDePruebas() {
        try (ConfigurableApplicationContext contexto = new SpringApplicationBuilder(ApiSunatApplication.class).web(WebApplicationType.NONE)
                .properties("sunat.reintentos.habilitado=false", "sunat.resumen.habilitado=false").run()) {
            assertThat(contexto.getBeansOfType(ValidadorProduccion.class)).isEmpty();
        }
    }
}
