package apisunat.api_sunat.sunat.soap;

import lombok.Builder;
import lombok.Value;

// Lo que trae un getStatusCdrResponse: el estado del comprobante en la SUNAT y, si ya lo tiene, su constancia (ZIP)
@Value
@Builder
public class RespuestaStatusCdr {

    // 0001 aceptado, 0002 rechazado, 0003 de baja, 0011 no existe... (ver docs/sunat/NOTAS.md)
    String statusCode;
    String statusMessage;

    // El ZIP de la constancia, ya decodificado; null si la SUNAT no la mandó
    byte[] constancia;
}
