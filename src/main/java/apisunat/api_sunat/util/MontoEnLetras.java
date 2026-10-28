package apisunat.api_sunat.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Escribe un monto en soles con letras para la leyenda 1000 de la factura, con el formato de la guía de la SUNAT:
// "MIL OCHOCIENTOS CINCUENTA Y OCHO CON 59/100 SOLES" (mayúsculas y sin tildes).
// Sigue la RAE: "veintiún mil", "un millón", "mil millones", "cien" y "ciento uno".
public final class MontoEnLetras {

    private static final BigDecimal MAXIMO = new BigDecimal("999999999999.99");

    private static final String[] BASICOS = {"CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISEIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE",
            "VEINTE", "VEINTIUNO", "VEINTIDOS", "VEINTITRES", "VEINTICUATRO", "VEINTICINCO", "VEINTISEIS", "VEINTISIETE",
            "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private MontoEnLetras() {
    }

    public static String convertir(BigDecimal monto) {
        if (monto == null) {
            throw new IllegalArgumentException("El monto es obligatorio");
        }
        if (monto.signum() < 0) {
            throw new IllegalArgumentException("El monto no puede ser negativo");
        }
        BigDecimal conDosDecimales;
        try {
            conDosDecimales = monto.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("El monto no puede tener más de 2 decimales");
        }
        if (conDosDecimales.compareTo(MAXIMO) > 0) {
            throw new IllegalArgumentException("El monto no puede pasar de " + MAXIMO.toPlainString());
        }

        long centavosTotales = conDosDecimales.movePointRight(2).longValueExact();
        long entero = centavosTotales / 100;
        long centavos = centavosTotales % 100;
        return enteroEnLetras(entero) + " CON " + String.format("%02d", centavos) + "/100 SOLES";
    }

    private static String enteroEnLetras(long n) {
        if (n == 0) {
            return "CERO";
        }
        long millones = n / 1_000_000;
        long resto = n % 1_000_000;
        StringBuilder sb = new StringBuilder();
        if (millones == 1) {
            sb.append("UN MILLON");
        } else if (millones > 1) {
            sb.append(hastaNovecientosNoventaYNueveMil(millones, true)).append(" MILLONES");
        }
        if (resto > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(hastaNovecientosNoventaYNueveMil(resto, false));
        }
        return sb.toString();
    }

    // De 1 a 999.999. Si el número va seguido de "millones", su último "uno" se vuelve "un" (mil un millones)
    private static String hastaNovecientosNoventaYNueveMil(long n, boolean seguidoDeMillones) {
        long miles = n / 1000;
        long unidades = n % 1000;
        StringBuilder sb = new StringBuilder();
        if (miles == 1) {
            sb.append("MIL");
        } else if (miles > 1) {
            sb.append(apocopar(hastaNovecientosNoventaYNueve((int) miles))).append(" MIL");
        }
        if (unidades > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String texto = hastaNovecientosNoventaYNueve((int) unidades);
            sb.append(seguidoDeMillones ? apocopar(texto) : texto);
        }
        return sb.toString();
    }

    private static String hastaNovecientosNoventaYNueve(int n) {
        if (n == 100) {
            return "CIEN";
        }
        StringBuilder sb = new StringBuilder();
        int centena = n / 100;
        int resto = n % 100;
        if (centena > 0) {
            sb.append(CENTENAS[centena]);
        }
        if (resto > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (resto < 30) {
                sb.append(BASICOS[resto]);
            } else {
                sb.append(DECENAS[resto / 10]);
                if (resto % 10 > 0) {
                    sb.append(" Y ").append(BASICOS[resto % 10]);
                }
            }
        }
        return sb.toString();
    }

    private static String apocopar(String texto) {
        return texto.endsWith("UNO") ? texto.substring(0, texto.length() - 3) + "UN" : texto;
    }
}
