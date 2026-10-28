package apisunat.api_sunat.exception;

public class ComprobanteNoEncontradoException extends RuntimeException {

    public ComprobanteNoEncontradoException(Long id) {
        super("No existe el comprobante " + id);
    }
}
