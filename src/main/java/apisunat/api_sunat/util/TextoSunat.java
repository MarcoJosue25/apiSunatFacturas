package apisunat.api_sunat.util;

import java.text.Normalizer;

// Deja un texto listo para el XML de la SUNAT, que va en ISO-8859-1 y, según las reglas de validación, no admite ningún espacio en blanco
// que no sea el espacio común (nada de saltos de línea ni tabulaciones).
// - Une las letras con tilde escritas en dos partes (la n y la tilde aparte) para que no se pierdan.
// - Los saltos de línea, tabulaciones y espacios raros pasan a un espacio, y los espacios seguidos quedan en uno solo.
// - Se quitan los caracteres de control.
// - Las comillas y guiones "elegantes", los puntos suspensivos y el euro se cambian por su parecido en ASCII.
// - Lo que no existe en ISO-8859-1 (emojis, caracteres de otros alfabetos) se cambia por "?", uno por cada carácter.
public final class TextoSunat {

    private TextoSunat() {
    }

    public static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(normalizado.length());
        normalizado.codePoints().forEach(cp -> agregar(sb, cp));
        return sb.toString().replaceAll(" {2,}", " ").strip();
    }

    private static void agregar(StringBuilder sb, int cp) {
        if (esEspacioEnBlanco(cp)) {
            sb.append(' ');
        } else if (cp < 0x20 || (cp >= 0x7F && cp <= 0x9F)) {
            // carácter de control: se descarta
        } else if (cp <= 0xFF) {
            sb.appendCodePoint(cp);
        } else {
            sb.append(parecidoEnAscii(cp));
        }
    }

    private static boolean esEspacioEnBlanco(int cp) {
        return cp == '\t' || cp == '\n' || cp == '\r' || cp == 0x0B || cp == 0x0C || cp == 0x85 || cp == 0xA0
                || cp == 0x2028 || cp == 0x2029 || (cp >= 0x2000 && cp <= 0x200A) || cp == 0x202F || cp == 0x205F || cp == 0x3000;
    }

    private static String parecidoEnAscii(int cp) {
        return switch (cp) {
            case 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2015, 0x2212 -> "-";
            case 0x2018, 0x2019, 0x201A, 0x201B, 0x2032 -> "'";
            case 0x201C, 0x201D, 0x201E, 0x201F, 0x2033 -> "\"";
            case 0x2026 -> "...";
            case 0x20AC -> "EUR";
            case 0x2022, 0x00B7 -> "-";
            case 0x2122 -> "TM";
            default -> "?";
        };
    }
}
