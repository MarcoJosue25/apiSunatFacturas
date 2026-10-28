package apisunat.api_sunat.sunat.xml;

import java.math.BigDecimal;

// Una boleta tal como se informa en el resumen diario: su número, su cliente y sus importes (en soles, con 2 decimales)
public record LineaResumen(String numero, String tipoDocumentoCliente, String numeroDocumentoCliente,
                           BigDecimal totalGravado, BigDecimal totalIgv, BigDecimal importeTotal) {
}
