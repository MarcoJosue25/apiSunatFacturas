package apisunat.api_sunat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "El cliente es obligatorio")
    @Valid
    private ClienteRequest cliente;

    // La SUNAT numera los ítems con hasta 3 dígitos
    @NotEmpty(message = "La factura necesita al menos un ítem")
    @Size(max = 999, message = "La factura no puede tener más de 999 ítems")
    @Valid
    private List<ItemRequest> items;
}
