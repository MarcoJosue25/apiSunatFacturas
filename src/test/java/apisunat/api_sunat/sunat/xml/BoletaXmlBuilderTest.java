package apisunat.api_sunat.sunat.xml;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.util.ReglasSunatXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static apisunat.api_sunat.util.FacturaXmlTestUtil.cantidad;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.conFirmaDeRelleno;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.leer;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoletaXmlBuilderTest {

    private static final LocalDateTime EMISION = LocalDateTime.of(2026, 10, 28, 10, 30, 0);
    private static final XsdValidator VALIDADOR = new XsdValidator();

    private static FacturaXmlBuilder builder() {
        SunatProperties p = new SunatProperties();
        p.setRuc("20100066603");
        p.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC");
        return new FacturaXmlBuilder(p);
    }

    private static ItemRequest item(String desc, String cant, String precio) {
        return ItemRequest.builder().descripcion(desc).cantidad(new BigDecimal(cant)).precioUnitario(new BigDecimal(precio)).build();
    }

    private static BoletaRequest boleta(ClienteBoletaRequest cliente, ItemRequest... items) {
        return BoletaRequest.builder().idExterno("p1").cliente(cliente).items(List.of(items)).build();
    }

    private static byte[] xml(BoletaRequest r) {
        return builder().construirBoleta(r, "B001", 12, EMISION);
    }

    private static Document doc(byte[] xml) {
        return leer(xml);
    }

    private static ClienteBoletaRequest dni() {
        return ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").nombre("Luana Pazos Atoche").build();
    }

    // --- Estructura ---

    @Test
    void laBoletaEsUnInvoiceConTipo03YNumeroDeSerieB() {
        Document d = doc(xml(boleta(dni(), item("Polo", "2", "35.00"))));

        assertThat(valor(d, "/inv:Invoice/cbc:InvoiceTypeCode")).isEqualTo("03");
        assertThat(valor(d, "/inv:Invoice/cbc:ID")).isEqualTo("B001-12");
        assertThat(valor(d, "/inv:Invoice/cbc:UBLVersionID")).isEqualTo("2.1");
        assertThat(valor(d, "/inv:Invoice/cbc:IssueDate")).isEqualTo("2026-10-28");
        assertThat(valor(d, "/inv:Invoice/cbc:IssueTime")).isEqualTo("10:30:00");
    }

    @Test
    void laBoletaNoLlevaFormaDePagoQueEsDeLaFactura() {
        Document d = doc(xml(boleta(dni(), item("Polo", "2", "35.00"))));
        assertThat(cantidad(d, "/inv:Invoice/cac:PaymentTerms")).isZero();
    }

    @Test
    void laBoletaCumpleElEsquemaOficialYLasReglasDeLosNumeros() {
        byte[] x = xml(boleta(dni(), item("Polo", "2", "35.00"), item("Cuaderno", "3", "8.90")));

        assertThat(VALIDADOR.validarFactura(conFirmaDeRelleno(x))).isEmpty();
        assertThat(ReglasSunatXml.verificar(doc(x), new BigDecimal("0.50"))).isEmpty();
    }

    @Test
    void elTotalEnLetrasYLosImportesSonLosDeLaFactura() {
        Document d = doc(xml(boleta(dni(), item("Polo", "2", "35.00"))));
        assertThat(valor(d, "/inv:Invoice/cbc:Note")).isEqualTo("SETENTA CON 00/100 SOLES");
        assertThat(valor(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount")).isEqualTo("70.00");
        assertThat(valor(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount")).isEqualTo("10.68");
    }

    // --- El comprador ---

    @ParameterizedTest
    @CsvSource({"1,46237547", "6,20123456786", "4,AB1234567", "7,P12345678901"})
    void elTipoYElNumeroDeDocumentoVanComoLosDio(String tipo, String numero) {
        ClienteBoletaRequest c = ClienteBoletaRequest.builder().tipoDocumento(tipo).numeroDocumento(numero).nombre("Juan").build();
        Document d = doc(xml(boleta(c, item("Polo", "1", "10.00"))));

        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo(numero);
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo(tipo);
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("Juan");
    }

    @Test
    void sinClienteLaBoletaEsAnonimaConTipo0YGuion() {
        Document d = doc(xml(boleta(null, item("Polo", "1", "10.00"))));

        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("-");
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID/@schemeID")).isEqualTo("0");
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("CLIENTES VARIOS");
    }

    @Test
    void unaBoletaAnonimaCumpleElEsquema() {
        byte[] x = xml(boleta(new ClienteBoletaRequest(), item("Polo", "1", "10.00")));
        assertThat(VALIDADOR.validarFactura(conFirmaDeRelleno(x))).isEmpty();
    }

    @Test
    void unClienteAnonimoPuedeTenerNombre() {
        ClienteBoletaRequest c = ClienteBoletaRequest.builder().tipoDocumento("0").nombre("Cliente de mostrador").build();
        Document d = doc(xml(boleta(c, item("Polo", "1", "10.00"))));
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("Cliente de mostrador");
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyIdentification/cbc:ID")).isEqualTo("-");
    }

    @Test
    void unClienteConDocumentoPeroSinNombreSaleComoClientesVarios() {
        ClienteBoletaRequest c = ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").build();
        Document d = doc(xml(boleta(c, item("Polo", "1", "10.00"))));
        assertThat(valor(d, "/inv:Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName")).isEqualTo("CLIENTES VARIOS");
    }

    @Test
    void elNombreConTildesYEnieSeEscribeEnIso88591() {
        ClienteBoletaRequest c = ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").nombre("Ñandú Pérez Ibáñez").build();
        byte[] x = xml(boleta(c, item("Camión", "1", "10.00")));
        assertThat(new String(x, StandardCharsets.ISO_8859_1)).contains("Ñandú Pérez Ibáñez");
        assertThat(VALIDADOR.validarFactura(conFirmaDeRelleno(x))).isEmpty();
    }

    // --- Validaciones del armador ---

    @Test
    void sinItemsFalla() {
        BoletaRequest r = BoletaRequest.builder().idExterno("p1").items(List.of()).build();
        assertThatThrownBy(() -> xml(r)).isInstanceOf(IllegalArgumentException.class).hasMessage("La boleta necesita al menos un ítem");
        assertThatThrownBy(() -> builder().construirBoleta(null, "B001", 1, EMISION)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"F001", "b001", "B00", "0001", ""})
    void unaSerieQueNoEsDeBoletaFalla(String serie) {
        assertThatThrownBy(() -> builder().construirBoleta(boleta(null, item("Polo", "1", "10")), serie, 1, EMISION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaSerieDeBoletaNoSirveParaUnaFacturaYAlRevesTampoco() {
        assertThatThrownBy(() -> builder().construir(apisunat.api_sunat.dto.FacturaRequest.builder().idExterno("a")
                .cliente(apisunat.api_sunat.dto.ClienteRequest.builder().ruc("20123456786").razonSocial("X").build())
                .items(List.of(item("Polo", "1", "10"))).build(), "B001", 1, EMISION)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinFechaDeEmisionFalla() {
        assertThatThrownBy(() -> builder().construirBoleta(boleta(null, item("Polo", "1", "10")), "B001", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unItemConMontoInvalidoIndicaCualEs() {
        assertThatThrownBy(() -> xml(boleta(null, item("Polo", "1", "10"), item("Malo", "1", "0"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Ítem 2");
    }

    @Test
    void elMismoXmlSalePorSegundaVezIgual() {
        BoletaRequest r = boleta(dni(), item("Polo", "2", "35.00"));
        assertThat(xml(r)).isEqualTo(xml(r));
    }

    @Test
    void conVariosItemsYDecimalesLosNumerosCuadran() {
        byte[] x = xml(boleta(null, item("A", "3", "12.34"), item("B", "0.5", "99.99"), item("C", "10", "0.10"), item("D", "1", "700.00")));
        assertThat(ReglasSunatXml.verificar(doc(x), new BigDecimal("0.50"))).isEmpty();
        assertThat(VALIDADOR.validarFactura(conFirmaDeRelleno(x))).isEmpty();
    }
}
