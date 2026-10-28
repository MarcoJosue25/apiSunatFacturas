package apisunat.api_sunat.exception;

// No se pudo firmar el XML: está vacío, está mal formado, no tiene el espacio de la firma o ya estaba firmado
public class FirmaException extends RuntimeException {

    public FirmaException(String mensaje) {
        super(mensaje);
    }

    public FirmaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
