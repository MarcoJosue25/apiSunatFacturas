package apisunat.api_sunat.sunat.soap;

import lombok.Builder;
import lombok.Value;

// Lo que trae un getStatusResponse de la SUNAT. El content puede ser un texto de error o, si terminó bien, un ZIP en base64.
@Value
@Builder
public class RespuestaStatus {

    String statusCode;
    String content;
}
