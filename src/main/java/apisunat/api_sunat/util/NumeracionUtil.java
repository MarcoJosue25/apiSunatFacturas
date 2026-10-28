package apisunat.api_sunat.util;

import java.util.regex.Pattern;

// Serie y correlativo de una factura electrónica. La regla de la SUNAT (código 1001) es [F][A-Z0-9]{3}-[0-9]{1,8}:
// la serie tiene 4 caracteres, empieza con F y solo lleva mayúsculas y dígitos; el correlativo tiene hasta 8 dígitos y empieza en 1.
public final class NumeracionUtil {

    public static final long MAX_CORRELATIVO = 99_999_999L;

    private static final Pattern SERIE_FACTURA = Pattern.compile("F[A-Z0-9]{3}");

    private NumeracionUtil() {
    }

    public static boolean esSerieDeFactura(String serie) {
        return serie != null && SERIE_FACTURA.matcher(serie).matches();
    }

    // El número tal como va en el XML y en el nombre del archivo, sin ceros a la izquierda: F001-4355
    public static String numeroFactura(String serie, long correlativo) {
        if (!esSerieDeFactura(serie)) {
            throw new IllegalArgumentException("La serie de una factura debe tener 4 caracteres, empezar con F y llevar solo mayúsculas y números");
        }
        if (correlativo < 1 || correlativo > MAX_CORRELATIVO) {
            throw new IllegalArgumentException("El correlativo debe estar entre 1 y " + MAX_CORRELATIVO);
        }
        return serie + "-" + correlativo;
    }
}
