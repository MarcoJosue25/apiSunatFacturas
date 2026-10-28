package apisunat.api_sunat.sunat.xml;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.ReglasSunatXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static apisunat.api_sunat.util.FacturaXmlTestUtil.cantidad;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.conFirmaDeRelleno;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.leer;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.normalizar;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.valor;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.valores;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FacturaXmlBuilderTest {

    private static final LocalDateTime EMISION = LocalDateTime.of(2026, 10, 28, 10, 30, 0);
    private static final XsdValidator VALIDADOR = new XsdValidator();
    private static final String[] RUCS_VALIDOS = {"10123456781", "10180967929", "10241902710", "10347017464", "10682360131", "10787789329", "10879217429", "10907579382", "10908100331", "15003307660", "15077283789", "15191614612", "15506123108", "15619345140", "15814538408", "16049403261", "16095461119", "16326616633", "16393095591", "16729792628", "16991023741", "17014673008", "17108436468", "17243933285", "17367916010", "17789914338", "17974821231", "20075400919", "20100066603", "20123456786", "20131312955", "20160790963", "20245892153", "20272887790", "20275892905", "20714521170", "20850255173", "20904954901"};

    // --- Armado de datos de prueba ---

    private static SunatProperties propiedades() {
        SunatProperties p = new SunatProperties();
        p.setRuc("20100066603");
        p.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC");
        return p;
    }

    private static ItemRequest item(String descripcion, String cantidad, String precio) {
        return ItemRequest.builder().descripcion(descripcion).cantidad(new BigDecimal(cantidad)).precioUnitario(new BigDecimal(precio)).build();
    }

    private static FacturaRequest factura(ItemRequest... items) {
        return FacturaRequest.builder()
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(Arrays.asList(items)).build();
    }

    private static FacturaRequest facturaBasica() {
        return factura(item("Polo basico negro", "2", "35.00"));
    }

    private static byte[] construir(SunatProperties props, FacturaRequest request) {
        return new FacturaXmlBuilder(props).construir(request, "F001", 1, EMISION);
    }

    private static byte[] construir(FacturaRequest request) {
        return construir(propiedades(), request);
    }

    private static Document doc(byte[] xml) {
        return leer(xml);
    }

    private static List<String> errores(byte[] xml) {
        return VALIDADOR.validarFactura(conFirmaDeRelleno(xml));
    }

    // --- Cumple el esquema oficial y coincide con el XML de referencia ---

    @Test
    void elXmlGeneradoPasaElEsquemaOficialDeLaSunat() {
        assertThat(errores(construir(facturaBasica()))).isEmpty();
    }

    @Test
    void coincideConElXmlDeReferenciaElemetoPorElemento() {
        SunatProperties props = propiedades();
        props.getEmisor().setNombreComercial("MI TIENDA");
        byte[] generado = construir(props, facturaBasica());
        Document referencia = leer(Fixtures.texto("xml/factura-minima-valida.xml"));

        assertThat(normalizar(doc(generado))).isEqualTo(normalizar(referencia));
    }

    @Test
    void elEspacioParaLaFirmaQuedaVacioYElDocumentoApuntaAEl() {
        Document d = doc(construir(facturaBasica()));
        assertThat(cantidad(d, "/inv:Invoice/ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent")).isEqualTo(1);
        assertThat(cantidad(d, "/inv:Invoice/ext:UBLExtensions/ext:UBLExtension/ext:ExtensionContent/*")).isZero();
        assertThat(valor(d, "//cac:Signature/cbc:ID")).isEqualTo("IDSignSP");
        assertThat(valor(d, "//cac:Signature/cac:DigitalSignatureAttachment/cac:ExternalReference/cbc:URI")).isEqualTo("#SignatureSP");
        assertThat(valor(d, "//cac:Signature/cac:SignatoryParty/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");
        assertThat(valor(d, "//cac:Signature/cac:SignatoryParty/cac:PartyName/cbc:Name")).isEqualTo("EMPRESA DE PRUEBAS SAC");
    }

    // --- Cabecera ---

    @Test
    void laCabeceraTieneLosValoresDeLaFactura() {
        Document d = doc(construir(facturaBasica()));
        assertThat(valor(d, "/inv:Invoice/cbc:UBLVersionID")).isEqualTo("2.1");
        assertThat(valor(d, "/inv:Invoice/cbc:CustomizationID")).isEqualTo("2.0");
        assertThat(valor(d, "/inv:Invoice/cbc:ID")).isEqualTo("F001-1");
        assertThat(valor(d, "/inv:Invoice/cbc:IssueDate")).isEqualTo("2026-10-28");
        assertThat(valor(d, "/inv:Invoice/cbc:IssueTime")).isEqualTo("10:30:00");
        assertThat(valor(d, "/inv:Invoice/cbc:DocumentCurrencyCode")).isEqualTo("PEN");
        assertThat(valor(d, "/inv:Invoice/cbc:InvoiceTypeCode")).isEqualTo("01");
        assertThat(valor(d, "/inv:Invoice/cbc:InvoiceTypeCode/@listID")).isEqualTo("0101");
        assertThat(valor(d, "/inv:Invoice/cbc:InvoiceTypeCode/@listURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo01");
        assertThat(valor(d, "/inv:Invoice/cbc:InvoiceTypeCode/@listSchemeURI")).isEqualTo("urn:pe:gob:sunat:cpe:see:gem:catalogos:catalogo51");
    }

    @ParameterizedTest
    @CsvSource({"F001, 1, F001-1", "F001, 4355, F001-4355", "FA01, 99999999, FA01-99999999", "FS21, 10, FS21-10"})
    void laNumeracionVaSinCerosALaIzquierda(String serie, long correlativo, String esperado) {
        byte[] xml = new FacturaXmlBuilder(propiedades()).construir(facturaBasica(), serie, correlativo, EMISION);
        assertThat(valor(doc(xml), "/inv:Invoice/cbc:ID")).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-28T10:30:00, 2026-10-28, 10:30:00", "2026-01-01T00:00:00, 2026-01-01, 00:00:00", "2026-12-31T23:59:59, 2026-12-31, 23:59:59",
            "2028-02-29T08:05:09, 2028-02-29, 08:05:09", "2026-10-28T10:30:00.987654321, 2026-10-28, 10:30:00", "2026-03-05T03:04:05.999, 2026-03-05, 03:04:05"})
    void laFechaYLaHoraSeEscribenConElFormatoDeLaSunat(String emision, String fecha, String hora) {
        byte[] xml = new FacturaXmlBuilder(propiedades()).construir(facturaBasica(), "F001", 1, LocalDateTime.parse(emision));
        Document d = doc(xml);
        assertThat(valor(d, "/inv:Invoice/cbc:IssueDate")).isEqualTo(fecha);
        assertThat(valor(d, "/inv:Invoice/cbc:IssueTime")).isEqualTo(hora);
    }

    @Test
    void laLeyendaDelMontoEnLetrasUsaElImporteTotal() {
        FacturaRequest f = factura(item("Polo", "2", "35.00"), item("Camisa", "3", "25.50"), item("Botón", "7", "0.23"));
        Document d = doc(construir(f));
        assertThat(valor(d, "/inv:Invoice/cbc:Note")).isEqualTo("CIENTO CUARENTA Y OCHO CON 11/100 SOLES");
        assertThat(valor(d, "/inv:Invoice/cbc:Note/@languageLocaleID")).isEqualTo("1000");
    }

    // --- Emisor ---

    @Test
    void elEmisorSaleConSuRucSuRazonSocialYElCodigoDeEstablecimiento() {
        Document d = doc(construir(facturaBasica()));
        String base = "/inv:Invoice/cac:AccountingSupplierParty/cac:Party";
        assertThat(valor(d, base + "/cac:PartyIdentification/cbc:ID")).isEqualTo("20100066603");
        assertThat(valor(d, base + "/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(valor(d, base + "/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("EMPRESA DE PRUEBAS SAC");
        assertThat(valor(d, base + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:AddressTypeCode")).isEqualTo("0000");
    }

    @Test
    void sinNombreComercialNiDireccionNoSeEscribenEsosElementos() {
        Document d = doc(construir(facturaBasica()));
        assertThat(cantidad(d, "/inv:Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyName")).isZero();
        assertThat(cantidad(d, "/inv:Invoice/cac:AccountingSupplierParty//cac:AddressLine")).isZero();
    }

    @Test
    void conNombreComercialDireccionYOtroEstablecimientoSeEscribenYValidan() {
        SunatProperties props = propiedades();
        props.getEmisor().setNombreComercial("Mi Tienda de Ropa");
        props.getEmisor().setDireccion("Av. Larco 123, Miraflores");
        props.getEmisor().setCodigoEstablecimiento("0003");
        byte[] xml = construir(props, facturaBasica());
        Document d = doc(xml);
        String base = "/inv:Invoice/cac:AccountingSupplierParty/cac:Party";
        assertThat(valor(d, base + "/cac:PartyName/cbc:Name")).isEqualTo("Mi Tienda de Ropa");
        assertThat(valor(d, base + "/cac:PartyLegalEntity/cac:RegistrationAddress/cac:AddressLine/cbc:Line")).isEqualTo("Av. Larco 123, Miraflores");
        assertThat(valor(d, base + "/cac:PartyLegalEntity/cac:RegistrationAddress/cbc:AddressTypeCode")).isEqualTo("0003");
        assertThat(errores(xml)).isEmpty();
    }

    // --- Cliente ---

    @Test
    void elClienteSaleConRucYRazonSocialYSinDireccionSiNoLaTiene() {
        Document d = doc(construir(facturaBasica()));
        String base = "/inv:Invoice/cac:AccountingCustomerParty/cac:Party";
        assertThat(valor(d, base + "/cac:PartyIdentification/cbc:ID")).isEqualTo("20123456786");
        assertThat(valor(d, base + "/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("6");
        assertThat(valor(d, base + "/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("CLIENTE SAC");
        assertThat(cantidad(d, base + "//cac:AddressLine")).isZero();
    }

    @Test
    void conDireccionDelClienteSeEscribeYValida() {
        FacturaRequest f = facturaBasica();
        f.getCliente().setDireccion("Jr. Unión 456, Lima");
        byte[] xml = construir(f);
        assertThat(valor(doc(xml), "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cac:RegistrationAddress/cac:AddressLine/cbc:Line"))
                .isEqualTo("Jr. Unión 456, Lima");
        assertThat(errores(xml)).isEmpty();
    }

    @Test
    void laFormaDePagoEsContado() {
        Document d = doc(construir(facturaBasica()));
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms/cbc:ID")).isEqualTo("FormaPago");
        assertThat(valor(d, "/inv:Invoice/cac:PaymentTerms/cbc:PaymentMeansID")).isEqualTo("Contado");
    }

    // --- Líneas ---

    @Test
    void unaLineaTieneTodosSusDatos() {
        Document d = doc(construir(facturaBasica()));
        String l = "/inv:Invoice/cac:InvoiceLine[1]";
        assertThat(valor(d, l + "/cbc:ID")).isEqualTo("1");
        assertThat(valor(d, l + "/cbc:InvoicedQuantity")).isEqualTo("2");
        assertThat(valor(d, l + "/cbc:InvoicedQuantity/@unitCode")).isEqualTo("NIU");
        assertThat(valor(d, l + "/cbc:LineExtensionAmount")).isEqualTo("59.32");
        assertThat(valor(d, l + "/cbc:LineExtensionAmount/@currencyID")).isEqualTo("PEN");
        assertThat(valor(d, l + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount")).isEqualTo("35.00");
        assertThat(valor(d, l + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceTypeCode")).isEqualTo("01");
        assertThat(valor(d, l + "/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("10.68");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount")).isEqualTo("59.32");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxAmount")).isEqualTo("10.68");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:Percent")).isEqualTo("18.00");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:TaxExemptionReasonCode")).isEqualTo("10");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:ID")).isEqualTo("1000");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:Name")).isEqualTo("IGV");
        assertThat(valor(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cac:TaxScheme/cbc:TaxTypeCode")).isEqualTo("VAT");
        assertThat(valor(d, l + "/cac:Item/cbc:Description")).isEqualTo("Polo basico negro");
        assertThat(valor(d, l + "/cac:Price/cbc:PriceAmount")).isEqualTo("29.6610169492");
    }

    @Test
    void variasLineasSeNumeranDelUnoEnAdelanteYLosTotalesSumanLasLineas() {
        FacturaRequest f = factura(item("Polo", "2", "35.00"), item("Camisa", "3", "25.50"), item("Botón", "7", "0.23"));
        byte[] xml = construir(f);
        Document d = doc(xml);

        assertThat(valores(d, "/inv:Invoice/cac:InvoiceLine/cbc:ID")).containsExactly("1", "2", "3");
        assertThat(valores(d, "/inv:Invoice/cac:InvoiceLine/cbc:LineExtensionAmount")).containsExactly("59.32", "64.83", "1.36");
        assertThat(valores(d, "/inv:Invoice/cac:InvoiceLine/cac:TaxTotal/cbc:TaxAmount")).containsExactly("10.68", "11.67", "0.25");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount")).isEqualTo("125.51");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("22.60");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount")).isEqualTo("125.51");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount")).isEqualTo("148.11");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("148.11");
        assertThat(errores(xml)).isEmpty();
        assertThat(ReglasSunatXml.verificar(d, BigDecimal.ONE)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"2, 2", "2.00, 2", "1.5, 1.5", "0.001, 0.001", "100000, 100000", "0.1234567890, 0.123456789", "12.50, 12.5", "1E+3, 1000", "0.5, 0.5"})
    void laCantidadSeEscribeSinCerosDeMasNiNotacionCientifica(String cantidad, String esperado) {
        Document d = doc(construir(factura(item("Cosa", cantidad, "100.00"))));
        assertThat(valor(d, "//cac:InvoiceLine/cbc:InvoicedQuantity")).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"35.00, 35.00", "35, 35.00", "35.0, 35.00", "12.5, 12.50", "0.1234567891, 0.1234567891", "1000, 1000.00", "1E+3, 1000.00", "9.999, 9.999"})
    void elPrecioConIgvSeEscribeConAlMenosDosDecimalesYSinNotacionCientifica(String precio, String esperado) {
        Document d = doc(construir(factura(item("Cosa", "1", precio))));
        assertThat(valor(d, "//cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount")).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NIU", "ZZ", "KGM", "LTR", "MTR", "BX"})
    void laUnidadDeMedidaSeEscribeEnLaCantidad(String unidad) {
        ItemRequest i = item("Cosa", "2", "10.00");
        i.setUnidadMedida(unidad);
        byte[] xml = construir(factura(i));
        assertThat(valor(doc(xml), "//cac:InvoiceLine/cbc:InvoicedQuantity/@unitCode")).isEqualTo(unidad);
        assertThat(errores(xml)).isEmpty();
    }

    @Test
    void sinCodigoDeProductoNoSeEscribeYConCodigoSi() {
        Document sin = doc(construir(facturaBasica()));
        assertThat(cantidad(sin, "//cac:Item/cac:SellersItemIdentification")).isZero();

        ItemRequest i = item("Polo", "1", "35.00");
        i.setCodigo("POLO-N-M");
        byte[] xml = construir(factura(i));
        assertThat(valor(doc(xml), "//cac:Item/cac:SellersItemIdentification/cbc:ID")).isEqualTo("POLO-N-M");
        assertThat(errores(xml)).isEmpty();
    }

    @Test
    void unCodigoEnBlancoSeTrataComoSinCodigo() {
        ItemRequest i = item("Polo", "1", "35.00");
        i.setCodigo("   ");
        assertThat(cantidad(doc(construir(factura(i))), "//cac:Item/cac:SellersItemIdentification")).isZero();
    }

    // --- Codificación: ISO-8859-1 declarada y real ---

    @Test
    void laDeclaracionDiceIso88591() {
        String inicio = new String(construir(facturaBasica()), 0, 60, StandardCharsets.ISO_8859_1);
        assertThat(inicio).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"");
    }

    @Test
    void lasEniesYTildesSeEscribenComoUnSoloByteLatin1Reales() {
        FacturaRequest f = factura(item("Ñandú, camión y señal", "1", "10.00"));
        byte[] xml = construir(f);

        // Ñ = 0xD1, ú = 0xFA, ó = 0xF3, ñ = 0xF1: un solo byte cada una (en UTF-8 serían dos y empezarían con 0xC3)
        assertThat(contiene(xml, (byte) 0xD1)).isTrue();
        assertThat(contiene(xml, (byte) 0xFA)).isTrue();
        assertThat(contiene(xml, (byte) 0xC3)).as("no debe haber bytes de UTF-8").isFalse();
        assertThat(new String(xml, StandardCharsets.ISO_8859_1)).contains("Ñandú, camión y señal");
        assertThat(valor(doc(xml), "//cac:Item/cbc:Description")).isEqualTo("Ñandú, camión y señal");
        assertThat(errores(xml)).isEmpty();
    }

    private static boolean contiene(byte[] bytes, byte buscado) {
        for (byte b : bytes) {
            if (b == buscado) {
                return true;
            }
        }
        return false;
    }

    @Test
    void todoElXmlSoloUsaBytesLatin1ValidosYNoPierdeNada() {
        byte[] xml = construir(factura(item("Camión", "1", "10.00")));
        String comoLatin1 = new String(xml, StandardCharsets.ISO_8859_1);
        assertThat(comoLatin1.getBytes(StandardCharsets.ISO_8859_1)).isEqualTo(xml);
    }

    // --- Caracteres especiales ---

    @Test
    void lasMarcasDeXmlEnLosTextosSeEscapanYSeLeenIgual() {
        String descripcion = "Cinturón <cuero> & hebilla \"dorada\" 'M' ]]> <![CDATA[x]]>";
        FacturaRequest f = factura(item(descripcion, "1", "10.00"));
        f.getCliente().setRazonSocial("Ramírez & Hijos <SAC> \"1\"");
        byte[] xml = construir(f);
        Document d = doc(xml);

        assertThat(valor(d, "//cac:Item/cbc:Description")).isEqualTo(descripcion);
        assertThat(valor(d, "//cac:AccountingCustomerParty//cbc:RegistrationName")).isEqualTo("Ramírez & Hijos <SAC> \"1\"");
        assertThat(errores(xml)).isEmpty();
    }

    @Test
    void unaRazonSocialDelEmisorConAmpersandSeEscapaEnTodosSusLugares() {
        SunatProperties props = propiedades();
        props.getEmisor().setRazonSocial("K&G Asociados S.A.");
        byte[] xml = construir(props, facturaBasica());
        Document d = doc(xml);
        assertThat(valor(d, "//cac:AccountingSupplierParty//cbc:RegistrationName")).isEqualTo("K&G Asociados S.A.");
        assertThat(valor(d, "//cac:Signature/cac:SignatoryParty/cac:PartyName/cbc:Name")).isEqualTo("K&G Asociados S.A.");
        assertThat(errores(xml)).isEmpty();
    }

    @Test
    void losEmojisYSimbolosFueraDeLatin1SeCambianYElXmlSigueSiendoValido() {
        FacturaRequest f = factura(item("Polo — edición “limitada” 😀 日本", "1", "10.00"));
        byte[] xml = construir(f);
        assertThat(valor(doc(xml), "//cac:Item/cbc:Description")).isEqualTo("Polo - edición \"limitada\" ? ??");
        assertThat(errores(xml)).isEmpty();
    }

    @Test
    void losSaltosDeLineaYTabulacionesSeVuelvenEspacios() {
        FacturaRequest f = factura(item("Línea uno\nlínea dos\t\ttres\r\n", "1", "10.00"));
        assertThat(valor(doc(construir(f)), "//cac:Item/cbc:Description")).isEqualTo("Línea uno línea dos tres");
    }

    // --- Datos inválidos ---

    @Test
    void sinSolicitudOSinClienteOSinItemsFalla() {
        FacturaXmlBuilder b = new FacturaXmlBuilder(propiedades());
        assertThatThrownBy(() -> b.construir(null, "F001", 1, EMISION)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.construir(FacturaRequest.builder().items(List.of(item("a", "1", "1"))).build(), "F001", 1, EMISION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.construir(FacturaRequest.builder().cliente(factura(item("a", "1", "1")).getCliente()).build(), "F001", 1, EMISION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> b.construir(FacturaRequest.builder().cliente(factura(item("a", "1", "1")).getCliente()).items(List.of()).build(), "F001", 1, EMISION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinFechaDeEmisionFalla() {
        assertThatThrownBy(() -> new FacturaXmlBuilder(propiedades()).construir(facturaBasica(), "F001", 1, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fecha de emisión");
    }

    @ParameterizedTest
    @ValueSource(strings = {"B001", "f001", "F01", "F0011", "0001", " F001"})
    void unaSerieInvalidaFalla(String serie) {
        assertThatThrownBy(() -> new FacturaXmlBuilder(propiedades()).construir(facturaBasica(), serie, 1, EMISION))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("serie");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 100_000_000L})
    void unCorrelativoInvalidoFalla(long correlativo) {
        assertThatThrownBy(() -> new FacturaXmlBuilder(propiedades()).construir(facturaBasica(), "F001", correlativo, EMISION))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("correlativo");
    }

    @Test
    void unaSerieNulaFalla() {
        assertThatThrownBy(() -> new FacturaXmlBuilder(propiedades()).construir(facturaBasica(), null, 1, EMISION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elErrorDeUnItemDiceCualEs() {
        FacturaRequest f = factura(item("Bien", "1", "10.00"), item("Mal", "1", "0.001"));
        assertThatThrownBy(() -> construir(f))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Ítem 2:").hasMessageContaining("al menos 0.01");
    }

    @Test
    void unaDescripcionQueQuedaVaciaDespuesDeLimpiarFalla() {
        FacturaRequest f = factura(item("\u0000\u0001\n\t", "1", "10.00"));
        assertThatThrownBy(() -> construir(f)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ítem 1").hasMessageContaining("vacía");
    }

    @Test
    void unTextoQueCreceAlLimpiarYPasaElMaximoFalla() {
        // cada "…" pasa a ser "..." (tres caracteres): 200 puntos suspensivos dan 600, más de los 500 que admite la SUNAT
        FacturaRequest f = factura(item("…".repeat(200), "1", "10.00"));
        assertThatThrownBy(() -> construir(f)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pasa de 500");
    }

    @Test
    void unaRazonSocialDeClienteQueQuedaVaciaFalla() {
        FacturaRequest f = facturaBasica();
        f.getCliente().setRazonSocial("\n\t \u0001");
        assertThatThrownBy(() -> construir(f)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("razón social del cliente");
    }

    @Test
    void unaRazonSocialDelEmisorQueQuedaVaciaFalla() {
        SunatProperties props = propiedades();
        props.getEmisor().setRazonSocial("\u0001\u0002");
        assertThatThrownBy(() -> construir(props, facturaBasica())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("emisor");
    }

    // --- No cambia lo que recibe, es determinista y aguanta varios hilos ---

    @Test
    void noModificaLaSolicitud() {
        FacturaRequest f = factura(item("Cinturón \u0000 raro 😀", "1.50", "10.5"), item("Otro", "3", "7.77"));
        f.getCliente().setRazonSocial("Cliente \n con salto");
        String antes = f.toString();
        construir(f);
        assertThat(f.toString()).isEqualTo(antes);
    }

    @Test
    void lamismaEntradaDaSiempreLosMismosBytes() {
        byte[] primero = construir(facturaBasica());
        for (int i = 0; i < 20; i++) {
            assertThat(construir(facturaBasica())).isEqualTo(primero);
        }
    }

    @Test
    void variosHilosArmandoALaVezDanElMismoResultado() throws Exception {
        byte[] esperado = construir(facturaBasica());
        FacturaXmlBuilder compartido = new FacturaXmlBuilder(propiedades());
        ExecutorService hilos = Executors.newFixedThreadPool(8);
        try {
            List<Future<byte[]>> resultados = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                resultados.add(hilos.submit(() -> compartido.construir(facturaBasica(), "F001", 1, EMISION)));
            }
            for (Future<byte[]> r : resultados) {
                assertThat(r.get()).isEqualTo(esperado);
            }
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void losEspaciosDeNombresSeDeclaranUnaSolaVezEnLaRaiz() {
        String xml = new String(construir(facturaBasica()), StandardCharsets.ISO_8859_1);
        assertThat(xml.split("xmlns", -1).length - 1).isEqualTo(5);
        assertThat(xml).doesNotContain("xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\" xmlns:cbc");
    }

    // --- Tamaño ---

    @Test
    void conLas999LineasQueAdmiteLaSunatEsValidoYRapido() {
        List<ItemRequest> items = new ArrayList<>();
        for (int i = 1; i <= 999; i++) {
            items.add(item("Producto " + i, "1", String.valueOf(10 + (i % 90)) + ".50"));
        }
        long inicio = System.nanoTime();
        byte[] xml = construir(FacturaRequest.builder().cliente(facturaBasica().getCliente()).items(items).build());
        long ms = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(cantidad(doc(xml), "/inv:Invoice/cac:InvoiceLine")).isEqualTo(999);
        assertThat(errores(xml)).isEmpty();
        assertThat(ms).as("tarda " + ms + " ms").isLessThan(3000);
    }

    // --- Miles de facturas al azar: esquema oficial y reglas numéricas de la SUNAT ---

    private static final String[] DESCRIPCIONES = {"Polo básico negro", "Camisa Oxford M/L — azul", "Jean \"slim\" 32", "Zapatillas Nike® Air", "Cinturón de cuero & hebilla",
            "Pantalón <cargo> 38", "Gorra 'trucker' ñandú", "Servicio de estampado (logo)", "Café 250g", "Artículo con emoji 😀", "Línea\ncon salto", "A"};
    private static final String[] UNIDADES = {"NIU", "NIU", "NIU", "ZZ", "KGM", "LTR"};

    @Test
    void trescientasFacturasAleatoriasCumplenElEsquemaYLasReglasDeLaSunat() {
        Random azar = new Random(20261030L);
        for (int n = 0; n < 300; n++) {
            int cuantos = 1 + azar.nextInt(40);
            List<ItemRequest> items = new ArrayList<>();
            for (int i = 0; i < cuantos; i++) {
                BigDecimal precio = BigDecimal.valueOf(1 + azar.nextInt(500_000), azar.nextInt(5));
                BigDecimal cantidad = BigDecimal.valueOf(1 + azar.nextInt(2_000), azar.nextInt(4));
                if (precio.multiply(cantidad).setScale(2, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("0.01")) < 0) {
                    continue;
                }
                ItemRequest item = ItemRequest.builder().descripcion(DESCRIPCIONES[azar.nextInt(DESCRIPCIONES.length)]).cantidad(cantidad).precioUnitario(precio)
                        .unidadMedida(UNIDADES[azar.nextInt(UNIDADES.length)]).codigo(azar.nextBoolean() ? "COD-" + azar.nextInt(10_000) : null).build();
                items.add(item);
            }
            if (items.isEmpty()) {
                continue;
            }
            ClienteRequest cliente = ClienteRequest.builder().ruc(RUCS_VALIDOS[azar.nextInt(RUCS_VALIDOS.length)])
                    .razonSocial(DESCRIPCIONES[azar.nextInt(DESCRIPCIONES.length)] + " S.A.C.").direccion(azar.nextBoolean() ? "Av. Perú " + azar.nextInt(999) : null).build();
            SunatProperties props = propiedades();
            if (azar.nextBoolean()) {
                props.getEmisor().setNombreComercial("Tienda " + azar.nextInt(100));
            }
            byte[] xml = new FacturaXmlBuilder(props).construir(FacturaRequest.builder().cliente(cliente).items(items).build(), "F001", 1 + azar.nextInt(99_999), EMISION);

            String caso = "factura aleatoria " + n + " con " + items.size() + " líneas";
            assertThat(errores(xml)).as(caso + ": esquema").isEmpty();
            assertThat(ReglasSunatXml.verificar(doc(xml), new BigDecimal("0.50"))).as(caso + ": reglas de la SUNAT").isEmpty();
        }
    }
}
