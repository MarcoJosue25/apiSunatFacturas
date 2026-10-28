package apisunat.api_sunat.exception;

// La petición no se puede convertir en una factura (por ejemplo un monto en cero). El mensaje se le muestra a quien pidió la factura.
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
