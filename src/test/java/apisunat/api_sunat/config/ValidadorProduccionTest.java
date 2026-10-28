package apisunat.api_sunat.config;

import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidadorProduccionTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
    private static final String LLAVE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    // Una configuración de producción correcta; cada test rompe una sola cosa
    private static SunatProperties produccion(Consumer<SunatProperties> cambio) {
        SunatProperties p = new SunatProperties();
        p.setUrl("https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService");
        p.setRuc("20512345678");   // el del certificado de prueba emitido por la autoridad de prueba
        p.setUsuario("USUARIOSEC");
        p.setClave("claveSecreta1");
        p.setApiKey(LLAVE);
        p.getEmisor().setRazonSocial("MI EMPRESA SAC");
        p.getCertificado().setRuta("src/test/resources/certificados/emitido-por-ca.p12");
        p.getCertificado().setClave("prueba123");
        cambio.accept(p);
        return p;
    }

    private static List<String> problemas(Consumer<SunatProperties> cambio) {
        SunatProperties p = produccion(cambio);
        return new ValidadorProduccion(p, new CertificadoLoader(p, RELOJ)).problemas();
    }

    @Test
    void unaConfiguracionDeProduccionCorrectaNoTieneProblemas() {
        assertThat(problemas(p -> { })).isEmpty();
    }

    @Test
    void elRucDePruebasSeRechaza() {
        assertThat(problemas(p -> p.setRuc("20100066603"))).anyMatch(m -> m.contains("RUC (SUNAT_RUC) es el de pruebas"));
    }

    @Test
    void laUrlDelBetaSeRechaza() {
        assertThat(problemas(p -> p.setUrl("https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService")))
                .anyMatch(m -> m.contains("servicio de pruebas (beta)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService", "https://otro-servidor.example.com/billService",
            "https://e-factura.sunat.gob.pe.malo.com/x", "http://localhost:8099/billService"})
    void unaUrlQueNoEsLaDeProduccionSeRechaza(String url) {
        assertThat(problemas(p -> p.setUrl(url))).anyMatch(m -> m.contains("no es la de producción"));
    }

    @Test
    void elUsuarioOLaClaveDePruebasSeRechazan() {
        assertThat(problemas(p -> p.setUsuario("MODDATOS"))).anyMatch(m -> m.contains("son los de pruebas"));
        assertThat(problemas(p -> p.setClave("MODDATOS"))).anyMatch(m -> m.contains("son los de pruebas"));
    }

    @Test
    void laRazonSocialDePruebasSeRechaza() {
        assertThat(problemas(p -> p.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC"))).anyMatch(m -> m.contains("razón social"));
    }

    @Test
    void unCertificadoAutofirmadoSeRechaza() {
        // valido.p12 es del RUC de pruebas, así que el RUC del emisor también tiene que serlo para que el certificado cargue
        assertThat(problemas(p -> {
            p.setRuc("20100066603");
            p.getCertificado().setRuta("src/test/resources/certificados/valido.p12");
        })).anyMatch(m -> m.contains("autofirmado"));
    }

    @Test
    void sinCertificadoOConCertificadoMaloSeDiceElMotivo() {
        assertThat(problemas(p -> p.getCertificado().setRuta(null))).anyMatch(m -> m.contains("certificado digital no sirve"));
        assertThat(problemas(p -> p.getCertificado().setClave("otra"))).anyMatch(m -> m.contains("certificado digital no sirve"));
        assertThat(problemas(p -> p.getCertificado().setRuta("src/test/resources/certificados/vencido.p12"))).anyMatch(m -> m.contains("certificado digital no sirve"));
        assertThat(problemas(p -> p.setRuc("20999999999"))).anyMatch(m -> m.contains("certificado digital no sirve"));
    }

    @Test
    void sinLlaveDeLaApiSeRechaza() {
        assertThat(problemas(p -> p.setApiKey(null))).anyMatch(m -> m.contains("Falta la llave de la API"));
        assertThat(problemas(p -> p.setApiKey("  "))).anyMatch(m -> m.contains("Falta la llave de la API"));
    }

    @Test
    void unaLlaveCortaSeRechazaYUnaDe32SePermite() {
        assertThat(problemas(p -> p.setApiKey("a".repeat(31)))).anyMatch(m -> m.contains("muy corta"));
        assertThat(problemas(p -> p.setApiKey("a".repeat(32)))).noneMatch(m -> m.contains("llave"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"con espacios en el medio 0123456789abcdef", "ñandú-ñandú-ñandú-ñandú-ñandú-ñandú-ñandú", "tab\taaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void unaLlaveConCaracteresRarosSeRechaza(String llave) {
        assertThat(problemas(p -> p.setApiKey(llave))).anyMatch(m -> m.contains("ASCII"));
    }

    @Test
    void laConsultaDeConstanciasHabilitadaNecesitaLaUrlDeProduccion() {
        assertThat(problemas(p -> {
            p.getConsultaCdr().setHabilitada(true);
            p.getConsultaCdr().setUrl("http://localhost/x");
        })).anyMatch(m -> m.contains("consulta de constancias"));
        assertThat(problemas(p -> p.getConsultaCdr().setUrl("http://localhost/x"))).noneMatch(m -> m.contains("consulta de constancias"));
    }

    @Test
    void siHayVariosProblemasSeDicenTodosJuntos() {
        List<String> todos = problemas(p -> {
            p.setUrl("https://e-beta.sunat.gob.pe/x");
            p.setUsuario("MODDATOS");
            p.setApiKey(null);
        });
        assertThat(todos).hasSize(3)
                .anyMatch(m -> m.contains("servicio de pruebas (beta)"))
                .anyMatch(m -> m.contains("son los de pruebas"))
                .anyMatch(m -> m.contains("Falta la llave"));
    }

    @Test
    void alArrancarConProblemasFallaYElMensajeNoTraeSecretos() {
        SunatProperties p = produccion(x -> {
            x.setUrl("https://e-beta.sunat.gob.pe/x");
            x.setClave("MODDATOS");
        });
        ValidadorProduccion validador = new ValidadorProduccion(p, new CertificadoLoader(p, RELOJ));

        assertThatThrownBy(validador::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("perfil prod")
                .hasMessageContaining("servicio de pruebas (beta)")
                .message().doesNotContain(LLAVE).doesNotContain("prueba123").doesNotContain("claveSecreta1");
    }

    @Test
    void alArrancarSinProblemasNoLanzaNada() {
        SunatProperties p = produccion(x -> { });

        assertThatCode(() -> new ValidadorProduccion(p, new CertificadoLoader(p, RELOJ)).afterPropertiesSet()).doesNotThrowAnyException();
    }
}
