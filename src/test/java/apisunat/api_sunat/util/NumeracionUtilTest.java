package apisunat.api_sunat.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumeracionUtilTest {

    // --- Series válidas: las de la guía de la SUNAT (F001, FA01, FS21) y otras del mismo patrón ---

    @ParameterizedTest
    @ValueSource(strings = {"F001", "F002", "F999", "F000", "FA01", "FS21", "FZZZ", "F0A9", "FAAA", "F1B2", "F9ZZ"})
    void lasSeriesQueEmpiezanConFYTienenCuatroCaracteresSonValidas(String serie) {
        assertThat(NumeracionUtil.esSerieDeFactura(serie)).isTrue();
    }

    // --- Series inválidas ---

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "F00", "F0", "F", "F0011", "F00001",              // largo distinto de 4
            "f001", "Fa01", "FA0a",                            // minúsculas
            "B001", "0001", "1234", "G001", "E001", "T001",    // no empieza con F (la B es de boletas, el 0001 es de talonarios impresos)
            " F001", "F001 ", "F 01", "F0 1",                  // espacios
            "F001\n", "\nF001", "F00\n1",                      // saltos de línea (cuidado con $ en regex)
            "F-01", "F_01", "F.01", "F+01", "F/01",            // símbolos
            "FÑ01", "F0é1", "F٠٠١", "F00１",                   // letras o dígitos que no son ASCII
            "   ", "\t", "F001-1", "F001;DROP"})
    void lasSeriesQueNoCumplenElPatronSeRechazan(String serie) {
        assertThat(NumeracionUtil.esSerieDeFactura(serie)).isFalse();
    }

    // --- El número tal como va en el XML ---

    @ParameterizedTest
    @CsvSource({"F001, 1, F001-1", "F001, 4355, F001-4355", "F002, 10, F002-10", "FA01, 10, FA01-10", "FS21, 4370, FS21-4370",
            "F001, 697, F001-697", "F999, 99999999, F999-99999999", "F001, 12345678, F001-12345678"})
    void armaElNumeroSinCerosALaIzquierda(String serie, long correlativo, String esperado) {
        assertThat(NumeracionUtil.numeroFactura(serie, correlativo)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -100, 100_000_000L, 999_999_999L, Long.MAX_VALUE, Long.MIN_VALUE})
    void elCorrelativoDebeEstarEntreUnoYNoventaYNueveMillones(long correlativo) {
        assertThatThrownBy(() -> NumeracionUtil.numeroFactura("F001", correlativo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlativo");
    }

    @Test
    void elMaximoYElMinimoPermitidosFuncionan() {
        assertThat(NumeracionUtil.numeroFactura("F001", 1)).isEqualTo("F001-1");
        assertThat(NumeracionUtil.numeroFactura("F001", NumeracionUtil.MAX_CORRELATIVO)).isEqualTo("F001-99999999");
    }

    @Test
    void unaSerieInvalidaNoPermiteArmarElNumero() {
        assertThatThrownBy(() -> NumeracionUtil.numeroFactura("B001", 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("serie");
        assertThatThrownBy(() -> NumeracionUtil.numeroFactura(null, 1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("serie");
        assertThatThrownBy(() -> NumeracionUtil.numeroFactura("f001", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    // El resultado siempre cumple la regla oficial 1001 de la SUNAT: [F][A-Z0-9]{3}-[0-9]{1,8}
    @ParameterizedTest
    @CsvSource({"F001, 1", "FA01, 99999999", "FZ9Z, 4355", "F000, 12"})
    void elNumeroSiempreCumpleLaReglaOficial(String serie, long correlativo) {
        assertThat(NumeracionUtil.numeroFactura(serie, correlativo)).matches("F[A-Z0-9]{3}-[0-9]{1,8}");
    }

    // --- Nombre del archivo ---

    @ParameterizedTest
    @CsvSource({"20100066603,F001,1,20100066603-01-F001-1", "20100066603,F001,4355,20100066603-01-F001-4355",
            "10123456781,FA01,99999999,10123456781-01-FA01-99999999", "20512345678,FS21,12,20512345678-01-FS21-12"})
    void elNombreDelArchivoEsRucTipoSerieYNumeroSinCeros(String ruc, String serie, long correlativo, String esperado) {
        assertThat(NumeracionUtil.nombreArchivoFactura(ruc, serie, correlativo)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2010006660", "201000666033", "2010006660a", " 20100066603", "20100066603\n", "20100066603-"})
    void elNombreDelArchivoExigeUnRucDeOnceDigitos(String ruc) {
        assertThatThrownBy(() -> NumeracionUtil.nombreArchivoFactura(ruc, "F001", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUC");
    }

    @Test
    void elNombreDelArchivoRechazaUnaSerieOUnCorrelativoInvalidos() {
        assertThatThrownBy(() -> NumeracionUtil.nombreArchivoFactura("20100066603", "B001", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NumeracionUtil.nombreArchivoFactura("20100066603", "F001", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NumeracionUtil.nombreArchivoFactura("20100066603", "F001", 100_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }
}
