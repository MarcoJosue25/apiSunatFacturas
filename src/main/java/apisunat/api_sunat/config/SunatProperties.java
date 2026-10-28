package apisunat.api_sunat.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @Valid
    @NotNull(message = "Faltan los datos del emisor")
    private Emisor emisor = new Emisor();

    // Los datos de quien emite la factura (el negocio). Lo opcional puede venir vacío.
    @Getter
    @Setter
    public static class Emisor {

        @NotBlank(message = "La razón social del emisor no puede estar vacía")
        @Size(max = 1500, message = "La razón social del emisor no puede pasar de 1500 caracteres")
        private String razonSocial;

        @Size(max = 1500, message = "El nombre comercial del emisor no puede pasar de 1500 caracteres")
        private String nombreComercial;

        @Pattern(regexp = "^(.{3,200})?$", message = "La dirección del emisor debe tener entre 3 y 200 caracteres")
        private String direccion;

        // Código del establecimiento anexo que asigna la SUNAT. El domicilio fiscal es 0000.
        @NotBlank(message = "El código de establecimiento no puede estar vacío")
        @Pattern(regexp = "^\\d{4}$", message = "El código de establecimiento debe tener 4 dígitos")
        private String codigoEstablecimiento = "0000";

        // En el archivo de configuración un dato opcional sin valor llega como texto vacío: se trata como si no existiera
        public String getNombreComercial() {
            return nombreComercial == null || nombreComercial.isBlank() ? null : nombreComercial;
        }

        public String getDireccion() {
            return direccion == null || direccion.isBlank() ? null : direccion;
        }
    }
}
