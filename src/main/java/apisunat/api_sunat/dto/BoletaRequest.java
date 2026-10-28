package apisunat.api_sunat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class BoletaRequest {

    // El identificador único que pone quien pide la boleta (por ejemplo el pedido de mi agente): repetirlo devuelve la misma boleta
    @NotBlank(message = "El idExterno es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9._:-]{1,100}$", message = "El idExterno debe tener de 1 a 100 caracteres: letras, números, punto, guion, guion bajo o dos puntos")
    private String idExterno;

    // Opcional: sin cliente la boleta es anónima (solo se permite hasta S/ 700)
    @Valid
    private ClienteBoletaRequest cliente;

    @NotEmpty(message = "La boleta necesita al menos un ítem")
    @Size(max = 999, message = "La boleta no puede tener más de 999 ítems")
    @Valid
    private List<ItemRequest> items;
}
