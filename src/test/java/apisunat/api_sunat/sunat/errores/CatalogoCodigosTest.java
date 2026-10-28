package apisunat.api_sunat.sunat.errores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoCodigosTest {

    private static final CatalogoCodigos CATALOGO = new CatalogoCodigos();

    // --- La tabla real ---

    @Test
    void cargaLos869CodigosDeLasReglasDeValidacion() {
        assertThat(CATALOGO.todos()).hasSize(869);
    }

    @Test
    void laCantidadPorRangoCoincideConLaDeLasNotas() {
        // docs/sunat/NOTAS.md: 14 en 0xxx, 19 en 1xxx, 678 en 2xxx-3xxx y 158 en 4xxx+
        long cero = CATALOGO.todos().stream().filter(c -> c.codigo().startsWith("0")).count();
        long uno = CATALOGO.todos().stream().filter(c -> c.codigo().startsWith("1")).count();
        long dosYTres = CATALOGO.todos().stream().filter(c -> c.codigo().startsWith("2") || c.codigo().startsWith("3")).count();
        long cuatroOMas = CATALOGO.todos().stream().filter(c -> Integer.parseInt(c.codigo()) >= 4000).count();
        assertThat(List.of(cero, uno, dosYTres, cuatroOMas)).containsExactly(14L, 19L, 678L, 158L);
    }

    @Test
    void todosLosCodigosTienenCuatroDigitosYUnaDescripcion() {
        assertThat(CATALOGO.todos()).allSatisfy(c -> {
            assertThat(c.codigo()).matches("\\d{4}");
            assertThat(c.descripcion()).isNotBlank();
            assertThat(c.hojas()).isNotBlank();
            assertThat(c.tipo()).isNotNull();
        });
    }

    @Test
    void laCantidadPorTipoCoincideConLaDeLaTabla() {
        var porTipo = CATALOGO.todos().stream().collect(Collectors.groupingBy(CodigoSunat::tipo, Collectors.counting()));
        assertThat(porTipo).containsEntry(CodigoSunat.Tipo.ERROR, 687L)
                .containsEntry(CodigoSunat.Tipo.OBSERVACION, 180L)
                .containsEntry(CodigoSunat.Tipo.ERROR_U_OBSERVACION, 2L);
    }

    @Test
    void busca0100ConSuDescripcionCompleta() {
        CodigoSunat c = CATALOGO.buscar("0100").orElseThrow();
        assertThat(c.tipo()).isEqualTo(CodigoSunat.Tipo.ERROR);
        assertThat(c.descripcion()).isEqualTo("El sistema no puede responder su solicitud. Intente nuevamente o comuníquese con su Administrador");
    }

    @Test
    void lasTildesSeLeenBien() {
        assertThat(CATALOGO.buscar("0109").orElseThrow().descripcion()).contains("autenticación");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "3094|El comprobante más \"código de operación del ítem\" no debe repetirse",
            "3330|Solo debe consignar información de percepciones si la forma de pago es \"Contado\"",
    })
    void losCamposConComillasDentroSeLeenCompletos(String codigo, String descripcion) {
        assertThat(CATALOGO.buscar(codigo).orElseThrow().descripcion()).isEqualTo(descripcion);
    }

    @Test
    void unaDescripcionConComasSeLeeEntera() {
        assertThat(CATALOGO.buscar("0127").orElseThrow().hojas()).contains(";");
        assertThat(CATALOGO.todos().stream().filter(c -> c.descripcion().contains(","))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2266", "2404", "2901", "2919", "2988", "3228", "4287"})
    void losCodigosDeObservacionTienenElTipoObservacion(String codigo) {
        assertThat(CATALOGO.buscar(codigo).orElseThrow().tipo()).isEqualTo(CodigoSunat.Tipo.OBSERVACION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2917", "3219"})
    void losDosCodigosQueDependenDelDocumentoTienenSuTipoPropio(String codigo) {
        assertThat(CATALOGO.buscar(codigo).orElseThrow().tipo()).isEqualTo(CodigoSunat.Tipo.ERROR_U_OBSERVACION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000", "0200", "0102", "9999", "100", "01000", " 0100", "0100 ", "abcd", ""})
    void unCodigoQueNoEstaEnLaTablaNoSeEncuentra(String codigo) {
        assertThat(CATALOGO.buscar(codigo)).isEmpty();
    }

    @Test
    void buscarNuloNoFallaYNoEncuentraNada() {
        assertThat(CATALOGO.buscar(null)).isEmpty();
    }

    @Test
    void laTablaNoSePuedeModificarDesdeAfuera() {
        assertThatThrownBy(() -> CATALOGO.todos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    // --- Archivos dañados: la aplicación no debe arrancar con una tabla mala ---

    private static void falla(String archivo, String mensaje) {
        assertThatThrownBy(() -> new CatalogoCodigos("/sunat/csv-malos/" + archivo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(mensaje);
    }

    @Test
    void sinElArchivoFallaConUnMensajeClaro() {
        falla("no-existe.csv", "No se encontró la tabla de códigos");
    }

    @Test
    void unArchivoVacioFalla() {
        falla("vacio.csv", "encabezado inesperado");
    }

    @Test
    void unArchivoSoloConEncabezadoFalla() {
        falla("solo-encabezado.csv", "está vacía");
    }

    @Test
    void unEncabezadoDistintoFalla() {
        falla("encabezado-distinto.csv", "encabezado inesperado");
    }

    @Test
    void unCodigoRepetidoFalla() {
        falla("repetido.csv", "el código 0100 está repetido");
    }

    @Test
    void unaFilaConMenosOMasColumnasFalla() {
        falla("columnas-de-menos.csv", "se esperaban 4 columnas y hay 3");
        falla("columnas-de-mas.csv", "se esperaban 4 columnas y hay 5");
    }

    @Test
    void unCodigoQueNoTieneCuatroDigitosFalla() {
        falla("codigo-corto.csv", "código inválido '100'");
        falla("codigo-con-letra.csv", "código inválido '01a0'");
    }

    @Test
    void unTipoDesconocidoFalla() {
        falla("tipo-desconocido.csv", "Tipo de código desconocido");
    }

    @Test
    void comillasSinCerrarFalla() {
        falla("comillas-sin-cerrar.csv", "comillas sin cerrar");
    }

    @Test
    void ignoraLineasEnBlancoYLeeComillasDobles() {
        CatalogoCodigos c = new CatalogoCodigos("/sunat/csv-malos/lineas-en-blanco-y-comillas.csv");
        assertThat(c.todos()).hasSize(2);
        assertThat(c.buscar("0100").orElseThrow().descripcion()).isEqualTo("Con coma, y \"comillas\"");
        assertThat(c.buscar("0111").orElseThrow().hojas()).isEqualTo("Hoja1;Hoja2");
    }

    // --- El separador de campos ---

    @Test
    void partirSeparaPorComasYRespetaLasComillas() {
        assertThat(CatalogoCodigos.partir("a,b,c", 1)).containsExactly("a", "b", "c");
        assertThat(CatalogoCodigos.partir("a,\"b,c\",d", 1)).containsExactly("a", "b,c", "d");
        assertThat(CatalogoCodigos.partir("a,\"di \"\"hola\"\"\",d", 1)).containsExactly("a", "di \"hola\"", "d");
        assertThat(CatalogoCodigos.partir("a,,c", 1)).containsExactly("a", "", "c");
        assertThat(CatalogoCodigos.partir("a,b,", 1)).containsExactly("a", "b", "");
        assertThat(CatalogoCodigos.partir("", 1)).containsExactly("");
    }
}
