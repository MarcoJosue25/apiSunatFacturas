package apisunat.api_sunat.exception;

// El certificado digital no se puede usar para firmar: no está configurado, no existe, está dañado, tiene la clave mal,
// está vencido o no es del RUC del emisor. El mensaje explica cuál es el caso y nunca incluye la clave.
public class CertificadoInvalidoException extends RuntimeException {

    public CertificadoInvalidoException(String mensaje) {
        super(mensaje);
    }

    public CertificadoInvalidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
