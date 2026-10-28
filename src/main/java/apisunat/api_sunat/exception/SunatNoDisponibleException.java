package apisunat.api_sunat.exception;

import lombok.Getter;

// No se pudo hablar con la SUNAT: no hubo conexión, no respondió a tiempo o contestó un error HTTP sin ningún detalle SOAP
@Getter
public class SunatNoDisponibleException extends RuntimeException {

    // true si la causa fue que se acabó el tiempo de espera (para distinguir "no responde" de "no hay conexión")
    private final boolean timeout;

    public SunatNoDisponibleException(String mensaje, boolean timeout, Throwable causa) {
        super(mensaje, causa);
        this.timeout = timeout;
    }
}
