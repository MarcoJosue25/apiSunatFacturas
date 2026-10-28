package apisunat.api_sunat.sunat.firma;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.exception.FirmaException;
import apisunat.api_sunat.sunat.xml.FacturaXmlBuilder;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static apisunat.api_sunat.util.FacturaXmlTestUtil.comoLatin1;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.leer;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.normalizar;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.valor;
import static apisunat.api_sunat.util.FirmaTestUtil.certificadoDeLaFirma;
import static apisunat.api_sunat.util.FirmaTestUtil.diagnostico;
import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlSignerTest {

    private static final String NS_DS = "http://www.w3.org/2000/09/xmldsig#";
    private static final String NS_EXT = "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2";
    private static final LocalDateTime EMISION = LocalDateTime.of(2026, 10, 28, 10, 30, 0);

    private static final XmlSigner FIRMANTE = new XmlSigner();
    private static final XsdValidator VALIDADOR = new XsdValidator();
    private static CertificadoFirma certificado;

    @BeforeAll
    static void cargarCertificado() {
        certificado = certificado("valido.p12");
    }

    private static CertificadoFirma certificado(String archivo) {
        return certificado(archivo, "20100066603");
    }

    private static CertificadoFirma certificado(String archivo, String ruc) {
        try {
            String ruta = Path.of(XmlSignerTest.class.getResource("/certificados/" + archivo).toURI()).toString();
            Clock reloj = Clock.fixed(Instant.parse("2026-10-28T15:00:00Z"), ZoneOffset.UTC);
            return new CertificadoLoader(new SunatProperties(), reloj).cargar(ruta, "prueba123".toCharArray(), ruc);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- Datos ---

    private static ItemRequest item(String descripcion, String cantidad, String precio) {
        return ItemRequest.builder().descripcion(descripcion).cantidad(new BigDecimal(cantidad)).precioUnitario(new BigDecimal(precio)).build();
    }

    private static byte[] sinFirmar(ItemRequest... items) {
        SunatProperties p = new SunatProperties();
        p.setRuc("20100066603");
        p.getEmisor().setRazonSocial("EMPRESA DE PRUEBAS SAC");
        FacturaRequest request = FacturaRequest.builder()
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(Arrays.asList(items)).build();
        return new FacturaXmlBuilder(p).construir(request, "F001", 1, EMISION);
    }

    private static byte[] sinFirmar() {
        return sinFirmar(item("Polo basico negro", "2", "35.00"));
    }

    private static byte[] firmada() {
        return FIRMANTE.firmar(sinFirmar(), certificado);
    }

    private static byte[] reemplazar(byte[] xml, String buscado, String nuevo) {
        String texto = comoLatin1(xml);
        assertThat(texto).contains(buscado);
        return texto.replace(buscado, nuevo).getBytes(StandardCharsets.ISO_8859_1);
    }

    // --- La firma es correcta ---

    @Test
    void laFirmaVerificaConElCertificadoQueTraeElPropioDocumento() {
        byte[] firmada = firmada();
        assertThat(verifica(firmada)).as(diagnostico(firmada)).isTrue();
        assertThat(certificadoDeLaFirma(firmada)).isEqualTo(certificado.certificado());
    }

    @Test
    void usaRsaConSha256YCanonicalizacionYEnvelopedComoPideElEjemploDeLaSunat() {
        Document doc = leer(firmada());
        Element firma = (Element) doc.getElementsByTagNameNS(NS_DS, "Signature").item(0);

        assertThat(atributo(firma, "SignatureMethod", "Algorithm")).isEqualTo("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256");
        assertThat(atributo(firma, "CanonicalizationMethod", "Algorithm")).isEqualTo("http://www.w3.org/TR/2001/REC-xml-c14n-20010315");
        assertThat(atributo(firma, "DigestMethod", "Algorithm")).isEqualTo("http://www.w3.org/2001/04/xmlenc#sha256");
        assertThat(atributo(firma, "Transform", "Algorithm")).isEqualTo("http://www.w3.org/2000/09/xmldsig#enveloped-signature");
        assertThat(atributo(firma, "Reference", "URI")).isEmpty();
        assertThat(firma.getElementsByTagNameNS(NS_DS, "Reference").getLength()).isEqualTo(1);
        assertThat(firma.getElementsByTagNameNS(NS_DS, "Transform").getLength()).isEqualTo(1);
    }

    private static String atributo(Element firma, String elemento, String atributo) {
        return ((Element) firma.getElementsByTagNameNS(NS_DS, elemento).item(0)).getAttribute(atributo);
    }

    @Test
    void elBase64DeLaFirmaYDelCertificadoVaEnUnaSolaLineaSinEntidadesDeRetornoDeCarro() {
        byte[] firmada = firmada();
        String texto = comoLatin1(firmada);

        assertThat(texto).doesNotContain("&#13;").doesNotContain("\r").doesNotContain("\n");
        assertThat(verifica(firmada)).isTrue();
    }

    @Test
    void laFirmaTraeElCertificadoYLosValoresEnBase64() {
        Document doc = leer(firmada());
        String firma = doc.getElementsByTagNameNS(NS_DS, "SignatureValue").item(0).getTextContent().replaceAll("\\s", "");
        String digest = doc.getElementsByTagNameNS(NS_DS, "DigestValue").item(0).getTextContent();
        String cert = doc.getElementsByTagNameNS(NS_DS, "X509Certificate").item(0).getTextContent().replaceAll("\\s", "");

        assertThat(java.util.Base64.getDecoder().decode(firma)).hasSize(256);   // RSA de 2048 bits
        assertThat(java.util.Base64.getDecoder().decode(digest)).hasSize(32);   // SHA-256
        assertThat(java.util.Base64.getDecoder().decode(cert)).isNotEmpty();
    }

    @Test
    void firmarDosVecesElMismoXmlDaExactamenteLosMismosBytes() {
        byte[] sin = sinFirmar();
        assertThat(FIRMANTE.firmar(sin, certificado)).isEqualTo(FIRMANTE.firmar(sin, certificado));
    }

    @Test
    void conUnaClaveRsaDe1024BitsTambienFirma() {
        byte[] firmada = FIRMANTE.firmar(sinFirmar(), certificado("rsa-1024.p12"));
        assertThat(verifica(firmada)).isTrue();
        assertThat(java.util.Base64.getDecoder().decode(leer(firmada).getElementsByTagNameNS(NS_DS, "SignatureValue").item(0).getTextContent().replaceAll("\\s", ""))).hasSize(128);
    }

    // --- Cualquier cambio posterior rompe la firma ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "<cbc:ID>F001-1</cbc:ID>|<cbc:ID>F001-2</cbc:ID>",
            "Polo basico negro|Polo basico negra",
            "SETENTA CON 00/100 SOLES|SETENTA CON 01/100 SOLES",
            "EMPRESA DE PRUEBAS SAC|EMPRESA DE PRUEBAS SAS",
            "20123456786|20123456787",
            ">10.68<|>10.69<",
            "2026-10-28|2026-10-29",
    })
    void cambiarUnSoloCaracterDespuesDeFirmarRompeLaVerificacion(String buscado, String nuevo) {
        byte[] firmada = firmada();
        assertThat(verifica(firmada)).isTrue();

        byte[] alterada = reemplazar(firmada, buscado, nuevo);
        assertThat(verifica(alterada)).as(diagnostico(alterada)).isFalse();
        assertThat(diagnostico(alterada)).contains("referencias=false");
    }

    @Test
    void cambiarUnNumeroDelImporteRompeLaVerificacion() {
        byte[] firmada = firmada();
        String texto = comoLatin1(firmada);
        assertThat(texto).contains(">70.00<");
        byte[] alterada = reemplazar(firmada, ">70.00<", ">70.01<");
        assertThat(verifica(alterada)).isFalse();
    }

    @Test
    void cambiarElValorDeLaFirmaTambienRompeLaVerificacion() {
        byte[] firmada = firmada();
        String valor = leer(firmada).getElementsByTagNameNS(NS_DS, "SignatureValue").item(0).getTextContent();
        char primero = valor.charAt(0);
        String cambiado = (primero == 'A' ? 'B' : 'A') + valor.substring(1);

        byte[] alterada = reemplazar(firmada, valor, cambiado);
        assertThat(verifica(alterada)).isFalse();
        assertThat(diagnostico(alterada)).contains("valorDeLaFirma=false").contains("referencias=true");
    }

    @Test
    void cambiarElDigestGuardadoRompeLaVerificacion() {
        byte[] firmada = firmada();
        String digest = leer(firmada).getElementsByTagNameNS(NS_DS, "DigestValue").item(0).getTextContent();
        String cambiado = (digest.charAt(0) == 'A' ? 'B' : 'A') + digest.substring(1);
        assertThat(verifica(reemplazar(firmada, digest, cambiado))).isFalse();
    }

    @Test
    void unaFirmaHechaConOtraClaveQueNoEsLaDelCertificadoNoVerifica() {
        // La clave privada es de un certificado y el certificado que se publica en la firma es de otro
        CertificadoFirma mezclado = new CertificadoFirma(
                certificado("otro-ruc.p12", "20512345678").clavePrivada(), certificado.certificado(), certificado.ruc());
        byte[] firmada = FIRMANTE.firmar(sinFirmar(), mezclado);

        assertThat(verifica(firmada)).isFalse();
        assertThat(diagnostico(firmada)).contains("valorDeLaFirma=false").contains("referencias=true");
    }

    @Test
    void agregarEspaciosEntreElementosDespuesDeFirmarRompeLaVerificacion() {
        byte[] firmada = firmada();
        assertThat(verifica(reemplazar(firmada, "</cac:Signature>", "</cac:Signature>\n  "))).isFalse();
    }

    // --- Posición y contenido ---

    @Test
    void laFirmaQuedaDentroDelUnicoExtensionContentYEsLaUnicaDelDocumento() {
        Document doc = leer(firmada());

        assertThat(doc.getElementsByTagNameNS(NS_EXT, "UBLExtension").getLength()).isEqualTo(1);
        assertThat(doc.getElementsByTagNameNS(NS_EXT, "ExtensionContent").getLength()).isEqualTo(1);
        assertThat(doc.getElementsByTagNameNS(NS_DS, "Signature").getLength()).isEqualTo(1);

        Element contenido = (Element) doc.getElementsByTagNameNS(NS_EXT, "ExtensionContent").item(0);
        assertThat(contenido.getChildNodes().getLength()).isEqualTo(1);
        Node hijo = contenido.getFirstChild();
        assertThat(hijo.getNamespaceURI()).isEqualTo(NS_DS);
        assertThat(hijo.getLocalName()).isEqualTo("Signature");
        assertThat(hijo.getPrefix()).isEqualTo("ds");

        // y sigue siendo lo primero del documento, antes de UBLVersionID
        Element extensiones = (Element) contenido.getParentNode().getParentNode();
        assertThat(extensiones.getLocalName()).isEqualTo("UBLExtensions");
        assertThat(extensiones.getParentNode()).isSameAs(doc.getDocumentElement());
        assertThat(extensiones.getNextSibling().getLocalName()).isEqualTo("UBLVersionID");
    }

    @Test
    void elIdDeLaFirmaCoincideConLaReferenciaDelDocumento() {
        byte[] firmada = firmada();
        Document doc = leer(firmada);
        Element firma = (Element) doc.getElementsByTagNameNS(NS_DS, "Signature").item(0);

        assertThat(firma.getAttribute("Id")).isEqualTo("SignatureSP");
        assertThat(valor(doc, "//cac:Signature/cac:DigitalSignatureAttachment/cac:ExternalReference/cbc:URI")).isEqualTo("#SignatureSP");
    }

    @Test
    void laFirmaNoAlteraElContenidoDelDocumento() {
        byte[] sin = sinFirmar();
        byte[] firmada = FIRMANTE.firmar(sin, certificado);

        // normalizar() ignora el contenido de ExtensionContent, donde va la firma: todo lo demás debe ser idéntico
        assertThat(normalizar(leer(firmada))).isEqualTo(normalizar(leer(sin)));
    }

    @Test
    void firmarSoloCambiaElOrdenDeLosXmlnsDeLaRaizYAgregaLaFirma() {
        byte[] sin = sinFirmar();
        byte[] firmada = FIRMANTE.firmar(sin, certificado);

        // Al volver a escribir el XML el JDK pone los xmlns de la raíz en otro orden (es lo único que cambia fuera de la firma)
        String antes = comoLatin1(sin).replace("<ext:ExtensionContent/>", "<ext:ExtensionContent></ext:ExtensionContent>");
        String despues = comoLatin1(firmada).replaceAll("(?s)<ds:Signature.*</ds:Signature>", "");
        assertThat(sinXmlns(despues)).isEqualTo(sinXmlns(antes));
        assertThat(despues).contains("<ext:ExtensionContent></ext:ExtensionContent>");
    }

    private static String sinXmlns(String xml) {
        return xml.replaceAll(" xmlns(:[a-z]+)?=\"[^\"]+\"", "");
    }

    @Test
    void elXmlFirmadoSigueSiendoCompactoYEnIso88591() {
        byte[] firmada = firmada();
        String texto = comoLatin1(firmada);

        assertThat(texto).startsWith("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"");
        assertThat(texto).doesNotContain("\n  <");
        assertThat(texto).doesNotContain("\r");
    }

    @Test
    void elXmlFirmadoSigueCumpliendoElEsquemaOficial() {
        byte[] firmada = firmada();
        assertThat(VALIDADOR.validarFactura(firmada)).isEmpty();
    }

    @Test
    void laFirmaRealCumpleElEsquemaConVariosItemsYValoresDecimales() {
        byte[] firmada = FIRMANTE.firmar(sinFirmar(
                item("Cuaderno A4 100 hojas", "3", "8.90"),
                item("Lapicero azul", "12", "1.50"),
                item("Servicio de delivery", "1", "5.00")), certificado);
        assertThat(VALIDADOR.validarFactura(firmada)).isEmpty();
        assertThat(verifica(firmada)).isTrue();
    }

    // --- ñ, tildes y caracteres especiales ---

    @Test
    void conEniesTildesYDieresisLaFirmaVerificaYRespetaIso88591() {
        byte[] firmada = FIRMANTE.firmar(sinFirmar(item("Camión ñandú áéíóú ü Ñ Pingüino", "1", "10.00")), certificado);

        assertThat(verifica(firmada)).isTrue();
        // los bytes están en ISO-8859-1 de verdad: "ñ" es un solo byte 0xF1, no los dos de UTF-8
        assertThat(firmada).contains((byte) 0xF1);
        assertThat(new String(firmada, StandardCharsets.ISO_8859_1)).contains("Camión ñandú áéíóú ü Ñ Pingüino");
        assertThat(VALIDADOR.validarFactura(firmada)).isEmpty();
    }

    @Test
    void conCaracteresReservadosDeXmlLaFirmaVerifica() {
        byte[] firmada = FIRMANTE.firmar(sinFirmar(item("Pan & Café <grande> \"nuevo\" 'x'", "1", "10.00")), certificado);
        assertThat(verifica(firmada)).isTrue();
        assertThat(VALIDADOR.validarFactura(firmada)).isEmpty();
    }

    @Test
    void cambiarUnaTildeDespuesDeFirmarRompeLaVerificacion() {
        byte[] firmada = FIRMANTE.firmar(sinFirmar(item("Camión", "1", "10.00")), certificado);
        assertThat(verifica(firmada)).isTrue();
        assertThat(verifica(reemplazar(firmada, "Camión", "Camion"))).isFalse();
    }

    // --- Tamaño y concurrencia ---

    @Test
    void unaFacturaDe999ItemsSeFirmaYVerificaEnPocosSegundos() {
        ItemRequest[] items = new ItemRequest[999];
        for (int i = 0; i < items.length; i++) {
            // precios distintos: con líneas iguales se acumula el redondeo (ver L-015)
            items[i] = item("Producto " + i, "1", BigDecimal.valueOf(10 + i * 0.37).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        }
        byte[] sin = sinFirmar(items);

        Instant inicio = Instant.now();
        byte[] firmada = FIRMANTE.firmar(sin, certificado);
        assertThat(Duration.between(inicio, Instant.now())).isLessThan(Duration.ofSeconds(10));

        assertThat(verifica(firmada)).isTrue();
        assertThat(VALIDADOR.validarFactura(firmada)).isEmpty();
    }

    @Test
    void variosHilosPuedenFirmarAlMismoTiempo() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<byte[]>> trabajos = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                String descripcion = "Producto " + i;
                trabajos.add(pool.submit(() -> FIRMANTE.firmar(sinFirmar(item(descripcion, "1", "10.00")), certificado)));
            }
            for (Future<byte[]> trabajo : trabajos) {
                assertThat(verifica(trabajo.get())).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Entradas inválidas ---

    @Test
    void sinXmlOSinCertificadoDaUnMensajeClaro() {
        assertThatThrownBy(() -> FIRMANTE.firmar(null, certificado)).isInstanceOf(FirmaException.class).hasMessage("No hay XML para firmar");
        assertThatThrownBy(() -> FIRMANTE.firmar(new byte[0], certificado)).isInstanceOf(FirmaException.class).hasMessage("No hay XML para firmar");
        assertThatThrownBy(() -> FIRMANTE.firmar(sinFirmar(), null)).isInstanceOf(FirmaException.class).hasMessage("No hay certificado para firmar");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "esto no es xml",
            "<Invoice>",
            "<a><b></a>",
            "<?xml version=\"1.0\"?><Invoice",
    })
    void unXmlMalFormadoDaUnMensajeClaro(String texto) {
        assertThatThrownBy(() -> FIRMANTE.firmar(texto.getBytes(StandardCharsets.UTF_8), certificado))
                .isInstanceOf(FirmaException.class)
                .hasMessageStartingWith("El XML no se puede leer");
    }

    @Test
    void unXmlSinElEspacioParaLaFirmaDaUnErrorClaro() {
        String sinEspacio = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"><x/></Invoice>";
        assertThatThrownBy(() -> FIRMANTE.firmar(sinEspacio.getBytes(StandardCharsets.ISO_8859_1), certificado))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("no tiene el espacio para la firma");
    }

    @Test
    void unXmlConDosEspaciosParaLaFirmaEsAmbiguo() {
        String texto = comoLatin1(sinFirmar());
        String doble = texto.replace("</ext:UBLExtensions>", "<ext:UBLExtension><ext:ExtensionContent/></ext:UBLExtension></ext:UBLExtensions>");
        assertThatThrownBy(() -> FIRMANTE.firmar(doble.getBytes(StandardCharsets.ISO_8859_1), certificado))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("más de un ext:ExtensionContent");
    }

    @Test
    void unXmlQueYaEstaFirmadoNoSeFirmaDeNuevo() {
        assertThatThrownBy(() -> FIRMANTE.firmar(firmada(), certificado))
                .isInstanceOf(FirmaException.class)
                .hasMessageContaining("ya tiene una firma");
    }

    @Test
    void unXmlConDoctypeSeRechazaYNoSeResuelveNingunaEntidadExterna() {
        String malicioso = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><!DOCTYPE Invoice [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">&x;</Invoice>";
        assertThatThrownBy(() -> FIRMANTE.firmar(malicioso.getBytes(StandardCharsets.ISO_8859_1), certificado))
                .isInstanceOf(FirmaException.class)
                .hasMessageStartingWith("El XML no se puede leer");
    }

    @Test
    void unXmlConSangriasTambienSeFirmaYVerifica() {
        // Si alguien le pasa un XML con saltos de línea y espacios, se firma tal como llegó
        String conSangrias = comoLatin1(sinFirmar()).replace("><cbc:UBLVersionID", ">\n  <cbc:UBLVersionID");
        byte[] firmada = FIRMANTE.firmar(conSangrias.getBytes(StandardCharsets.ISO_8859_1), certificado);
        assertThat(verifica(firmada)).isTrue();
    }
}
