package apisunat.api_sunat.sunat.calculo;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Calcula los números de una factura con IGV al 18% a partir de precios que ya incluyen IGV.
// Todo con BigDecimal y redondeo de mitad hacia arriba. Las fórmulas y por qué están en docs/sunat/CALCULOS.md
public final class CalculadoraIgv {

    private static final BigDecimal DIVISOR_IGV = new BigDecimal("1.18");
    private static final BigDecimal UN_CENTAVO = new BigDecimal("0.01");
    private static final int MAX_ENTEROS = 12;
    private static final int MAX_DECIMALES_UNITARIOS = 10;

    private CalculadoraIgv() {
    }

    public static LineaCalculada calcularLinea(BigDecimal precioUnitarioConIgv, BigDecimal cantidad) {
        validarEntrada("El precio unitario", precioUnitarioConIgv);
        validarEntrada("La cantidad", cantidad);

        BigDecimal totalLinea = precioUnitarioConIgv.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        if (totalLinea.compareTo(UN_CENTAVO) < 0) {
            throw new IllegalArgumentException("El total de la línea debe ser al menos 0.01");
        }
        if (enteros(totalLinea) > MAX_ENTEROS) {
            throw new IllegalArgumentException("El total de la línea no puede tener más de " + MAX_ENTEROS + " dígitos enteros");
        }

        BigDecimal valorVenta = totalLinea.divide(DIVISOR_IGV, 2, RoundingMode.HALF_UP);
        return LineaCalculada.builder()
                .cantidad(cantidad)
                .precioUnitario(precioUnitarioConIgv)
                .valorUnitario(precioUnitarioConIgv.divide(DIVISOR_IGV, MAX_DECIMALES_UNITARIOS, RoundingMode.HALF_UP))
                .valorVenta(valorVenta)
                .igv(totalLinea.subtract(valorVenta))
                .totalLinea(totalLinea)
                .build();
    }

    private static void validarEntrada(String nombre, BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException(nombre + " es obligatorio");
        }
        if (valor.signum() <= 0) {
            throw new IllegalArgumentException(nombre + " debe ser mayor que cero");
        }
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() > MAX_DECIMALES_UNITARIOS) {
            throw new IllegalArgumentException(nombre + " no puede tener más de " + MAX_DECIMALES_UNITARIOS + " decimales");
        }
        if (enteros(limpio) > MAX_ENTEROS) {
            throw new IllegalArgumentException(nombre + " no puede tener más de " + MAX_ENTEROS + " dígitos enteros");
        }
    }

    // Cuántos dígitos tiene la parte entera (un número menor que 1 tiene cero)
    private static int enteros(BigDecimal valor) {
        return Math.max(valor.precision() - valor.scale(), 0);
    }
}
