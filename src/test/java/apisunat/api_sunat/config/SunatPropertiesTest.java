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
        p.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC");
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
                .containsExactlyInAnyOrder("url", "ruc", "usuario", "clave", "conexionTimeoutMs", "lecturaTimeoutMs", "emisor.razonSocial");
    }

    // --- Datos del emisor ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void laRazonSocialDelEmisorEsObligatoria(String razon) {
        SunatProperties p = valida();
        p.getEmisor().setRazonSocial(razon);
        assertThat(camposInvalidos(p)).containsExactly("emisor.razonSocial");
    }

    @Test
    void laRazonSocialYElNombreComercialTienenUnMaximoDe1500() {
        SunatProperties p = valida();
        p.getEmisor().setRazonSocial("R".repeat(1500));
        p.getEmisor().setNombreComercial("N".repeat(1500));
        assertThat(validator.validate(p)).isEmpty();

        p.getEmisor().setRazonSocial("R".repeat(1501));
        p.getEmisor().setNombreComercial("N".repeat(1501));
        assertThat(camposInvalidos(p)).containsExactlyInAnyOrder("emisor.razonSocial", "emisor.nombreComercial");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 100, 200})
    void unaDireccionDeTresADoscientosCaracteresEsValida(int largo) {
        SunatProperties p = valida();
        p.getEmisor().setDireccion("D".repeat(largo));
        assertThat(validator.validate(p)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 201, 500})
    void unaDireccionMuyCortaOMuyLargaFalla(int largo) {
        SunatProperties p = valida();
        p.getEmisor().setDireccion("D".repeat(largo));
        assertThat(camposInvalidos(p)).containsExactly("emisor.direccion");
    }

    @Test
    void laDireccionYElNombreComercialVaciosCuentanComoSinDato() {
        SunatProperties p = valida();
        p.getEmisor().setDireccion("");
        p.getEmisor().setNombreComercial("   ");
        assertThat(validator.validate(p)).isEmpty();
        assertThat(p.getEmisor().getDireccion()).isNull();
        assertThat(p.getEmisor().getNombreComercial()).isNull();
    }

    @Test
    void elCodigoDeEstablecimientoPorDefectoEs0000() {
        assertThat(new SunatProperties().getEmisor().getCodigoEstablecimiento()).isEqualTo("0000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000", "0001", "0123", "9999"})
    void unCodigoDeEstablecimientoDeCuatroDigitosEsValido(String codigo) {
        SunatProperties p = valida();
        p.getEmisor().setCodigoEstablecimiento(codigo);
        assertThat(validator.validate(p)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"000", "00000", "abcd", "00 0", " 000", "000\n"})
    void unCodigoDeEstablecimientoQueNoEsDeCuatroDigitosFalla(String codigo) {
        SunatProperties p = valida();
        p.getEmisor().setCodigoEstablecimiento(codigo);
        assertThat(camposInvalidos(p)).as(String.valueOf(codigo)).containsExactly("emisor.codigoEstablecimiento");
    }

    // --- Serie de las facturas ---

    @Test
    void laSerieDeLasFacturasPorDefectoEsF001() {
        assertThat(new SunatProperties().getSerieFactura()).isEqualTo("F001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"F001", "FA01", "FS21", "FZZZ", "F9A0"})
    void unaSerieDeFacturaValidaPasa(String serie) {
        SunatProperties p = valida();
        p.setSerieFactura(serie);
        assertThat(validator.validate(p)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"B001", "f001", "F00", "F0011", "F 01", "F-01", " F001", "F001\n", "FÑ01"})
    void unaSerieDeFacturaInvalidaFalla(String serie) {
        SunatProperties p = valida();
        p.setSerieFactura(serie);
        assertThat(camposInvalidos(p)).containsExactly("serieFactura");
    }

    // --- Plazo de envío, reintentos y consulta de constancias ---

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 30})
    void unPlazoDeEnvioDeCeroATreintaDiasEsValido(int dias) {
        SunatProperties p = valida();
        p.setPlazoEnvioDias(dias);
        assertThat(validator.validate(p)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 31, 365})
    void unPlazoDeEnvioFueraDeRangoFalla(int dias) {
        SunatProperties p = valida();
        p.setPlazoEnvioDias(dias);
        assertThat(camposInvalidos(p)).containsExactly("plazoEnvioDias");
    }

    @Test
    void losValoresPorDefectoDeLosReintentosYDeLaConsultaSonLosEsperados() {
        SunatProperties p = new SunatProperties();
        assertThat(p.getReintentos().isHabilitado()).isTrue();
        assertThat(p.getReintentos().getIntervaloSegundos()).isEqualTo(60);
        assertThat(p.getReintentos().getLote()).isEqualTo(20);
        assertThat(p.getReintentos().getMinutosMaximoEnviando()).isEqualTo(10);
        assertThat(p.getConsultaCdr().isHabilitada()).isFalse();
        assertThat(p.getConsultaCdr().getUrl()).isEqualTo("https://e-factura.sunat.gob.pe/ol-it-wsconscpegem/billConsultService");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void elIntervaloElLoteYElTiempoEnviandoDebenSerPositivos(int valor) {
        SunatProperties p = valida();
        p.getReintentos().setIntervaloSegundos(valor);
        p.getReintentos().setLote(valor);
        p.getReintentos().setMinutosMaximoEnviando(valor);
        assertThat(camposInvalidos(p)).containsExactlyInAnyOrder("reintentos.intervaloSegundos", "reintentos.lote", "reintentos.minutosMaximoEnviando");
    }

    @Test
    void elLoteNoPuedePasarDe500() {
        SunatProperties p = valida();
        p.getReintentos().setLote(500);
        assertThat(validator.validate(p)).isEmpty();
        p.getReintentos().setLote(501);
        assertThat(camposInvalidos(p)).containsExactly("reintentos.lote");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ftp://x", "e-factura.sunat.gob.pe", "https://"})
    void laUrlDeLaConsultaDeConstanciasDebeSerHttpOHttps(String url) {
        SunatProperties p = valida();
        p.getConsultaCdr().setUrl(url);
        assertThat(camposInvalidos(p)).containsExactly("consultaCdr.url");
    }

    @Test
    void conLaConfiguracionDeSpringLosNuevosValoresSeLeen() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.plazo-envio-dias=3", "sunat.reintentos.habilitado=false",
                "sunat.reintentos.intervalo-segundos=5", "sunat.consulta-cdr.habilitada=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            SunatProperties p = ctx.getBean(SunatProperties.class);
            assertThat(p.getPlazoEnvioDias()).isEqualTo(3);
            assertThat(p.getReintentos().isHabilitado()).isFalse();
            assertThat(p.getReintentos().getIntervaloSegundos()).isEqualTo(5);
            assertThat(p.getConsultaCdr().isHabilitada()).isTrue();
        });
    }

    @Test
    void conUnPlazoNegativoLaAplicacionNoArranca() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.plazo-envio-dias=-1").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("plazoEnvioDias");
        });
    }

    // --- Certificado digital ---

    @Test
    void sinCertificadoConfiguradoLaValidacionPasaYLosDatosSonNulos() {
        SunatProperties p = valida();
        assertThat(validator.validate(p)).isEmpty();
        assertThat(p.getCertificado().getRuta()).isNull();
        assertThat(p.getCertificado().getClave()).isNull();
    }

    @Test
    void conRutaYClaveElCertificadoEsValido() {
        SunatProperties p = valida();
        p.getCertificado().setRuta("/ruta/certificado.p12");
        p.getCertificado().setClave("secreta");
        assertThat(validator.validate(p)).isEmpty();
    }

    @Test
    void laRutaSinClaveOLaClaveSinRutaFallan() {
        SunatProperties soloRuta = valida();
        soloRuta.getCertificado().setRuta("/ruta/certificado.p12");
        assertThat(camposInvalidos(soloRuta)).containsExactly("certificado.rutaYClaveCompletas");

        SunatProperties soloClave = valida();
        soloClave.getCertificado().setClave("secreta");
        assertThat(camposInvalidos(soloClave)).containsExactly("certificado.rutaYClaveCompletas");
    }

    @Test
    void laRutaVaciaOConSoloEspaciosCuentaComoSinRuta() {
        SunatProperties p = valida();
        p.getCertificado().setRuta("   ");
        assertThat(p.getCertificado().getRuta()).isNull();
        p.getCertificado().setClave("secreta");
        assertThat(camposInvalidos(p)).containsExactly("certificado.rutaYClaveCompletas");
    }

    @Test
    void laClaveVaciaCuentaComoSinClavePeroUnEspacioEsUnaClaveReal() {
        SunatProperties p = valida();
        p.getCertificado().setClave("");
        assertThat(p.getCertificado().getClave()).isNull();
        p.getCertificado().setClave(" ");
        assertThat(p.getCertificado().getClave()).isEqualTo(" ");
        p.getCertificado().setRuta("/ruta/certificado.p12");
        assertThat(validator.validate(p)).isEmpty();
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
            "sunat.conexion-timeout-ms=5000", "sunat.lectura-timeout-ms=30000",
            "sunat.emisor.razon-social=EMPRESA DE PRUEBAS SAC"};

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
    void sinLaRazonSocialDelEmisorLaAplicacionNoArranca() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.emisor.razon-social=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("razonSocial");
        });
    }

    @Test
    void losDatosOpcionalesDelEmisorSeLeenOSeQuedanNulos() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.emisor.nombre-comercial=MI TIENDA", "sunat.emisor.direccion=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            SunatProperties p = ctx.getBean(SunatProperties.class);
            assertThat(p.getEmisor().getNombreComercial()).isEqualTo("MI TIENDA");
            assertThat(p.getEmisor().getDireccion()).isNull();
            assertThat(p.getEmisor().getCodigoEstablecimiento()).isEqualTo("0000");
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

    @Test
    void sinCertificadoLaAplicacionArrancaConLosDatosNulos() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.certificado.ruta=", "sunat.certificado.clave=").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(SunatProperties.class).getCertificado().getRuta()).isNull();
            assertThat(ctx.getBean(SunatProperties.class).getCertificado().getClave()).isNull();
        });
    }

    @Test
    void conRutaYClaveDelCertificadoLaAplicacionLosLee() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.certificado.ruta=/ruta/certificado.p12", "sunat.certificado.clave=secreta").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(SunatProperties.class).getCertificado().getRuta()).isEqualTo("/ruta/certificado.p12");
            assertThat(ctx.getBean(SunatProperties.class).getCertificado().getClave()).isEqualTo("secreta");
        });
    }

    @Test
    void conUnaRutaSinClaveLaAplicacionNoArranca() {
        runner.withPropertyValues(BUENAS).withPropertyValues("sunat.certificado.ruta=/ruta/certificado.p12").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("la ruta y la clave juntas");
        });
    }
}
