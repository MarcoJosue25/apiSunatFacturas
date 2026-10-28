package apisunat.api_sunat.sunat.calculo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Calcula los números de una factura con IGV al 18% a partir de precios que ya incluyen IGV.
// Todo con BigDecimal y redondeo de mitad hacia arriba. Las fórmulas y por qué están en docs/sunat/CALCULOS.md
public final class CalculadoraIgv {

    private static final BigDecimal DIVISOR_IGV = new BigDecimal("1.18");
    private static final BigDecimal TASA_IGV = new BigDecimal("0.18");
    private static final BigDecimal UN_CENTAVO = new BigDecimal("0.01");
    // La SUNAT tolera una diferencia de 1 entre el IGV total y el 18% de la base (regla 3291). Nosotros no pasamos de la mitad.
    private static final BigDecimal MARGEN_IGV = new BigDecimal("0.50");
    // Lo mismo para el precio unitario (regla 3270): no nos alejamos más de 0.50 del que resulta de (valor de venta + IGV) / cantidad
    private static final BigDecimal MARGEN_PRECIO_UNITARIO = new BigDecimal("0.50");
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

        // El total se redondea a centavos y al dividirlo entre una cantidad muy chica ese redondeo se amplifica: con 0.001 unidades, medio
        // centavo de diferencia son 5 soles por unidad. Si el precio que llegó se aleja demasiado del que de verdad se cobró, se informa el cobrado.
        BigDecimal precioEfectivo = totalLinea.divide(cantidad, MAX_DECIMALES_UNITARIOS, RoundingMode.HALF_UP);
        BigDecimal precioInformado = precioEfectivo.subtract(precioUnitarioConIgv).abs().compareTo(MARGEN_PRECIO_UNITARIO) <= 0
                ? precioUnitarioConIgv : precioEfectivo;
        if (enteros(precioInformado) > MAX_ENTEROS) {
            throw new IllegalArgumentException("El precio unitario efectivo de la línea no puede tener más de " + MAX_ENTEROS + " dígitos enteros");
        }

        BigDecimal valorVenta = totalLinea.divide(DIVISOR_IGV, 2, RoundingMode.HALF_UP);
        return LineaCalculada.builder()
                .cantidad(cantidad)
                .precioUnitario(precioInformado)
                .valorUnitario(precioInformado.divide(DIVISOR_IGV, MAX_DECIMALES_UNITARIOS, RoundingMode.HALF_UP))
                .valorVenta(valorVenta)
                .igv(totalLinea.subtract(valorVenta))
                .totalLinea(totalLinea)
                .build();
    }

    public static TotalesDocumento calcularTotales(List<LineaCalculada> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("La factura necesita al menos una línea");
        }
        BigDecimal gravado = BigDecimal.ZERO.setScale(2);
        BigDecimal igv = BigDecimal.ZERO.setScale(2);
        BigDecimal importe = BigDecimal.ZERO.setScale(2);
        for (LineaCalculada linea : lineas) {
            if (linea == null) {
                throw new IllegalArgumentException("Hay una línea vacía en la factura");
            }
            gravado = gravado.add(linea.getValorVenta());
            igv = igv.add(linea.getIgv());
            importe = importe.add(linea.getTotalLinea());
        }
        if (enteros(importe) > MAX_ENTEROS) {
            throw new IllegalArgumentException("El importe total no puede tener más de " + MAX_ENTEROS + " dígitos enteros");
        }

        // El redondeo de cada línea se va acumulando. Con documentos normales son unos centavos, pero con muchísimas
        // líneas idénticas y desfavorables podría pasar la tolerancia de la SUNAT: en ese caso no se envía.
        BigDecimal igvEsperado = gravado.multiply(TASA_IGV).setScale(2, RoundingMode.HALF_UP);
        if (igv.subtract(igvEsperado).abs().compareTo(MARGEN_IGV) > 0) {
            throw new IllegalArgumentException("No se puede cuadrar el IGV de la factura con estas líneas: el redondeo acumulado pasa de " + MARGEN_IGV);
        }
        return TotalesDocumento.builder().totalGravado(gravado).totalIgv(igv).importeTotal(importe).build();
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
