package apisunat.api_sunat.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SunatPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SunatProperties valida() {
        SunatProperties p = new SunatProperties();
        p.setUrl("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService");
        p.setRuc("20100066603");
        p.setUsuario("MODDATOS");
        p.setClave("MODDATOS");
        p.setConexionTimeoutMs(5000);
        p.setLecturaTimeoutMs(30000);
        return p;
    }

    private Set<String> camposInvalidos(SunatProperties p) {
        return validator.validate(p).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void unaConfiguracionCorrectaNoTieneErrores() {
        assertThat(validator.validate(valida())).isEmpty();
    }

    @Test
    void aceptaUnaUrlLocalConHttpParaLosTests() {
        SunatProperties p = valida();
        p.setUrl("http://localhost:8089/billService");
        assertThat(validator.validate(p)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "1234567890", "123456789012", "2010006660a", " 20100066603", "20100066603 ", "2010006660-"})
    void elRucDebeTenerExactamenteOnceDigitos(String ruc) {
        SunatProperties p = valida();
        p.setRuc(ruc);
        assertThat(camposInvalidos(p)).containsExactly("ruc");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ftp://sunat.gob.pe", "e-beta.sunat.gob.pe", "https://", "https://con espacio.pe"})
    void laUrlDebeSerHttpOHttps(String url) {
        SunatProperties p = valida();
        p.setUrl(url);
        assertThat(camposInvalidos(p)).contains("url");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void elUsuarioNoPuedeEstarVacio(String usuario) {
        SunatProperties p = valida();
        p.setUsuario(usuario);
        assertThat(camposInvalidos(p)).containsExactly("usuario");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void laClaveNoPuedeEstarVacia(String clave) {
        SunatProperties p = valida();
        p.setClave(clave);
        assertThat(camposInvalidos(p)).containsExactly("clave");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5000})
    void losTimeoutsDebenSerMayoresQueCero(int valor) {
        SunatProperties p = valida();
        p.setConexionTimeoutMs(valor);
        p.setLecturaTimeoutMs(valor);
        assertThat(camposInvalidos(p)).containsExactlyInAnyOrder("conexionTimeoutMs", "lecturaTimeoutMs");
    }

    @Test
    void sinPropiedadesNingunCampoPasaLaValidacion() {
        assertThat(camposInvalidos(new SunatProperties()))
                .containsExactlyInAnyOrder("url", "ruc", "usuario", "clave", "conexionTimeoutMs", "lecturaTimeoutMs");
    }

    // --- Con el arranque de Spring de verdad: una configuración mala impide que la aplicación arranque ---

    @Configuration
    @EnableConfigurationProperties(SunatProperties.class)
    static class ConfigDePrueba {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ConfigDePrueba.class);

    private static final String[] BUENAS = {
            "sunat.url=https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService",
            "sunat.ruc=20100066603", "sunat.usuario=MODDATOS", "sunat.clave=MODDATOS",
            "sunat.conexion-timeout-ms=5000", "sunat.lectura-timeout-ms=30000"};

    @Test
    void conPropiedadesBuenasLaAplicacionArrancaYLasLee() {
        runner.withPropertyValues(BUENAS).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            SunatProperties p = ctx.getBean(SunatProperties.class);
            assertThat(p.getRuc()).isEqualTo("20100066603");
            assertThat(p.getConexionTimeoutMs()).isEqualTo(5000);
        });
    }

    @Test
    void conUnRucMaloLaAplicacionNoArranca() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.ruc=123").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("sunat.ruc");
        });
    }

    @Test
    void conUnTimeoutEnCeroLaAplicacionNoArranca() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.lectura-timeout-ms=0").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("lecturaTimeoutMs");
        });
    }

    @Test
    void sinNingunaPropiedadLaAplicacionNoArranca() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }
}
