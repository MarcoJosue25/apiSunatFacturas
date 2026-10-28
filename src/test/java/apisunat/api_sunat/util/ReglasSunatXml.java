package apisunat.api_sunat.util;

import org.w3c.dom.Document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static apisunat.api_sunat.util.FacturaXmlTestUtil.cantidad;
import static apisunat.api_sunat.util.FacturaXmlTestUtil.valor;

// Comprueba las reglas numéricas de la SUNAT (docs/sunat/CALCULOS.md) leyendo los números directamente del XML.
// No usa la calculadora del proyecto: así, si la calculadora y el armador se equivocaran juntos, esto lo notaría.
public final class ReglasSunatXml {

    private static final BigDecimal IGV = new BigDecimal("0.18");

    private ReglasSunatXml() {
    }

    private static BigDecimal n(Document d, String xpath) {
        String v = valor(d, xpath);
        if (v == null) {
            throw new AssertionError("No existe " + xpath);
        }
        return new BigDecimal(v);
    }

    private static void cerca(List<String> fallos, String regla, String que, BigDecimal a, BigDecimal b, BigDecimal tolerancia) {
        if (a.subtract(b).abs().compareTo(tolerancia) > 0) {
            fallos.add(regla + " " + que + ": " + a.toPlainString() + " contra " + b.toPlainString() + " (tolerancia " + tolerancia + ")");
        }
    }

    // La SUNAT tolera 1 en casi todo. Aquí se puede pedir una tolerancia más estricta.
    public static List<String> verificar(Document d, BigDecimal tolerancia) {
        List<String> fallos = new ArrayList<>();
        int lineas = cantidad(d, "/inv:Invoice/cac:InvoiceLine");
        BigDecimal sumaBases = BigDecimal.ZERO;
        BigDecimal sumaIgvLineas = BigDecimal.ZERO;

        for (int i = 1; i <= lineas; i++) {
            String l = "/inv:Invoice/cac:InvoiceLine[" + i + "]";
            BigDecimal cantidad = n(d, l + "/cbc:InvoicedQuantity");
            BigDecimal valorUnitario = n(d, l + "/cac:Price/cbc:PriceAmount");
            BigDecimal precioConIgv = n(d, l + "/cac:PricingReference/cac:AlternativeConditionPrice/cbc:PriceAmount");
            BigDecimal base = n(d, l + "/cbc:LineExtensionAmount");
            BigDecimal igvLinea = n(d, l + "/cac:TaxTotal/cbc:TaxAmount");
            BigDecimal baseImponible = n(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount");
            BigDecimal igvSubtotal = n(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxAmount");
            BigDecimal tasa = n(d, l + "/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory/cbc:Percent").movePointLeft(2);
            String donde = " (línea " + i + ")";

            cerca(fallos, "3271", "valor de venta = valor unitario x cantidad" + donde, base, valorUnitario.multiply(cantidad), tolerancia);
            if (baseImponible.compareTo(base) != 0) {
                fallos.add("3272 la base del IGV debe ser igual al valor de venta" + donde + ": " + baseImponible + " contra " + base);
            }
            cerca(fallos, "3103", "IGV = tasa x base" + donde, igvSubtotal, tasa.multiply(baseImponible), tolerancia);
            cerca(fallos, "3292", "total de tributos de la línea = suma de sus tributos" + donde, igvLinea, igvSubtotal, tolerancia);
            cerca(fallos, "3270", "precio unitario = (valor de venta + IGV) / cantidad" + donde, precioConIgv,
                    base.add(igvLinea).divide(cantidad, 10, RoundingMode.HALF_UP), tolerancia);

            if (tasa.compareTo(IGV) != 0) {
                fallos.add("3462 todas las líneas deben tener la misma tasa de IGV" + donde);
            }
            sumaBases = sumaBases.add(base);
            sumaIgvLineas = sumaIgvLineas.add(igvLinea);
            formato(fallos, base, 2, "valor de venta" + donde);
            formato(fallos, igvLinea, 2, "IGV de la línea" + donde);
            formato(fallos, valorUnitario, 10, "valor unitario" + donde);
            formato(fallos, precioConIgv, 10, "precio unitario" + donde);
            formato(fallos, cantidad, 10, "cantidad" + donde);
            if (!String.valueOf(i).equals(valor(d, l + "/cbc:ID"))) {
                fallos.add("Los ítems deben numerarse 1, 2, 3...: la línea " + i + " tiene el número " + valor(d, l + "/cbc:ID"));
            }
        }

        BigDecimal gravado = n(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxableAmount");
        BigDecimal igvSub = n(d, "/inv:Invoice/cac:TaxTotal/cac:TaxSubtotal/cbc:TaxAmount");
        BigDecimal totalTributos = n(d, "/inv:Invoice/cac:TaxTotal/cbc:TaxAmount");
        BigDecimal totalValorVenta = n(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:LineExtensionAmount");
        BigDecimal totalPrecioVenta = n(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:TaxInclusiveAmount");
        BigDecimal importe = n(d, "/inv:Invoice/cac:LegalMonetaryTotal/cbc:PayableAmount");

        cerca(fallos, "3277", "total gravado = suma de los valores de venta", gravado, sumaBases, tolerancia);
        cerca(fallos, "3278", "total valor de venta = suma de los valores de venta", totalValorVenta, sumaBases, tolerancia);
        cerca(fallos, "3291", "IGV total = 18% de la base", igvSub, IGV.multiply(gravado), tolerancia);
        cerca(fallos, "3294", "total de tributos = suma de los subtotales", totalTributos, igvSub, tolerancia);
        cerca(fallos, "3279", "total precio de venta = valor de venta + IGV", totalPrecioVenta, totalValorVenta.add(totalTributos), tolerancia);
        cerca(fallos, "3280", "importe total = total precio de venta", importe, totalPrecioVenta, tolerancia);
        cerca(fallos, "igv", "el IGV total sale de sumar el de las líneas", igvSub, sumaIgvLineas, tolerancia);
        for (BigDecimal monto : List.of(gravado, igvSub, totalTributos, totalValorVenta, totalPrecioVenta, importe)) {
            formato(fallos, monto, 2, "monto del documento");
        }
        return fallos;
    }

    private static void formato(List<String> fallos, BigDecimal valor, int maxDecimales, String que) {
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() > maxDecimales) {
            fallos.add("Formato: " + que + " tiene más de " + maxDecimales + " decimales (" + valor.toPlainString() + ")");
        }
        if (Math.max(limpio.precision() - limpio.scale(), 0) > 12) {
            fallos.add("Formato: " + que + " tiene más de 12 enteros (" + valor.toPlainString() + ")");
        }
    }
}
