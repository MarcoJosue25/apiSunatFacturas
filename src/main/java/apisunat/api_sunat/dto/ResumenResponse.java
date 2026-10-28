package apisunat.api_sunat.dto;

import apisunat.api_sunat.model.enums.EstadoResumen;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

// Lo que se cuenta de un resumen diario: cómo va y cuántas boletas lleva. No incluye el XML ni la constancia.
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumenResponse {

    Long id;

    // RC-AAAAMMDD-N
    String nombre;

    EstadoResumen estado;

    // La fecha de emisión de las boletas que lleva
    LocalDate fechaReferencia;

    // El ticket que dio la SUNAT al recibirlo
    String ticket;

    String codigoSunat;
    String descripcionSunat;
    int cantidadBoletas;
}
