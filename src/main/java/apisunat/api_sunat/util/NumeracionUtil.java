package apisunat.api_sunat.util;

import java.util.regex.Pattern;

// Serie y correlativo de una factura electrónica. La regla de la SUNAT (código 1001) es [F][A-Z0-9]{3}-[0-9]{1,8}:
// la serie tiene 4 caracteres, empieza con F y solo lleva mayúsculas y dígitos; el correlativo tiene hasta 8 dígitos y empieza en 1.
public final class NumeracionUtil {

    public static final long MAX_CORRELATIVO = 99_999_999L;

    private static final Pattern SERIE_FACTURA = Pattern.compile("F[A-Z0-9]{3}");
    private static final Pattern SERIE_BOLETA = Pattern.compile("B[A-Z0-9]{3}");
    private static final Pattern RUC = Pattern.compile("\\d{11}");

    private NumeracionUtil() {
    }

    public static boolean esSerieDeFactura(String serie) {
        return serie != null && SERIE_FACTURA.matcher(serie).matches();
    }

    public static boolean esSerieDeBoleta(String serie) {
        return serie != null && SERIE_BOLETA.matcher(serie).matches();
    }

    // El número de una boleta: la regla de la SUNAT es ([B][A-Z0-9]{3})-(?!0+$)([0-9]{1,8}), igual que la factura pero con serie B
    public static String numeroBoleta(String serie, long correlativo) {
        if (!esSerieDeBoleta(serie)) {
            throw new IllegalArgumentException("La serie de una boleta debe tener 4 caracteres, empezar con B y llevar solo mayúsculas y números");
        }
        if (correlativo < 1 || correlativo > MAX_CORRELATIVO) {
            throw new IllegalArgumentException("El correlativo debe estar entre 1 y " + MAX_CORRELATIVO);
        }
        return serie + "-" + correlativo;
    }

    // El nombre del archivo de una boleta (que se guarda y se entrega aunque a la SUNAT solo viaje el resumen): 20100066603-03-B001-12
    public static String nombreArchivoBoleta(String ruc, String serie, long correlativo) {
        if (ruc == null || !RUC.matcher(ruc).matches()) {
            throw new IllegalArgumentException("El RUC del nombre del archivo debe tener 11 dígitos");
        }
        return ruc + "-03-" + numeroBoleta(serie, correlativo);
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

    // El nombre del archivo que pide la SUNAT: RUC-tipo de documento-serie-correlativo, por ejemplo 20100066603-01-F001-4355.
    // El tipo 01 es factura. El ZIP y el XML que lleva adentro se llaman igual (cambia solo la extensión).
    public static String nombreArchivoFactura(String ruc, String serie, long correlativo) {
        if (ruc == null || !RUC.matcher(ruc).matches()) {
            throw new IllegalArgumentException("El RUC del nombre del archivo debe tener 11 dígitos");
        }
        return ruc + "-01-" + numeroFactura(serie, correlativo);
    }
}
