package apisunat.api_sunat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaRequest {

    // El identificador único que pone quien pide la factura (por ejemplo el pedido de mi agente). Con el mismo valor dos veces
    // se obtiene la misma factura, no dos.
    @NotBlank(message = "El idExterno es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9._:-]{1,100}$", message = "El idExterno debe tener de 1 a 100 caracteres: letras, números, punto, guion, guion bajo o dos puntos")
    private String idExterno;

    @NotNull(message = "El cliente es obligatorio")
    @Valid
    private ClienteRequest cliente;

    // La SUNAT numera los ítems con hasta 3 dígitos
    @NotEmpty(message = "La factura necesita al menos un ítem")
    @Size(max = 999, message = "La factura no puede tener más de 999 ítems")
    @Valid
    private List<ItemRequest> items;
}
