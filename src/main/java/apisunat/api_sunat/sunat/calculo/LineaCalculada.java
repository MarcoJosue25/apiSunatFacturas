package apisunat.api_sunat.sunat.calculo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

// Los números de una línea de la factura, ya calculados. Ver docs/sunat/CALCULOS.md
@Value
@Builder
public class LineaCalculada {

    // Tal como llegaron
    BigDecimal cantidad;
    BigDecimal precioUnitario;

    // Precio unitario sin IGV, con 10 decimales (el XML lo pide así)
    BigDecimal valorUnitario;

    // Base imponible de la línea (valor de venta del ítem), con 2 decimales
    BigDecimal valorVenta;

    // IGV de la línea, con 2 decimales
    BigDecimal igv;

    // Lo que paga el cliente por la línea: valorVenta + igv. Con 2 decimales.
    BigDecimal totalLinea;
}
