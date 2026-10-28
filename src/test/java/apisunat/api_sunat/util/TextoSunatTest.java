package apisunat.api_sunat.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TextoSunatTest {

    @Test
    void nuloSigueSiendoNulo() {
        assertThat(TextoSunat.limpiar(null)).isNull();
    }

    @Test
    void unTextoNormalNoCambia() {
        assertThat(TextoSunat.limpiar("Polo básico negro talla M")).isEqualTo("Polo básico negro talla M");
        assertThat(TextoSunat.limpiar("")).isEmpty();
    }

    // --- Lo que sí existe en ISO-8859-1 se conserva ---

    @ParameterizedTest
    @ValueSource(strings = {"ñandú", "ÑANDÚ", "camión señal canción", "áéíóúü ÁÉÍÓÚÜ", "¿Qué tal? ¡Hola!", "N° 5 y 10°C", "Ramírez & Hijos",
            "«entre comillas»", "© 2026 - x", "a/b-c_d.e,f;g:h(i)[j]{k}+l=m*n%o$p#q@r", "ß ø æ å ç"})
    void lasLetrasYSignosDeLatin1SeConservan(String texto) {
        assertThat(TextoSunat.limpiar(texto)).isEqualTo(texto);
    }

    // --- Espacios en blanco: todos pasan a un espacio y los seguidos se juntan ---

    static Stream<Arguments> espacios() {
        return Stream.of(
                Arguments.of("a\nb", "a b"), Arguments.of("a\r\nb", "a b"), Arguments.of("a\tb", "a b"), Arguments.of("a\u000Bb", "a b"),
                Arguments.of("a\u000Cb", "a b"), Arguments.of("a\u0085b", "a b"), Arguments.of("a b", "a b"),
                Arguments.of("a b", "a b"), Arguments.of("a b", "a b"), Arguments.of("a b", "a b"),
                Arguments.of("a b", "a b"), Arguments.of("a　b", "a b"), Arguments.of("a\n\n\nb", "a b"),
                Arguments.of("a  \t \n  b", "a b"), Arguments.of("  a  ", "a"), Arguments.of("\n\ta\n", "a"), Arguments.of("   ", ""));
    }

    @ParameterizedTest
    @MethodSource("espacios")
    void todoEspacioEnBlancoSeVuelveUnEspacioYLosSeguidosSeJuntan(String entrada, String esperado) {
        assertThat(TextoSunat.limpiar(entrada)).isEqualTo(esperado);
    }

    @Test
    void losCaracteresDeControlSeDescartan() {
        assertThat(TextoSunat.limpiar("a\u0000b\u0001c\u001Fd\u007Fe\u0090f")).isEqualTo("abcdef");
    }

    @Test
    void unTextoSoloDeControlYEspaciosQuedaVacio() {
        assertThat(TextoSunat.limpiar("\u0000\n\t \u0001")).isEmpty();
    }

    // --- Parecidos en ASCII ---

    static Stream<Arguments> parecidos() {
        return Stream.of(
                Arguments.of("a—b", "a-b"), Arguments.of("a–b", "a-b"), Arguments.of("a−b", "a-b"), Arguments.of("a‐b", "a-b"),
                Arguments.of("“hola”", "\"hola\""), Arguments.of("‘hola’", "'hola'"), Arguments.of("„hola‟", "\"hola\""),
                Arguments.of("uno…", "uno..."), Arguments.of("10€", "10EUR"), Arguments.of("• item", "- item"),
                Arguments.of("marca™", "marcaTM"));
    }

    @ParameterizedTest
    @MethodSource("parecidos")
    void lasComillasGuionesYSimbolosSeCambianPorSuParecido(String entrada, String esperado) {
        assertThat(TextoSunat.limpiar(entrada)).isEqualTo(esperado);
    }

    // --- Lo que no existe en Latin-1 pasa a "?" uno por carácter ---

    @Test
    void unEmojiQuedaEnUnSoloSignoDePregunta() {
        assertThat(TextoSunat.limpiar("Hola 😀")).isEqualTo("Hola ?");
        assertThat(TextoSunat.limpiar("😀😀😀")).isEqualTo("???");
    }

    @Test
    void otrosAlfabetosYCaracteresRarosQuedanEnSignosDePregunta() {
        assertThat(TextoSunat.limpiar("日本語")).isEqualTo("???");
        assertThat(TextoSunat.limpiar("Привет")).isEqualTo("??????");
        assertThat(TextoSunat.limpiar("αβγ")).isEqualTo("???");
        assertThat(TextoSunat.limpiar("aŒb")).isEqualTo("a?b");   // Œ no está en Latin-1
        assertThat(TextoSunat.limpiar("₩")).isEqualTo("?");
    }

    @Test
    void unaMitadDeEmojiSueltaTambienSeCambia() {
        assertThat(TextoSunat.limpiar("a\uD83Db")).isEqualTo("a?b");
        assertThat(TextoSunat.limpiar("a\uDE00b")).isEqualTo("a?b");
    }

    // --- Normalización: la ñ y las tildes escritas en dos partes no se pierden ---

    @Test
    void lasLetrasConTildeEscritasEnDosPartesSeUnen() {
        String descompuesta = "ñandú camión é";   // ñ (n + tilde), ú, ó y una é en dos partes
        assertThat(TextoSunat.limpiar(descompuesta)).isEqualTo("ñandú camión é");
    }

    // --- Garantías generales con miles de textos al azar ---

    @Test
    void paraMilesDeTextosAleatoriosElResultadoSiempreEntraEnLatin1YNoTieneEspaciosRaros() {
        CharsetEncoder latin1 = StandardCharsets.ISO_8859_1.newEncoder();
        Random azar = new Random(2026);
        for (int i = 0; i < 20_000; i++) {
            StringBuilder sb = new StringBuilder();
            int largo = azar.nextInt(40);
            for (int j = 0; j < largo; j++) {
                switch (azar.nextInt(6)) {
                    case 0 -> sb.append((char) azar.nextInt(0x80));
                    case 1 -> sb.append((char) azar.nextInt(0x100));
                    case 2 -> sb.append((char) (0x100 + azar.nextInt(0x2000)));
                    case 3 -> sb.appendCodePoint(0x1F300 + azar.nextInt(0x300));
                    case 4 -> sb.append((char) (0xD800 + azar.nextInt(0x800)));
                    default -> sb.append((char) azar.nextInt(0x20));
                }
            }
            String limpio = TextoSunat.limpiar(sb.toString());

            assertThat(latin1.canEncode(limpio)).as("[" + sb + "] -> [" + limpio + "]").isTrue();
            assertThat(limpio).as("sin caracteres de control").doesNotContainPattern("[\\u0000-\\u001F\\u007F-\\u009F]");
            assertThat(limpio).as("sin espacios dobles ni en los bordes").doesNotContain("  ").isEqualTo(limpio.strip());
        }
    }

    @Test
    void limpiarDosVecesDaLoMismoQueUna() {
        Random azar = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 20; j++) {
                sb.append((char) azar.nextInt(0x2200));
            }
            String una = TextoSunat.limpiar(sb.toString());
            assertThat(TextoSunat.limpiar(una)).isEqualTo(una);
        }
    }
}
