package apisunat.api_sunat.dto;

import apisunat.api_sunat.model.enums.EstadoComprobante;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Lo que se le cuenta a quien pidió la factura. No lleva el XML ni nada interno (para el XML hay otro endpoint).
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FacturaResponse {

    Long id;
    String idExterno;

    // Serie y número, por ejemplo F001-4355
    String numero;

    EstadoComprobante estado;
    LocalDateTime fechaEmision;
    BigDecimal importeTotal;

    // Lo que contestó la SUNAT, si ya contestó
    String codigoSunat;
    String descripcionSunat;
    List<String> observaciones;
}
