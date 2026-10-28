package apisunat.api_sunat.dto;

import apisunat.api_sunat.validation.Ruc;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// El cliente de una factura: siempre tiene RUC (a quien no tiene se le emite boleta)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteRequest {

    @NotBlank(message = "El RUC del cliente no puede estar vacío")
    @Ruc(message = "El RUC del cliente no es válido")
    private String ruc;

    @NotBlank(message = "La razón social del cliente no puede estar vacía")
    @Size(max = 1500, message = "La razón social del cliente no puede pasar de 1500 caracteres")
    private String razonSocial;

    // Opcional
    @Size(min = 3, max = 200, message = "La dirección del cliente debe tener entre 3 y 200 caracteres")
    private String direccion;
}
