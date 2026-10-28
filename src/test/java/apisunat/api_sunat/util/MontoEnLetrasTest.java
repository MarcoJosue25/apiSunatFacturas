package apisunat.api_sunat.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MontoEnLetrasTest {

    private static String letras(String monto) {
        return MontoEnLetras.convertir(new BigDecimal(monto));
    }

    // Casi 8.000 enteros generados con una librería independiente (num2words, normalizada): del 0 al 2000, alrededor de cada millar,
    // de cada potencia de diez y 4.000 al azar de 1 a 12 dígitos
    @Test
    void coincideConUnaLibreriaIndependienteEnCasiOchoMilNumeros() {
        List<String> fallos = new ArrayList<>();
        int total = 0;
        for (String linea : Fixtures.texto("letras/enteros.csv").split("\n")) {
            if (linea.isBlank() || linea.startsWith("#")) {
                continue;
            }
            String[] partes = linea.split(";");
            total++;
            String obtenido = letras(partes[0]);
            String esperado = partes[1] + " CON 00/100 SOLES";
            if (!obtenido.equals(esperado)) {
                fallos.add(partes[0] + ": esperado [" + esperado + "] pero fue [" + obtenido + "]");
            }
        }
        assertThat(total).isGreaterThan(7000);
        assertThat(fallos).as("diferencias con la librería independiente").isEmpty();
    }

    // --- Los ejemplos de la guía de la SUNAT y los casos clásicos ---

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1858.59 | MIL OCHOCIENTOS CINCUENTA Y OCHO CON 59/100 SOLES",
            "423225.00 | CUATROCIENTOS VEINTITRES MIL DOSCIENTOS VEINTICINCO CON 00/100 SOLES",
            "71354.99 | SETENTA Y UN MIL TRESCIENTOS CINCUENTA Y CUATRO CON 99/100 SOLES",
            "0 | CERO CON 00/100 SOLES",
            "0.00 | CERO CON 00/100 SOLES",
            "0.01 | CERO CON 01/100 SOLES",
            "0.99 | CERO CON 99/100 SOLES",
            "1 | UNO CON 00/100 SOLES",
            "15 | QUINCE CON 00/100 SOLES",
            "21 | VEINTIUNO CON 00/100 SOLES",
            "30 | TREINTA CON 00/100 SOLES",
            "31 | TREINTA Y UNO CON 00/100 SOLES",
            "99.99 | NOVENTA Y NUEVE CON 99/100 SOLES",
            "100 | CIEN CON 00/100 SOLES",
            "101 | CIENTO UNO CON 00/100 SOLES",
            "110 | CIENTO DIEZ CON 00/100 SOLES",
            "500 | QUINIENTOS CON 00/100 SOLES",
            "1000 | MIL CON 00/100 SOLES",
            "1001 | MIL UNO CON 00/100 SOLES",
            "2000 | DOS MIL CON 00/100 SOLES",
            "21000 | VEINTIUN MIL CON 00/100 SOLES",
            "100000 | CIEN MIL CON 00/100 SOLES",
            "101000 | CIENTO UN MIL CON 00/100 SOLES",
            "1000000 | UN MILLON CON 00/100 SOLES",
            "1000001 | UN MILLON UNO CON 00/100 SOLES",
            "2000000 | DOS MILLONES CON 00/100 SOLES",
            "21000000 | VEINTIUN MILLONES CON 00/100 SOLES",
            "1000000000 | MIL MILLONES CON 00/100 SOLES",
            "1001000000 | MIL UN MILLONES CON 00/100 SOLES",
            "999999999999.99 | NOVECIENTOS NOVENTA Y NUEVE MIL NOVECIENTOS NOVENTA Y NUEVE MILLONES NOVECIENTOS NOVENTA Y NUEVE MIL NOVECIENTOS NOVENTA Y NUEVE CON 99/100 SOLES"
    })
    void casosConocidos(String monto, String esperado) {
        assertThat(letras(monto.trim())).isEqualTo(esperado.trim());
    }

    // --- Los centavos ---

    @ParameterizedTest
    @CsvSource({"5.1, 10", "5.10, 10", "5.01, 01", "5.05, 05", "5.5, 50", "5.50, 50", "5.99, 99", "5.0, 00", "5, 00"})
    void losCentavosSiempreTienenDosDigitos(String monto, String centavos) {
        assertThat(letras(monto)).endsWith(" CON " + centavos + "/100 SOLES");
    }

    @Test
    void ceroDecimalesSobrantesNoCuentanComoDecimales() {
        assertThat(letras("5.1000")).isEqualTo("CINCO CON 10/100 SOLES");
        assertThat(letras("5.00000")).isEqualTo("CINCO CON 00/100 SOLES");
        assertThat(letras("5E+2")).isEqualTo("QUINIENTOS CON 00/100 SOLES");
    }

    // --- El resultado cumple el formato de la guía: mayúsculas, sin tildes y solo letras, espacios, dígitos y barra ---

    @ParameterizedTest
    @ValueSource(strings = {"16", "23", "26", "1000000", "999999.99", "12345.67", "1000000000", "0.5"})
    void elTextoSoloUsaMayusculasSinTildesNiCaracteresRaros(String monto) {
        assertThat(letras(monto)).matches("[A-Z0-9 /]+");
    }

    // --- Entradas inválidas ---

    @Test
    void sinMontoFalla() {
        assertThatThrownBy(() -> MontoEnLetras.convertir(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-0.01", "-1000000"})
    void unMontoNegativoSeRechaza(String monto) {
        assertThatThrownBy(() -> letras(monto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("negativo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.001", "0.005", "10.999", "1.23456789"})
    void masDeDosDecimalesSeRechaza(String monto) {
        assertThatThrownBy(() -> letras(monto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2 decimales");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000000000", "1000000000000.00", "999999999999.991", "9999999999999.99"})
    void unMontoMayorAlMaximoSeRechaza(String monto) {
        assertThatThrownBy(() -> letras(monto)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elMayorMontoPermitidoFunciona() {
        assertThat(letras("999999999999.99")).startsWith("NOVECIENTOS NOVENTA Y NUEVE MIL").endsWith("CON 99/100 SOLES");
    }
}
