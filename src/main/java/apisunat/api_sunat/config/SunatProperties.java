package apisunat.api_sunat.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Todo lo que hace falta para hablar con el servicio de la SUNAT. Si algo está mal, la aplicación no arranca.
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sunat")
public class SunatProperties {

    @NotBlank(message = "La URL del servicio de la SUNAT no puede estar vacía")
    @Pattern(regexp = "^https?://\\S+$", message = "La URL del servicio de la SUNAT debe empezar con http:// o https://")
    private String url;

    @NotBlank(message = "El RUC no puede estar vacío")
    @Pattern(regexp = "^\\d{11}$", message = "El RUC debe tener exactamente 11 dígitos")
    private String ruc;

    @NotBlank(message = "El usuario SOL no puede estar vacío")
    private String usuario;

    @NotBlank(message = "La clave SOL no puede estar vacía")
    private String clave;

    @Positive(message = "El timeout de conexión debe ser mayor que cero")
    private int conexionTimeoutMs;

    @Positive(message = "El timeout de lectura debe ser mayor que cero")
    private int lecturaTimeoutMs;
}
