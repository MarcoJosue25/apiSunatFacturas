package apisunat.api_sunat.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipUtilTest {

    private static final byte[] XML = "<?xml version=\"1.0\"?><Invoice>áéíóú ñ</Invoice>".getBytes(StandardCharsets.ISO_8859_1);

    private static byte[] zip(String... nombresYContenidos) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (int i = 0; i < nombresYContenidos.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nombresYContenidos[i]));
                if (!nombresYContenidos[i].endsWith("/")) {
                    zip.write(nombresYContenidos[i + 1].getBytes(StandardCharsets.ISO_8859_1));
                }
                zip.closeEntry();
            }
        }
        return salida.toByteArray();
    }

    private static List<String> nombres(byte[] zip) throws IOException {
        List<String> nombres = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                nombres.add(e.getName());
            }
        }
        return nombres;
    }

    // --- Comprimir ---

    @Test
    void elZipTieneExactamenteUnArchivoConElNombreIndicado() throws Exception {
        byte[] zip = ZipUtil.comprimir("20100066603-01-F001-1.xml", XML);
        assertThat(nombres(zip)).containsExactly("20100066603-01-F001-1.xml");
    }

    @Test
    void alDescomprimirSeRecuperanLosMismosBytes() {
        byte[] zip = ZipUtil.comprimir("a.xml", XML);
        ZipUtil.Entrada entrada = ZipUtil.leerXmlUnico(zip);
        assertThat(entrada.nombre()).isEqualTo("a.xml");
        assertThat(entrada.contenido()).isEqualTo(XML);
    }

    @Test
    void conContenidoAleatorioDeVariosTamanosSeRecuperaIgual() {
        Random azar = new Random(20260428L);
        for (int largo : new int[]{1, 2, 100, 4096, 65_536, 300_000}) {
            byte[] datos = new byte[largo];
            azar.nextBytes(datos);
            assertThat(ZipUtil.leerXmlUnico(ZipUtil.comprimir("x.xml", datos)).contenido()).as("largo " + largo).isEqualTo(datos);
        }
    }

    @Test
    void elMismoContenidoDaSiempreElMismoZip() throws Exception {
        byte[] primero = ZipUtil.comprimir("a.xml", XML);
        Thread.sleep(2200);   // el ZIP guarda la hora de a 2 segundos: si la fecha real se colara, los bytes cambiarían
        assertThat(ZipUtil.comprimir("a.xml", XML)).isEqualTo(primero);
    }

    @Test
    void elZipEmpiezaConLaFirmaDeUnArchivoZip() {
        byte[] zip = ZipUtil.comprimir("a.xml", XML);
        assertThat(zip[0]).isEqualTo((byte) 'P');
        assertThat(zip[1]).isEqualTo((byte) 'K');
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "carpeta/a.xml", "carpeta\\a.xml", "/a.xml", "../a.xml"})
    void unNombreDeArchivoInvalidoSeRechaza(String nombre) {
        assertThatThrownBy(() -> ZipUtil.comprimir(nombre, XML))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre del archivo del ZIP no es válido");
    }

    @Test
    void sinContenidoNoSeComprime() {
        assertThatThrownBy(() -> ZipUtil.comprimir("a.xml", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZipUtil.comprimir("a.xml", new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Leer el XML de un ZIP (la constancia que devuelve la SUNAT) ---

    @Test
    void ignoraLaCarpetaDummyQueAVecesAgregaLaSunat() throws Exception {
        byte[] zip = zip("dummy/", "", "R-20100066603-01-F001-1.xml", "<cdr/>");
        ZipUtil.Entrada entrada = ZipUtil.leerXmlUnico(zip);
        assertThat(entrada.nombre()).isEqualTo("R-20100066603-01-F001-1.xml");
        assertThat(new String(entrada.contenido(), StandardCharsets.ISO_8859_1)).isEqualTo("<cdr/>");
    }

    @Test
    void unaCarpetaCuyoNombreTerminaEnXmlNoCuentaComoArchivo() throws Exception {
        ZipUtil.Entrada entrada = ZipUtil.leerXmlUnico(zip("carpeta.xml/", "", "R-1.xml", "<cdr/>"));
        assertThat(entrada.nombre()).isEqualTo("R-1.xml");
    }

    @Test
    void ignoraArchivosQueNoSonXml() throws Exception {
        ZipUtil.Entrada entrada = ZipUtil.leerXmlUnico(zip("leeme.txt", "hola", "R-1.xml", "<cdr/>"));
        assertThat(entrada.nombre()).isEqualTo("R-1.xml");
    }

    @Test
    void laExtensionXmlSeReconoceEnMayusculasTambien() throws Exception {
        assertThat(ZipUtil.leerXmlUnico(zip("R-1.XML", "<cdr/>")).nombre()).isEqualTo("R-1.XML");
    }

    @Test
    void conMasDeUnXmlFalla() throws Exception {
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(zip("a.xml", "<a/>", "b.xml", "<b/>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El ZIP tiene más de un archivo XML");
    }

    @Test
    void sinNingunXmlFalla() throws Exception {
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(zip("leeme.txt", "hola")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El ZIP no tiene ningún archivo XML");
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(zip("dummy/", "")))
                .hasMessage("El ZIP no tiene ningún archivo XML");
    }

    @Test
    void unZipVacioONuloFalla() {
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("El ZIP está vacío");
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(new byte[0])).isInstanceOf(IllegalArgumentException.class).hasMessage("El ZIP está vacío");
    }

    @Test
    void unZipSinArchivosNoTieneXml() throws Exception {
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(zip()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El ZIP no tiene ningún archivo XML");
    }

    @ParameterizedTest
    @ValueSource(strings = {"esto no es un zip", "PK", "PK\u0003\u0004basura"})
    void bytesQueNoSonUnZipFallanConUnMensajeClaro(String texto) {
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(texto.getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageMatching("El ZIP (está dañado o no es un ZIP|no tiene ningún archivo XML)");
    }

    @Test
    void unZipCortadoALaMitadFalla() {
        byte[] completo = ZipUtil.comprimir("a.xml", new byte[10_000]);
        byte[] cortado = java.util.Arrays.copyOf(completo, completo.length / 2);
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(cortado)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unZipBombaQueSeExpandeMasDelTopeSeRechaza() {
        // 6 MB de ceros se comprimen a unos pocos KB; descomprimidos pasan del tope de 5 MB
        byte[] zip = ZipUtil.comprimir("bomba.xml", new byte[6 * 1024 * 1024]);
        assertThat(zip.length).isLessThan(100_000);
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasa de " + ZipUtil.MAX_BYTES_DESCOMPRIMIDOS + " bytes");
    }

    @Test
    void elTopeSeCuentaSobreElTotalDeTodosLosArchivosYNoPorArchivo() throws Exception {
        String tres = "a".repeat(2 * 1024 * 1024);
        assertThatThrownBy(() -> ZipUtil.leerXmlUnico(zip("a.txt", tres, "b.txt", tres, "c.txt", tres)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasa de");
    }

    @Test
    void unArchivoJustoEnElTopeSeAcepta() {
        byte[] justo = new byte[ZipUtil.MAX_BYTES_DESCOMPRIMIDOS];
        assertThat(ZipUtil.leerXmlUnico(ZipUtil.comprimir("justo.xml", justo)).contenido()).hasSize(ZipUtil.MAX_BYTES_DESCOMPRIMIDOS);
    }
}
