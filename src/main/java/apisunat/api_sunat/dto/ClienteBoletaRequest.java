package apisunat.api_sunat.dto;

import apisunat.api_sunat.validation.RucValidator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// El cliente de una boleta. Puede ser anónimo (sin documento) si la boleta no pasa de S/ 700.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteBoletaRequest {

    // Catálogo 06 de la SUNAT: 0 sin documento, 1 DNI, 4 carné de extranjería, 6 RUC, 7 pasaporte
    @Pattern(regexp = "^[01467]$", message = "El tipo de documento del cliente debe ser 0, 1, 4, 6 o 7")
    private String tipoDocumento;

    private String numeroDocumento;

    @Size(max = 1500, message = "El nombre del cliente no puede pasar de 1500 caracteres")
    private String nombre;

    // El tipo "sin documento" (o ningún tipo) significa cliente anónimo
    @JsonIgnore
    public boolean esAnonimo() {
        return tipoDocumento == null || tipoDocumento.isBlank() || "0".equals(tipoDocumento);
    }

    // Cada tipo de documento tiene su formato: DNI de 8 dígitos, RUC válido, carné y pasaporte alfanuméricos de hasta 12
    @JsonIgnore
    @AssertTrue(message = "El número de documento del cliente no corresponde a su tipo de documento")
    public boolean isDocumentoCoherente() {
        if (esAnonimo()) {
            return numeroDocumento == null || numeroDocumento.isBlank();
        }
        if (numeroDocumento == null) {
            return false;
        }
        return switch (tipoDocumento) {
            case "1" -> numeroDocumento.matches("^\\d{8}$");
            case "6" -> numeroDocumento.matches("^\\d{11}$") && RucValidator.esValido(numeroDocumento);
            case "4", "7" -> numeroDocumento.matches("^[A-Za-z0-9]{1,12}$");
            default -> true;   // los demás tipos ya los rechaza el @Pattern de tipoDocumento
        };
    }
}
