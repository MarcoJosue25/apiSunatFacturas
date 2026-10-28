package apisunat.api_sunat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Cómo responde la API cuando algo sale mal. El código de la SUNAT solo viaja cuando la SUNAT lo dio.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MensajeError {

    private String mensaje;
    private String codigoSunat;
}
