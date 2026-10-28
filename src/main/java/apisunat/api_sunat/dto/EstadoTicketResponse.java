package apisunat.api_sunat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// El estado de un ticket tal como lo devuelve la SUNAT, sin interpretar:
// statusCode 0 = terminó, 98 = en proceso, 99 = terminó con errores; cualquier otro es un código de error de la SUNAT.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadoTicketResponse {

    private String statusCode;
    private String content;
}
