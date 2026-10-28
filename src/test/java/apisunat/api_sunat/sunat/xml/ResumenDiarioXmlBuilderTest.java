package apisunat.api_sunat.sunat.xml;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.sunat.firma.CertificadoFirma;
import apisunat.api_sunat.sunat.firma.CertificadoLoader;
import apisunat.api_sunat.sunat.firma.XmlSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static apisunat.api_sunat.util.FacturaXmlTestUtil.cantidad;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.conFirmaDeRelleno;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.leer;
import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResumenDiarioXmlBuilderTest {

    private static final LocalDate EMISION = LocalDate.of(2026, 10, 28);
    private static final LocalDate GENERACION = LocalDate.of(2026, 10, 29);
    private static final String NS_CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    private static final XsdValidator VALIDADOR = new XsdValidator();

    private static ResumenDiarioXmlBuilder builder() {
        SunatProperties p = new SunatProperties();
        p.setRuc("20100066603");
        p.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC");
        return new ResumenDiarioXmlBuilder(p);
    }

    private static LineaResumen linea(long n) {
        return new LineaResumen("B001-" + n, "1", "46237547", new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("70.00"));
    }

    private static List<LineaResumen> lineas(int cantidad) {
        List<LineaResumen> l = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            l.add(linea(i));
        }
        return l;
    }

    private static byte[] resumen(int cantidad) {
        return builder().construir("RC-20261028-1", EMISION, GENERACION, lineas(cantidad));
    }

    // XPath por nombre local: así no hace falta declarar los espacios de nombres del resumen
    private static String texto(Document d, String expresion) {
        try {
            String xpath = expresion.startsWith("/") ? expresion : "/*/*[local-name()='" + expresion + "']";
            return javax.xml.xpath.XPathFactory.newInstance().newXPath().evaluate(xpath, d).trim();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // --- Estructura ---

    @Test
    void elRaizYLosDatosDelResumenSonLosDeLaGuia() {
        Document d = leer(resumen(1));

        assertThat(d.getDocumentElement().getLocalName()).isEqualTo("SummaryDocuments");
        assertThat(d.getDocumentElement().getNamespaceURI()).isEqualTo("urn:sunat:names:specification:ubl:peru:schema:xsd:SummaryDocuments-1");
        assertThat(texto(d, "UBLVersionID")).isEqualTo("2.0");
        assertThat(texto(d, "CustomizationID")).isEqualTo("1.1");
        assertThat(d.getElementsByTagNameNS(NS_CBC, "ID").item(0).getTextContent()).isEqualTo("RC-20261028-1");
        assertThat(texto(d, "ReferenceDate")).isEqualTo("2026-10-28");
        assertThat(texto(d, "IssueDate")).isEqualTo("2026-10-29");
    }

    @Test
    void lasLineasLlevanTodoLoQueDiceLaGuia() {
        Document d = leer(resumen(2));

        assertThat(cantidad(d, "//*[local-name()='SummaryDocumentsLine']")).isEqualTo(2);
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='LineID']")).isEqualTo("2");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='DocumentTypeCode']")).isEqualTo("03");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='ID']")).isEqualTo("B001-2");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='AccountingCustomerParty']/*[local-name()='CustomerAssignedAccountID']")).isEqualTo("46237547");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='AccountingCustomerParty']/*[local-name()='AdditionalAccountID']")).isEqualTo("1");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='Status']/*[local-name()='ConditionCode']")).isEqualTo("1");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='TotalAmount']")).isEqualTo("70.00");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='BillingPayment']/*[local-name()='PaidAmount']")).isEqualTo("59.32");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='BillingPayment']/*[local-name()='InstructionID']")).isEqualTo("01");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='TaxTotal']/*[local-name()='TaxAmount']")).isEqualTo("10.68");
        assertThat(texto(d, "//*[local-name()='SummaryDocumentsLine'][2]/*[local-name()='TaxTotal']//*[local-name()='TaxScheme']/*[local-name()='ID']")).isEqualTo("1000");
    }

    @Test
    void losImportesLlevanLaMonedaPen() {
        Document d = leer(resumen(1));
        assertThat(texto(d, "//*[local-name()='TotalAmount']/@currencyID")).isEqualTo("PEN");
        assertThat(texto(d, "//*[local-name()='PaidAmount']/@currencyID")).isEqualTo("PEN");
    }

    @Test
    void elEmisorLlevaSuRucYSuRazonSocial() {
        Document d = leer(resumen(1));
        assertThat(texto(d, "//*[local-name()='AccountingSupplierParty']/*[local-name()='CustomerAssignedAccountID']")).isEqualTo("20100066603");
        assertThat(texto(d, "//*[local-name()='AccountingSupplierParty']/*[local-name()='AdditionalAccountID']")).isEqualTo("6");
        assertThat(texto(d, "//*[local-name()='AccountingSupplierParty']//*[local-name()='RegistrationName']")).isEqualTo("EMPRESA DE PRUEBAS SAC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "4", "6", "7", "A", "B", "C", "D", "E", "F", "G", "-"})
    void todosLosTiposDelCatalogo06SeAceptan(String tipo) {
        LineaResumen l = new LineaResumen("B001-1", tipo, "ABC123", new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("70.00"));
        assertThat(VALIDADOR.validarResumen(builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(l)))).isEmpty();
    }

    @Test
    void elClienteAnonimoVaConGuion() {
        LineaResumen anonima = new LineaResumen("B001-5", "-", "-", new BigDecimal("8.47"), new BigDecimal("1.53"), new BigDecimal("10.00"));
        Document d = leer(builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(anonima)));
        assertThat(texto(d, "//*[local-name()='AccountingCustomerParty']/*[local-name()='CustomerAssignedAccountID']")).isEqualTo("-");
        assertThat(texto(d, "//*[local-name()='AccountingCustomerParty']/*[local-name()='AdditionalAccountID']")).isEqualTo("-");
    }

    // --- Esquema oficial ---

    @Test
    void elResumenCumpleElEsquemaOficialDeLaSunat() {
        assertThat(VALIDADOR.validarResumen(conFirmaDeRelleno(resumen(3)))).isEmpty();
    }

    @Test
    void elEsquema20AdmiteElEspacioDeLaFirmaVacioAUnQueElDeLaFacturaNo() {
        // En UBL 2.0 el contenido de la extensión es opcional; en 2.1 (facturas y boletas) un ExtensionContent vacío no valida
        assertThat(VALIDADOR.validarResumen(resumen(1))).isEmpty();
    }

    @Test
    void conClienteAnonimoYDistintosTiposDeDocumentoCumpleElEsquema() {
        List<LineaResumen> mezcla = List.of(
                new LineaResumen("B001-1", "-", "-", new BigDecimal("8.47"), new BigDecimal("1.53"), new BigDecimal("10.00")),
                new LineaResumen("B001-2", "6", "20123456786", new BigDecimal("84.75"), new BigDecimal("15.25"), new BigDecimal("100.00")),
                new LineaResumen("B001-3", "4", "AB1234567", new BigDecimal("84.75"), new BigDecimal("15.25"), new BigDecimal("100.00")),
                new LineaResumen("B002-99999999", "7", "P12345678901", new BigDecimal("0.85"), new BigDecimal("0.15"), new BigDecimal("1.00")));
        assertThat(VALIDADOR.validarResumen(conFirmaDeRelleno(builder().construir("RC-20261028-3", EMISION, GENERACION, mezcla)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 499, 500})
    void unResumenDeHasta500LineasCumpleElEsquema(int cantidad) {
        assertThat(VALIDADOR.validarResumen(conFirmaDeRelleno(resumen(cantidad)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {501, 1000})
    void masDe500LineasSeRechazan(int cantidad) {
        assertThatThrownBy(() -> resumen(cantidad)).isInstanceOf(IllegalArgumentException.class).hasMessage("Un resumen admite como máximo 500 boletas");
    }

    // --- Firma real ---

    @Test
    void firmadoConElFirmanteRealElResumenVerificaYSigueCumpliendoElEsquema() throws Exception {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
        String ruta = Path.of(getClass().getResource("/certificados/valido.p12").toURI()).toString();
        SunatProperties p = new SunatProperties();
        p.setRuc("20100066603");
        p.getCertificado().setRuta(ruta);
        p.getCertificado().setClave("prueba123");
        CertificadoFirma cert = new CertificadoLoader(p, reloj).cargar();

        byte[] firmado = new XmlSigner().firmar(resumen(5), cert);

        assertThat(verifica(firmado)).isTrue();
        assertThat(VALIDADOR.validarResumen(firmado)).isEmpty();
        assertThat(new String(firmado, StandardCharsets.ISO_8859_1)).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"");
    }

    // --- Datos inválidos ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"RC-2026102-1", "RC-20261028", "RC-20261028-0", "RC-20261028-123456", "RA-20261028-1", "rc-20261028-1", "RC-20261028-01", "RC-20261028-1 "})
    void unNombreInvalidoSeRechaza(String nombre) {
        assertThatThrownBy(() -> builder().construir(nombre, EMISION, GENERACION, lineas(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laFechaDelNombreDebeSerLaDeEmisionDeLasBoletas() {
        assertThatThrownBy(() -> builder().construir("RC-20261029-1", EMISION, GENERACION, lineas(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fecha del nombre");
    }

    @Test
    void laFechaDeGeneracionNoPuedeSerAnteriorALaEmision() {
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, EMISION.minusDays(1), lineas(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no puede ser anterior");
        assertThat(builder().construir("RC-20261028-1", EMISION, EMISION, lineas(1))).isNotEmpty();   // el mismo día sí
    }

    @Test
    void sinFechasFalla() {
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", null, GENERACION, lineas(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, null, lineas(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinBoletasFalla() {
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("El resumen necesita al menos una boleta");
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaBoletaRepetidaFalla() {
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(linea(1), linea(2), linea(1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La boleta B001-1 está repetida en el resumen");
    }

    @ParameterizedTest
    @CsvSource({"F001-1", "B001-0", "B001-012", "b001-1", "B01-1", "B001-123456789", "B001", "''"})
    void unNumeroDeBoletaInvalidoFalla(String numero) {
        LineaResumen l = new LineaResumen(numero, "1", "46237547", new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("70.00"));
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(l)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Boleta 1").hasMessageContaining("número");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "3", "5", "8", "9", "H", "x", "10", "", "-1", "a"})
    void unTipoDeDocumentoDelClienteInvalidoFalla(String tipo) {
        LineaResumen l = new LineaResumen("B001-1", tipo, "46237547", new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("70.00"));
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(l)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tipo de documento");
    }

    @Test
    void unNumeroDeDocumentoDelClienteVacioOMuyLargoOConEspaciosFalla() {
        for (String doc : new String[]{"", "  ", " 123", "123 ", "1".repeat(21)}) {
            LineaResumen l = new LineaResumen("B001-1", "1", doc, new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("70.00"));
            assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(l))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void losImportesNegativosConMasDeDosDecimalesOEnCeroFallan() {
        BigDecimal ok = new BigDecimal("10.00");
        for (LineaResumen l : new LineaResumen[]{
                new LineaResumen("B001-1", "1", "46237547", new BigDecimal("-1.00"), ok, ok),
                new LineaResumen("B001-1", "1", "46237547", new BigDecimal("8.475"), ok, ok),
                new LineaResumen("B001-1", "1", "46237547", new BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                new LineaResumen("B001-1", "1", "46237547", null, ok, ok),
                new LineaResumen("B001-1", "1", "46237547", new BigDecimal("1000000000000.00"), ok, ok)}) {
            assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(l))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void unTotalQueNoCuadraConElValorMasElIgvFalla() {
        LineaResumen l = new LineaResumen("B001-1", "1", "46237547", new BigDecimal("59.32"), new BigDecimal("10.68"), new BigDecimal("75.00"));
        assertThatThrownBy(() -> builder().construir("RC-20261028-1", EMISION, GENERACION, List.of(l)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no coincide");
    }

    @Test
    void elMismoResumenSaleIgualDosVeces() {
        assertThat(resumen(10)).isEqualTo(resumen(10));
    }
}
