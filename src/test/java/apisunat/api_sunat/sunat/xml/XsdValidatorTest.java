package apisunat.api_sunat.sunat.xml;

import apisunat.api_sunat.util.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class XsdValidatorTest {

    private static final XsdValidator validador = new XsdValidator();
    private static String valida;

    @BeforeAll
    static void leerFixture() {
        valida = Fixtures.texto("xml/factura-minima-valida.xml");
    }

    private static List<String> validar(String xml) {
        return validador.validarFactura(xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String quitar(String xml, String trozo) {
        assertThat(xml).contains(trozo);
        return xml.replace(trozo, "");
    }

    // --- El documento bueno ---

    @Test
    void laFacturaMinimaDeReferenciaEsValida() {
        assertThat(validar(valida)).isEmpty();
    }

    @Test
    void validarVariasVecesDaSiempreElMismoResultado() {
        for (int i = 0; i < 20; i++) {
            assertThat(validar(valida)).isEmpty();
        }
    }

    @Test
    void variosHilosAlMismoTiempoNoSeEstorban() throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<String>>> resultados = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String xml = i % 2 == 0 ? valida : quitar(valida, "<cbc:ID>F001-1</cbc:ID>");
                resultados.add(hilos.submit(() -> validar(xml)));
            }
            for (int i = 0; i < 40; i++) {
                List<String> errores = resultados.get(i).get();
                if (i % 2 == 0) {
                    assertThat(errores).as("documento válido " + i).isEmpty();
                } else {
                    assertThat(errores).as("documento sin ID " + i).isNotEmpty();
                }
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    // --- Errores de contenido ---

    @Test
    void siFaltaElIdDeLaFacturaLoDice() {
        List<String> errores = validar(quitar(valida, "<cbc:ID>F001-1</cbc:ID>"));
        assertThat(errores).isNotEmpty();
        assertThat(errores.get(0)).contains("IssueDate").contains("línea");
    }

    @Test
    void siFaltaElTotalAPagarLoDice() {
        List<String> errores = validar(quitar(valida, "<cbc:PayableAmount currencyID=\"PEN\">70.00</cbc:PayableAmount>"));
        assertThat(errores).isNotEmpty();
    }

    @Test
    void siNoHayNingunaLineaLoDice() {
        int desde = valida.indexOf("<cac:InvoiceLine>");
        int hasta = valida.indexOf("</cac:InvoiceLine>") + "</cac:InvoiceLine>".length();
        assertThat(validar(valida.substring(0, desde) + valida.substring(hasta))).isNotEmpty();
    }

    @Test
    void elOrdenDeLosElementosImportaEnUbl() {
        String cambiado = valida.replace("<cbc:IssueDate>2026-10-28</cbc:IssueDate>\n  <cbc:IssueTime>10:30:00</cbc:IssueTime>",
                "<cbc:IssueTime>10:30:00</cbc:IssueTime>\n  <cbc:IssueDate>2026-10-28</cbc:IssueDate>");
        assertThat(cambiado).isNotEqualTo(valida);
        assertThat(validar(cambiado)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-45", "28/10/2026", "2026-10-32", "hoy", "2026-1-1", ""})
    void unaFechaMalEscritaSeRechaza(String fecha) {
        assertThat(validar(valida.replace("2026-10-28", fecha))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "70,00", "7 0", "", "1e"})
    void unMontoQueNoEsUnNumeroSeRechaza(String monto) {
        assertThat(validar(valida.replace(">70.00</cbc:PayableAmount>", ">" + monto + "</cbc:PayableAmount>"))).isNotEmpty();
    }

    @Test
    void unaMonedaFaltanteEnUnMontoSeRechaza() {
        assertThat(validar(valida.replace("<cbc:PayableAmount currencyID=\"PEN\">", "<cbc:PayableAmount>"))).isNotEmpty();
    }

    @Test
    void unElementoDesconocidoDentroDeLaFacturaSeRechaza() {
        assertThat(validar(valida.replace("<cbc:UBLVersionID>", "<cbc:Inventado>x</cbc:Inventado><cbc:UBLVersionID>"))).isNotEmpty();
    }

    @Test
    void laFirmaMalFormadaDentroDelExtensionContentTambienSeDetecta() {
        assertThat(validar(quitar(valida, "<ds:SignatureValue>AAAA</ds:SignatureValue>"))).isNotEmpty();
    }

    @Test
    void unExtensionContentVacioSeRechazaPorqueElDocumentoDebeEstarFirmado() {
        int desde = valida.indexOf("<ext:ExtensionContent>");
        int hasta = valida.indexOf("</ext:ExtensionContent>") + "</ext:ExtensionContent>".length();
        List<String> errores = validar(valida.substring(0, desde) + "<ext:ExtensionContent/>" + valida.substring(hasta));
        assertThat(errores).isNotEmpty();
        assertThat(errores.get(0)).contains("ExtensionContent");
    }

    // --- Raíz y espacio de nombres ---

    @Test
    void unaRaizQueNoEsInvoiceSeRechaza() {
        assertThat(validar(valida.replace("<Invoice ", "<CreditNote ").replace("</Invoice>", "</CreditNote>"))).isNotEmpty();
    }

    @Test
    void unEspacioDeNombresEquivocadoSeRechaza() {
        assertThat(validar(valida.replace("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", "urn:otro:espacio"))).isNotEmpty();
    }

    // --- Lo que ni siquiera es XML ---

    @Test
    void sinBytesDiceQueEstaVacio() {
        assertThat(validador.validarFactura(null)).containsExactly("El XML está vacío");
        assertThat(validador.validarFactura(new byte[0])).containsExactly("El XML está vacío");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"hola", "<html><body>502 Bad Gateway</body></html>", "<Invoice>", "{\"a\":1}", "<?xml version=\"1.0\"?>", "<a/><b/>"})
    void loQueNoEsXmlBienFormadoDaUnErrorYNoUnaExcepcion(String basura) {
        List<String> errores = validar(basura == null ? "" : basura);
        assertThat(errores).isNotEmpty();
    }

    // --- Codificación: declarada y real tienen que coincidir ---

    @Test
    void lasEniesYTildesEnIso88591DeclaradoSonValidas() {
        String conTildes = valida.replace("Polo basico negro", "Polo básico negro con ñandú, camión y señal");
        assertThat(validador.validarFactura(conTildes.getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
    }

    @Test
    void siSeDeclaraUtf8PeroLosBytesSonLatin1ElXmlEsInvalido() {
        String conTildes = valida.replace("Polo basico negro", "Polo básico ñandú").replace("encoding=\"ISO-8859-1\"", "encoding=\"UTF-8\"");
        assertThat(validador.validarFactura(conTildes.getBytes(StandardCharsets.ISO_8859_1))).isNotEmpty();
    }

    @Test
    void siSeDeclaraLatin1PeroLosBytesSonUtf8ElXmlSigueSiendoValidoPeroConLosCaracteresMalLeidos() {
        // Latin-1 acepta cualquier byte: no hay error de lectura, lo que cambia es el texto. Por eso el armador debe escribir
        // los bytes con la misma codificación que declara (se prueba en FacturaXmlBuilderTest).
        String conTildes = valida.replace("Polo basico negro", "Polo básico ñandú");
        assertThat(validador.validarFactura(conTildes.getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    // --- Seguridad ---

    @Test
    void unaEntidadExternaNoSeResuelveNiLeeArchivosDelServidor() {
        String malicioso = valida.replace("<Invoice ", "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><Invoice ")
                .replace("Polo basico negro", "&e;");
        List<String> errores = validar(malicioso);
        assertThat(errores).isNotEmpty();
        assertThat(String.join(" ", errores)).doesNotContain("root:");
    }

    @Test
    void sinImportarCuantosErroresHayDevuelveComoMaximoCincuenta() {
        StringBuilder muchas = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            muchas.append("<cac:InvoiceLine><cbc:Basura/></cac:InvoiceLine>");
        }
        List<String> errores = validar(valida.replace("</Invoice>", muchas + "</Invoice>"));
        assertThat(errores).isNotEmpty().hasSizeLessThanOrEqualTo(50);
    }
}
