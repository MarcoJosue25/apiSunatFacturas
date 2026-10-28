package apisunat.api_sunat.exception;

import lombok.Getter;

// La SUNAT contestó con un error SOAP (Fault). Ojo: lo manda con HTTP 200, así que solo se ve leyendo el cuerpo.
@Getter
public class SunatFaultException extends RuntimeException {

    private final String faultCode;
    private final String faultString;
    // Código de 4 dígitos de la SUNAT (por ejemplo "0200") si se pudo sacar del mensaje, o null
    private final String codigo;

    public SunatFaultException(String faultCode, String faultString, String codigo) {
        super("La SUNAT respondió con un error SOAP" + (codigo != null ? " " + codigo : "") + ": " + faultString);
        this.faultCode = faultCode;
        this.faultString = faultString;
        this.codigo = codigo;
    }
}
