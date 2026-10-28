package apisunat.api_sunat.exception;

// Lo que se pidió no se puede hacer con el estado actual del resumen. El mensaje se le muestra a quien lo pidió.
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
