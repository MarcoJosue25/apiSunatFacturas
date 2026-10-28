package apisunat.api_sunat.sunat.errores;

// Una fila de la tabla de códigos de las Reglas de validación de la SUNAT
public record CodigoSunat(String codigo, Tipo tipo, String hojas, String descripcion) {

    // ERROR: excepción o rechazo. OBSERVACION: el comprobante se acepta. ERROR_U_OBSERVACION: depende del tipo de documento.
    public enum Tipo {
        ERROR, OBSERVACION, ERROR_U_OBSERVACION;

        static Tipo desde(String texto) {
            return switch (texto) {
                case "ERROR" -> ERROR;
                case "OBSERV" -> OBSERVACION;
                case "ERROR/OBSERV" -> ERROR_U_OBSERVACION;
                default -> throw new IllegalArgumentException("Tipo de código desconocido en la tabla de la SUNAT: " + texto);
            };
        }
    }
}
