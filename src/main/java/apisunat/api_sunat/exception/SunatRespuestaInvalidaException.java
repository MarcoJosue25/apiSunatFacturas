package apisunat.api_sunat.exception;

// La SUNAT contestó algo que no se puede leer: no es XML, no es SOAP o le falta un dato que siempre debería traer
public class SunatRespuestaInvalidaException extends RuntimeException {

    public SunatRespuestaInvalidaException(String mensaje) {
        super(mensaje);
    }

    public SunatRespuestaInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
