package apisunat.api_sunat.sunat.cdr;

import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CdrParserTest {

    private final CdrParser parser = new CdrParser();

    private static byte[] zipDe(String fixture) {
        return zipDeTexto("R-20150147718-01-FA01-981.xml", Fixtures.texto(fixture));
    }

    private static byte[] zipDeTexto(String nombre, String xml) {
        return ZipUtil.comprimir(nombre, xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static final String NS = "xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\""
            + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
            + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"";

    private static String cdr(String interior) {
        return "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><ar:ApplicationResponse " + NS + ">" + interior + "</ar:ApplicationResponse>";
    }

    private static String respuesta(String id, String codigo, String descripcion) {
        return "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>" + id + "</cbc:ReferenceID><cbc:ResponseCode>" + codigo
                + "</cbc:ResponseCode><cbc:Description>" + descripcion + "</cbc:Description></cac:Response></cac:DocumentResponse>";
    }

    // --- Los tres ejemplos del manual ---

    @Test
    void unaConstanciaAceptadaSeLeeConSuCodigoCeroYSuDescripcion() {
        CdrResultado r = parser.leer(zipDe("sunat/cdr/aceptado.xml"));

        assertThat(r.aceptado()).isTrue();
        assertThat(r.getCodigo()).isEqualTo("0");
        assertThat(r.getIdDocumento()).isEqualTo("FA01-981");
        assertThat(r.getDescripcion()).isEqualTo("La Factura numero FA01-981, ha sido aceptada");
        assertThat(r.getObservaciones()).isEmpty();
    }

    @Test
    void unaConstanciaAceptadaConObservacionesTraeCadaNotaConSuCodigo() {
        CdrResultado r = parser.leer(zipDe("sunat/cdr/aceptado-con-observaciones.xml"));

        assertThat(r.aceptado()).isTrue();
        assertThat(r.getObservaciones()).containsExactly(
                new CdrResultado.Observacion("4031", "Debe indicar el nombre comercial"),
                new CdrResultado.Observacion("4001", "El numero de RUC del receptor no existe."));
    }

    @Test
    void unaConstanciaRechazadaTraeElCodigoDelError() {
        CdrResultado r = parser.leer(zipDe("sunat/cdr/rechazado.xml"));

        assertThat(r.aceptado()).isFalse();
        assertThat(r.getCodigo()).isEqualTo("2047");
        assertThat(r.getIdDocumento()).isEqualTo("FT01-982");
        assertThat(r.getDescripcion()).isEqualTo("Es obligatorio al menos un AdditionalMonetaryTotal con codigo 1001, 1002 o 1003");
        assertThat(r.getObservaciones()).isEmpty();
    }

    // --- El ZIP ---

    @Test
    void ignoraLaCarpetaDummyDelZipDeLaSunat() {
        byte[] xml = Fixtures.texto("sunat/cdr/aceptado.xml").getBytes(StandardCharsets.ISO_8859_1);
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(salida)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("dummy/"));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("R-1.xml"));
            zip.write(xml);
            zip.closeEntry();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        assertThat(parser.leer(salida.toByteArray()).aceptado()).isTrue();
    }

    @Test
    void sinZipOConUnZipVacioFallaConUnMensajeClaro() {
        assertThatThrownBy(() -> parser.leer(null))
                .isInstanceOf(SunatRespuestaInvalidaException.class).hasMessageContaining("El ZIP está vacío");
        assertThatThrownBy(() -> parser.leer(new byte[0])).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    @Test
    void bytesQueNoSonUnZipFallanComoRespuestaInvalida() {
        assertThatThrownBy(() -> parser.leer("no soy un zip".getBytes()))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageStartingWith("La constancia de la SUNAT no se puede leer");
    }

    @Test
    void unZipSinXmlFallaComoRespuestaInvalida() {
        assertThatThrownBy(() -> parser.leer(ZipUtil.comprimir("leeme.txt", "hola".getBytes())))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("no tiene ningún archivo XML");
    }

    @Test
    void unZipCortadoALaMitadFallaComoRespuestaInvalida() {
        byte[] completo = zipDe("sunat/cdr/aceptado.xml");
        byte[] cortado = java.util.Arrays.copyOf(completo, completo.length / 2);
        assertThatThrownBy(() -> parser.leer(cortado)).isInstanceOf(SunatRespuestaInvalidaException.class);
    }

    // --- El XML ---

    @ParameterizedTest
    @ValueSource(strings = {"esto no es xml", "<ar:ApplicationResponse>", "<a><b></a>", ""})
    void unXmlMalFormadoFallaComoRespuestaInvalida(String xml) {
        byte[] zip = xml.isEmpty() ? ZipUtil.comprimir("r.xml", new byte[]{' '}) : zipDeTexto("r.xml", xml);
        assertThatThrownBy(() -> parser.leer(zip))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessage("La constancia de la SUNAT no es un XML válido");
    }

    @Test
    void unXmlQueNoEsUnApplicationResponseSeRechaza() {
        String factura = "<?xml version=\"1.0\"?><Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>";
        assertThatThrownBy(() -> parser.leer(zipDeTexto("r.xml", factura)))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessage("La constancia de la SUNAT no es un ApplicationResponse");
    }

    @Test
    void conElNombreCorrectoPeroOtroEspacioDeNombresSeRechaza() {
        String raro = "<ar:ApplicationResponse xmlns:ar=\"urn:otro\"/>";
        assertThatThrownBy(() -> parser.leer(zipDeTexto("r.xml", raro)))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessage("La constancia de la SUNAT no es un ApplicationResponse");
    }

    @Test
    void sinDocumentResponseSeRechaza() {
        assertThatThrownBy(() -> parser.leer(zipDeTexto("r.xml", cdr("<cbc:ID>1</cbc:ID>"))))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("DocumentResponse");
    }

    @Test
    void sinResponseDentroDelDocumentResponseSeRechaza() {
        assertThatThrownBy(() -> parser.leer(zipDeTexto("r.xml", cdr("<cac:DocumentResponse/>"))))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessageContaining("Response");
    }

    @Test
    void sinCodigoOSinDocumentoDeLaRespuestaSeRechaza() {
        String sinCodigo = cdr("<cac:DocumentResponse><cac:Response><cbc:ReferenceID>F001-1</cbc:ReferenceID></cac:Response></cac:DocumentResponse>");
        String sinReferencia = cdr("<cac:DocumentResponse><cac:Response><cbc:ResponseCode>0</cbc:ResponseCode></cac:Response></cac:DocumentResponse>");
        String codigoVacio = cdr(respuesta("F001-1", "  ", "x"));
        for (String xml : new String[]{sinCodigo, sinReferencia, codigoVacio}) {
            assertThatThrownBy(() -> parser.leer(zipDeTexto("r.xml", xml)))
                    .isInstanceOf(SunatRespuestaInvalidaException.class)
                    .hasMessage("La constancia de la SUNAT no trae el código ni el documento de la respuesta");
        }
    }

    @Test
    void unaConstanciaSinDescripcionSeLeeConDescripcionNula() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(
                "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>F001-1</cbc:ReferenceID><cbc:ResponseCode>0</cbc:ResponseCode></cac:Response></cac:DocumentResponse>")));
        assertThat(r.aceptado()).isTrue();
        assertThat(r.getDescripcion()).isNull();
    }

    @Test
    void unDoctypeSeRechazaYNoSeLeeNingunArchivo() {
        String malo = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>";
        assertThatThrownBy(() -> parser.leer(zipDeTexto("r.xml", malo)))
                .isInstanceOf(SunatRespuestaInvalidaException.class)
                .hasMessage("La constancia de la SUNAT no es un XML válido");
    }

    // --- Notas (observaciones) ---

    @Test
    void lasNotasSinCodigoSeGuardanConElTextoCompletoYCodigoNulo() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr("<cbc:Note>Aviso general sin código</cbc:Note>" + respuesta("F001-1", "0", "ok"))));
        assertThat(r.getObservaciones()).containsExactly(new CdrResultado.Observacion(null, "Aviso general sin código"));
    }

    @Test
    void lasNotasAceptanElGuionSinEspaciosOConVarios() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(
                "<cbc:Note>4252-Debe consignar el tipo</cbc:Note><cbc:Note>4287   -   Texto  con   espacios</cbc:Note>"
                        + respuesta("F001-1", "0", "ok"))));
        assertThat(r.getObservaciones()).containsExactly(
                new CdrResultado.Observacion("4252", "Debe consignar el tipo"),
                new CdrResultado.Observacion("4287", "Texto con espacios"));
    }

    @Test
    void unaNotaVaciaSeIgnora() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr("<cbc:Note>  </cbc:Note><cbc:Note/>" + respuesta("F001-1", "0", "ok"))));
        assertThat(r.getObservaciones()).isEmpty();
    }

    @Test
    void soloSeLeenLasNotasDeLaRaizYNoLasQueEstenDentroDeOtrosElementos() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(
                "<cbc:Note>4031 - De la raíz</cbc:Note>"
                        + "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>F001-1</cbc:ReferenceID><cbc:ResponseCode>0</cbc:ResponseCode>"
                        + "<cbc:Description>ok</cbc:Description></cac:Response><cbc:Note>9999 - Anidada</cbc:Note></cac:DocumentResponse>")));
        assertThat(r.getObservaciones()).extracting(CdrResultado.Observacion::getCodigo).containsExactly("4031");
    }

    @Test
    void unaNotaConCodigoDeTresODeCincoDigitosNoSeTomaComoCodigo() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(
                "<cbc:Note>403 - Corto</cbc:Note><cbc:Note>40311 - Largo</cbc:Note>" + respuesta("F001-1", "0", "ok"))));
        assertThat(r.getObservaciones()).extracting(CdrResultado.Observacion::getCodigo).containsExactly(null, null);
    }

    // --- Tildes y codificación ---

    @Test
    void lasTildesDeLaDescripcionEnIso88591SeLeenBien() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(respuesta("F001-1", "2335", "El número de documento añadió ñandú"))));
        assertThat(r.getDescripcion()).isEqualTo("El número de documento añadió ñandú");
    }

    @Test
    void unaConstanciaEnUtf8TambienSeLeeSiAsiLoDeclara() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ar:ApplicationResponse " + NS + ">"
                + respuesta("F001-1", "0", "Número ñ") + "</ar:ApplicationResponse>";
        CdrResultado r = parser.leer(ZipUtil.comprimir("r.xml", xml.getBytes(StandardCharsets.UTF_8)));
        assertThat(r.getDescripcion()).isEqualTo("Número ñ");
    }

    // --- Resultado ---

    @ParameterizedTest
    @ValueSource(strings = {"1", "00", "0 1", "2047", "x"})
    void soloElCodigoCeroEsAceptado(String codigo) {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(respuesta("F001-1", codigo, "x"))));
        assertThat(r.aceptado()).isFalse();
    }

    @Test
    void elCodigoCeroConEspaciosAlrededorSigueSiendoAceptado() {
        assertThat(parser.leer(zipDeTexto("r.xml", cdr(respuesta("F001-1", " 0 \n", "x")))).aceptado()).isTrue();
    }

    @Test
    void siHayVariosDocumentResponseSeUsaElPrimero() {
        CdrResultado r = parser.leer(zipDeTexto("r.xml", cdr(respuesta("F001-1", "0", "primero") + respuesta("F001-2", "2047", "segundo"))));
        assertThat(r.getIdDocumento()).isEqualTo("F001-1");
        assertThat(r.aceptado()).isTrue();
    }
}
