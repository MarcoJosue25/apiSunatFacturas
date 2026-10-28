package apisunat.api_sunat.sunat.calculo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

// Los totales de la factura, con 2 decimales. Ver docs/sunat/CALCULOS.md
@Value
@Builder
public class TotalesDocumento {

    // Suma de las bases de las líneas: total de operaciones gravadas (y total valor de venta)
    BigDecimal totalGravado;

    // Suma del IGV de las líneas
    BigDecimal totalIgv;

    // Lo que paga el cliente: suma de los totales de las líneas (= totalGravado + totalIgv)
    BigDecimal importeTotal;
}
