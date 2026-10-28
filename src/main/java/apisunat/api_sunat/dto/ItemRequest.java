package apisunat.api_sunat.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Una línea de la factura. El precio es el que paga el cliente, con IGV incluido.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRequest {

    private static final String UNIDAD_POR_DEFECTO = "NIU";

    @NotBlank(message = "La descripción del ítem no puede estar vacía")
    @Size(max = 500, message = "La descripción del ítem no puede pasar de 500 caracteres")
    private String descripcion;

    @NotNull(message = "La cantidad del ítem es obligatoria")
    @DecimalMin(value = "0", inclusive = false, message = "La cantidad del ítem debe ser mayor que cero")
    @Digits(integer = 12, fraction = 10, message = "La cantidad del ítem admite hasta 12 enteros y 10 decimales")
    private BigDecimal cantidad;

    @NotNull(message = "El precio unitario del ítem es obligatorio")
    @DecimalMin(value = "0", inclusive = false, message = "El precio unitario del ítem debe ser mayor que cero")
    @Digits(integer = 12, fraction = 10, message = "El precio unitario del ítem admite hasta 12 enteros y 10 decimales")
    private BigDecimal precioUnitario;

    // Código de la unidad de medida de la SUNAT (catálogo 03): NIU es "unidad (bienes)" y ZZ es "unidad (servicios)".
    // Si no viene, es NIU. El valor por defecto va en el getter y no en el campo porque al leer un JSON, Jackson usa el constructor
    // con todos los argumentos y deja null lo que no vino, sin respetar los valores por defecto del campo.
    @Pattern(regexp = "^[A-Z0-9]{1,3}$", message = "La unidad de medida debe ser un código de hasta 3 letras mayúsculas o números, como NIU")
    private String unidadMedida;

    public String getUnidadMedida() {
        return unidadMedida == null ? UNIDAD_POR_DEFECTO : unidadMedida;
    }

    // Opcional: el código que el negocio le da al producto
    @Size(max = 30, message = "El código del ítem no puede pasar de 30 caracteres")
    private String codigo;
}
