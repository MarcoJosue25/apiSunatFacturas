package apisunat.api_sunat.dto;

import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import lombok.Builder;
import lombok.Value;

import java.util.List;

// Lo que pasó al enviar una factura a la SUNAT: la clase del resultado y lo que la SUNAT dijo (en una constancia o en un error SOAP)
@Value
@Builder
public class ResultadoEmision {

    // Por ejemplo 20100066603-01-F001-1
    String nombreArchivo;

    ClaseResultado clase;

    // "0" si la SUNAT lo aceptó; si no, el código del error
    String codigo;

    String descripcion;

    // Las advertencias de una constancia aceptada
    List<CdrResultado.Observacion> observaciones;

    // El XML tal como se envió (firmado), para guardarlo
    byte[] xmlFirmado;
}
