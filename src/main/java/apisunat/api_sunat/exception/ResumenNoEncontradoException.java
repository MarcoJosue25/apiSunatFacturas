package apisunat.api_sunat.exception;

public class ResumenNoEncontradoException extends RuntimeException {

    public ResumenNoEncontradoException(Long id) {
        super("No existe el resumen " + id);
    }
}
