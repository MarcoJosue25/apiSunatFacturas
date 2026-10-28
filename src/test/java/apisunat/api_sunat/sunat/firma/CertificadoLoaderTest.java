package apisunat.api_sunat.sunat.firma;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.exception.CertificadoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificadoLoaderTest {

    private static final String CLAVE = "prueba123";
    private static final String RUC = "20100066603";
    private static final Instant HOY = Instant.parse("2026-10-28T15:00:00Z");

    @TempDir
    Path temporal;

    private static Clock reloj(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    private static String ruta(String archivo) {
        try {
            return Path.of(CertificadoLoaderTest.class.getResource("/certificados/" + archivo).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private CertificadoLoader loader(Instant ahora) {
        return new CertificadoLoader(new SunatProperties(), reloj(ahora));
    }

    private CertificadoFirma cargar(String archivo) {
        return loader(HOY).cargar(ruta(archivo), CLAVE.toCharArray(), RUC);
    }

    // --- Caso feliz ---

    @Test
    void cargaUnCertificadoValidoConSuClavePrivadaYSuRuc() {
        CertificadoFirma firma = cargar("valido.p12");

        assertThat(firma.ruc()).isEqualTo(RUC);
        assertThat(firma.certificado().getSubjectX500Principal().getName()).contains("OU=20100066603");
        assertThat(firma.clavePrivada().getAlgorithm()).isEqualTo("RSA");
        assertThat(((RSAKey) firma.clavePrivada()).getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    void laClavePrivadaEsLaParejaDelCertificado() throws Exception {
        CertificadoFirma firma = cargar("valido.p12");
        byte[] datos = "hola".getBytes();

        Signature firmante = Signature.getInstance("SHA256withRSA");
        firmante.initSign(firma.clavePrivada());
        firmante.update(datos);
        byte[] resultado = firmante.sign();

        Signature verificador = Signature.getInstance("SHA256withRSA");
        verificador.initVerify(firma.certificado());
        verificador.update(datos);
        assertThat(verificador.verify(resultado)).isTrue();
    }

    @Test
    void leElRucAunqueVengaConTextoYDespuesDeOtraUnidadOrganizativa() {
        assertThat(cargar("ruc-con-texto.p12").ruc()).isEqualTo(RUC);
    }

    @Test
    void aceptaUnaClaveRsaDe1024BitsQueEsElMinimoDeLaSunat() {
        assertThat(((RSAKey) cargar("rsa-1024.p12").clavePrivada()).getModulus().bitLength()).isEqualTo(1024);
    }

    // --- El certificado configurado ---

    private SunatProperties propiedades(String ruta, String clave, String ruc) {
        SunatProperties p = new SunatProperties();
        p.setRuc(ruc);
        p.getCertificado().setRuta(ruta);
        p.getCertificado().setClave(clave);
        return p;
    }

    @Test
    void cargarUsaLaRutaYLaClaveDeLaConfiguracion() {
        CertificadoLoader loader = new CertificadoLoader(propiedades(ruta("valido.p12"), CLAVE, RUC), reloj(HOY));
        assertThat(loader.cargar().ruc()).isEqualTo(RUC);
    }

    @Test
    void cargarComparaConElRucDeLaConfiguracion() {
        CertificadoLoader loader = new CertificadoLoader(propiedades(ruta("valido.p12"), CLAVE, "20512345678"), reloj(HOY));
        assertThatThrownBy(loader::cargar)
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("El certificado es del RUC 20100066603 y el emisor es el RUC 20512345678");
    }

    @Test
    void sinCertificadoConfiguradoElMensajeDiceQuePropiedadesFaltan() {
        CertificadoLoader loader = new CertificadoLoader(propiedades(null, null, RUC), reloj(HOY));
        assertThatThrownBy(loader::cargar)
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("sunat.certificado.ruta")
                .hasMessageContaining("sunat.certificado.clave");
    }

    @Test
    void cadaLlamadaLeeElArchivoDeNuevoYUnCertificadoRenovadoSeUsaSinReiniciar() throws Exception {
        Path destino = temporal.resolve("actual.p12");
        Files.copy(Path.of(ruta("vencido.p12")), destino);
        CertificadoLoader loader = new CertificadoLoader(propiedades(destino.toString(), CLAVE, RUC), reloj(HOY));
        assertThatThrownBy(loader::cargar).hasMessageContaining("venció");

        Files.copy(Path.of(ruta("valido.p12")), destino, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThat(loader.cargar().ruc()).isEqualTo(RUC);
    }

    // --- Archivo ---

    @Test
    void unArchivoQueNoExisteDaUnMensajeClaro() {
        String inexistente = temporal.resolve("no-esta.p12").toString();
        assertThatThrownBy(() -> loader(HOY).cargar(inexistente, CLAVE.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("No se encontró el archivo del certificado: " + inexistente);
    }

    @Test
    void unaCarpetaNoEsUnCertificado() {
        assertThatThrownBy(() -> loader(HOY).cargar(temporal.toString(), CLAVE.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("No se encontró el archivo del certificado");
    }

    @Test
    void unaRutaConCaracteresInvalidosDaUnMensajeClaro() {
        assertThatThrownBy(() -> loader(HOY).cargar("certificado\0.p12", CLAVE.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("La ruta del certificado no es válida");
    }

    @Test
    void unArchivoQueNoEsUnP12EstaDanado() {
        assertThatThrownBy(() -> cargar("corrupto.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("no es un certificado .p12 válido o está dañado");
    }

    @Test
    void unArchivoVacioEstaDanado() throws Exception {
        Path vacio = Files.createFile(temporal.resolve("vacio.p12"));
        assertThatThrownBy(() -> loader(HOY).cargar(vacio.toString(), CLAVE.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("no es un certificado .p12 válido o está dañado");
    }

    @Test
    void unArchivoP12CortadoALaMitadEstaDanado() throws Exception {
        byte[] completo = Files.readAllBytes(Path.of(ruta("valido.p12")));
        Path cortado = temporal.resolve("cortado.p12");
        Files.write(cortado, java.util.Arrays.copyOf(completo, completo.length / 2));
        assertThatThrownBy(() -> loader(HOY).cargar(cortado.toString(), CLAVE.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("dañado");
    }

    // --- Clave ---

    @ParameterizedTest
    @ValueSource(strings = {"otraClave", "PRUEBA123", "prueba123 ", " prueba123", "x"})
    void conUnaClaveIncorrectaFallaSinDecirLaClave(String equivocada) {
        assertThatThrownBy(() -> loader(HOY).cargar(ruta("valido.p12"), equivocada.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("La clave del certificado es incorrecta")
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(equivocada.strip());
                    assertThat(e.getMessage()).doesNotContain(CLAVE);
                });
    }

    @Test
    void conUnaClaveVaciaFallaComoClaveIncorrecta() {
        assertThatThrownBy(() -> loader(HOY).cargar(ruta("valido.p12"), new char[0], RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("La clave del certificado es incorrecta");
    }

    // --- Contenido del almacén ---

    @Test
    void unAlmacenSoloConUnCertificadoDeConfianzaNoTieneClavePrivada() throws Exception {
        KeyStore origen = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(Path.of(ruta("valido.p12")))) {
            origen.load(in, CLAVE.toCharArray());
        }
        KeyStore soloCertificado = KeyStore.getInstance("PKCS12");
        soloCertificado.load(null, null);
        soloCertificado.setCertificateEntry("emisor", origen.getCertificate("emisor"));
        Path archivo = temporal.resolve("solo-certificado.p12");
        try (OutputStream out = Files.newOutputStream(archivo)) {
            soloCertificado.store(out, CLAVE.toCharArray());
        }

        assertThatThrownBy(() -> loader(HOY).cargar(archivo.toString(), CLAVE.toCharArray(), RUC))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("no contiene una clave privada para firmar");
    }

    @Test
    void unaClaveEcNoSeAcepta() {
        assertThatThrownBy(() -> cargar("ec.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("La clave del certificado es EC y la SUNAT solo acepta RSA");
    }

    @Test
    void unaClaveRsaDe512BitsNoSeAcepta() {
        assertThatThrownBy(() -> cargar("rsa-512.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("La clave RSA del certificado tiene 512 bits y la SUNAT exige al menos 1024");
    }

    // --- Vigencia (con reloj fijo) ---

    @Test
    void unCertificadoVencidoSeRechazaConLaFechaDeVencimiento() {
        assertThatThrownBy(() -> cargar("vencido.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("El certificado venció el 2020-01-31T00:00:00Z");
    }

    @Test
    void unCertificadoQueTodaviaNoEmpiezaSeRechazaConLaFechaDeInicio() {
        assertThatThrownBy(() -> cargar("futuro.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("El certificado todavía no está vigente: empieza el 2040-01-01T00:00:00Z");
    }

    @Test
    void laVigenciaIncluyeLosDosExtremosYSeMideConElRelojInyectado() {
        Instant inicio = Instant.parse("2026-01-01T00:00:00Z");
        Instant fin = Instant.parse("2035-12-30T00:00:00Z");

        assertThat(loader(inicio).cargar(ruta("valido.p12"), CLAVE.toCharArray(), RUC).ruc()).isEqualTo(RUC);
        assertThat(loader(fin).cargar(ruta("valido.p12"), CLAVE.toCharArray(), RUC).ruc()).isEqualTo(RUC);

        assertThatThrownBy(() -> loader(inicio.minusSeconds(1)).cargar(ruta("valido.p12"), CLAVE.toCharArray(), RUC))
                .hasMessageContaining("todavía no está vigente");
        assertThatThrownBy(() -> loader(fin.plusSeconds(1)).cargar(ruta("valido.p12"), CLAVE.toCharArray(), RUC))
                .hasMessageContaining("venció");
    }

    // --- RUC ---

    @Test
    void unCertificadoDeOtroRucSeDetecta() {
        assertThatThrownBy(() -> cargar("otro-ruc.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessage("El certificado es del RUC 20512345678 y el emisor es el RUC 20100066603");
    }

    @Test
    void unCertificadoSinRucEnLaUnidadOrganizativaSeRechaza() {
        assertThatThrownBy(() -> cargar("sin-ruc.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("no trae el RUC en la unidad organizativa (OU)");
    }

    @Test
    void unNumeroDeDoceDigitosNoSeTomaComoRuc() {
        assertThatThrownBy(() -> cargar("ruc-de-doce-digitos.p12"))
                .isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("no trae el RUC en la unidad organizativa (OU)");
    }
}
