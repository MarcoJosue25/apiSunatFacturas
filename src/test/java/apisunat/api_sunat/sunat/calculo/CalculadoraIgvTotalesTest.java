package apisunat.api_sunat.sunat.calculo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraIgvTotalesTest {

    private static final BigDecimal TASA = new BigDecimal("0.18");
    private static final BigDecimal UNO = BigDecimal.ONE;

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static List<LineaCalculada> lineas(String descripcion) {
        List<LineaCalculada> lista = new ArrayList<>();
        for (String par : descripcion.split(";")) {
            String[] pq = par.trim().split(":");
            lista.add(CalculadoraIgv.calcularLinea(bd(pq[0]), bd(pq[1])));
        }
        return lista;
    }

    // Los esperados salen de un cálculo independiente (Python con Decimal)
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "35.00:2;25.00:1 | 80.51 | 14.49 | 95.00",
            "10.00:1;10.00:1;10.00:1 | 25.41 | 4.59 | 30.00",
            "29.90:2;49.90:12;8.99:100 | 1320.00 | 237.60 | 1557.60",
            "0.05:1;0.05:1;0.05:1;0.05:1;0.05:1;0.05:1;0.05:1;0.05:1;0.05:1;0.05:1 | 0.40 | 0.10 | 0.50",
            "1.005:1;0.015:1 | 0.88 | 0.15 | 1.03",
            "100:1;250:3;17.5:0.25 | 724.05 | 130.33 | 854.38",
            "7.77:13;0.333:3;4.5:4.5 | 103.61 | 18.65 | 122.26",
            "197.73:26;853.20:4;94.95:35;123.38:24;763.88:4;665.11:14;49.15:6;568.39:27;91.57:16;118.90:36;556.43:4;741.16:8 | 48090.80 | 8656.36 | 56747.16",
            "826.58:41;764.15:4;756.43:38;519.94:4;289.78:3;729.64:9;379.60:27;189.08:35;154.40:37 | 82869.82 | 14916.56 | 97786.38",
            "734.35:44;236.89:7;762.32:37;837.44:13;488.11:7;717.94:46;82.30:37;78.13:40;269.96:32;891.82:35;560.46:50 | 155550.77 | 27999.15 | 183549.92",
            "610.28:38;594.00:24;392.92:16;235.63:45;319.95:6;752.91:20;688.39:32;450.21:47;588.30:19;798.18:5;154.76:33;548.05:11 | 119328.12 | 21479.07 | 140807.19",
            "199.21:32;552.73:3;875.85:5;731.49:37;411.24:22;911.34:23;779.06:32;760.09:30;90.13:6;353.82:31;913.63:43;85.20:4 | 142672.98 | 25681.12 | 168354.10",
            "919.46:20;848.21:37;892.92:29;373.03:46;505.67:43;454.83:2;605.16:23;220.27:40;153.48:32;77.28:14;376.75:9;967.79:16;521.54:26;650.79:6;218.06:29;526.45:36;364.17:9;564.30:36;364.94:46;544.34:23;894.86:25;302.46:10;108.77:12;198.31:15;863.14:15 | 255374.47 | 45967.38 | 301341.85",
            "635.66:38;239.01:17 | 23913.77 | 4304.48 | 28218.25",
            "5.37:10;549.13:35;483.99:40;742.32:21;164.49:45;675.67:40;858.48:44;969.66:4;598.54:50;892.05:36;514.30:26 | 174334.54 | 31380.24 | 205714.78"
    })
    void losTotalesCoincidenConUnCalculoIndependiente(String descripcion, String gravado, String igv, String importe) {
        TotalesDocumento t = CalculadoraIgv.calcularTotales(lineas(descripcion));

        assertThat(t.getTotalGravado()).isEqualTo(bd(gravado.trim()));
        assertThat(t.getTotalIgv()).isEqualTo(bd(igv.trim()));
        assertThat(t.getImporteTotal()).isEqualTo(bd(importe.trim()));
    }

    @Test
    void conUnaSolaLineaLosTotalesSonLosDeLaLinea() {
        LineaCalculada l = CalculadoraIgv.calcularLinea(bd("35.00"), bd("2"));
        TotalesDocumento t = CalculadoraIgv.calcularTotales(List.of(l));

        assertThat(t.getTotalGravado()).isEqualTo(l.getValorVenta());
        assertThat(t.getTotalIgv()).isEqualTo(l.getIgv());
        assertThat(t.getImporteTotal()).isEqualTo(l.getTotalLinea());
    }

    @Test
    void elOrdenDeLasLineasNoCambiaLosTotales() {
        List<LineaCalculada> originales = lineas("35.00:2;25.50:3;0.23:7;999.99:1;12.34:12");
        TotalesDocumento esperado = CalculadoraIgv.calcularTotales(originales);

        Random azar = new Random(1);
        for (int i = 0; i < 50; i++) {
            List<LineaCalculada> mezcladas = new ArrayList<>(originales);
            Collections.shuffle(mezcladas, azar);
            assertThat(CalculadoraIgv.calcularTotales(mezcladas)).isEqualTo(esperado);
        }
    }

    @Test
    void losTotalesSiempreTienenDosDecimales() {
        TotalesDocumento t = CalculadoraIgv.calcularTotales(lineas("100:1"));
        assertThat(t.getTotalGravado().scale()).isEqualTo(2);
        assertThat(t.getTotalIgv().scale()).isEqualTo(2);
        assertThat(t.getImporteTotal().scale()).isEqualTo(2);
    }

    // --- Entradas inválidas ---

    @Test
    void sinLineasNoSeCalculaNada() {
        assertThatThrownBy(() -> CalculadoraIgv.calcularTotales(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos una línea");
        assertThatThrownBy(() -> CalculadoraIgv.calcularTotales(List.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos una línea");
    }

    @Test
    void unaLineaNulaSeRechaza() {
        List<LineaCalculada> conNula = Arrays.asList(CalculadoraIgv.calcularLinea(BigDecimal.TEN, UNO), null);
        assertThatThrownBy(() -> CalculadoraIgv.calcularTotales(conNula)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("línea vacía");
    }

    @Test
    void unImporteTotalDeMasDeDoceEnterosSeRechaza() {
        LineaCalculada grande = CalculadoraIgv.calcularLinea(bd("999999999999.99"), UNO);
        assertThatThrownBy(() -> CalculadoraIgv.calcularTotales(List.of(grande, grande)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 dígitos enteros");
    }

    @Test
    void unDocumentoGrandeConTrescientasLineasIgualesNoTieneProblema() {
        List<LineaCalculada> lista = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            lista.add(CalculadoraIgv.calcularLinea(bd("35.00"), UNO));
        }
        TotalesDocumento t = CalculadoraIgv.calcularTotales(lista);
        assertThat(t.getImporteTotal()).isEqualTo(bd("10500.00"));
        assertThat(t.getTotalGravado()).isEqualTo(bd("8898.00"));
        assertThat(t.getTotalIgv()).isEqualTo(bd("1602.00"));
    }

    // L-015: con 35.00 cada línea se desvía 0.0012 del 18%; con mil iguales son 1.20 y la SUNAT lo rechazaría (tolera 1).
    // Lo normal, juntar los productos iguales en una sola línea con su cantidad, no tiene este problema.
    @Test
    void milLineasIgualesDe35SeRechazanPeroUnaSolaLineaConCantidadMilEsExacta() {
        LineaCalculada una = CalculadoraIgv.calcularLinea(bd("35.00"), UNO);
        assertThatThrownBy(() -> CalculadoraIgv.calcularTotales(Collections.nCopies(1000, una)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cuadrar el IGV");

        TotalesDocumento unida = CalculadoraIgv.calcularTotales(List.of(CalculadoraIgv.calcularLinea(bd("35.00"), bd("1000"))));
        assertThat(unida.getImporteTotal()).isEqualTo(bd("35000.00"));
        assertThat(unida.getTotalGravado()).isEqualTo(bd("29661.02"));
        assertThat(unida.getTotalIgv()).isEqualTo(bd("5338.98"));
    }

    // --- Documentos aleatorios: las reglas de la SUNAT, comprobadas aparte de la calculadora ---

    @Test
    void paraMilesDeDocumentosAleatoriosCumpleLasReglasDeTotalesDeLaSunat() {
        Random azar = new Random(20261029L);
        for (int d = 0; d < 5_000; d++) {
            int n = 1 + azar.nextInt(300);
            List<LineaCalculada> lista = new ArrayList<>();
            BigDecimal importeEsperado = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                BigDecimal precio = BigDecimal.valueOf(1 + azar.nextInt(3_000_000), 2 + azar.nextInt(3));
                BigDecimal cantidad = BigDecimal.valueOf(1 + azar.nextInt(500), azar.nextInt(3));
                BigDecimal total = precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
                if (total.compareTo(bd("0.01")) < 0) {
                    continue;
                }
                lista.add(CalculadoraIgv.calcularLinea(precio, cantidad));
                importeEsperado = importeEsperado.add(total);
            }
            if (lista.isEmpty()) {
                continue;
            }

            TotalesDocumento t = CalculadoraIgv.calcularTotales(lista);

            String caso = "documento " + d + " con " + lista.size() + " líneas";
            // Lo que paga el cliente es la suma de lo que costó cada línea
            assertThat(t.getImporteTotal()).as(caso).isEqualTo(importeEsperado);
            // 3279 y 3280: el importe es la base más el IGV
            assertThat(t.getTotalGravado().add(t.getTotalIgv())).as(caso).isEqualTo(t.getImporteTotal());
            // 3277 y 3278: el total gravado es la suma de las bases
            assertThat(t.getTotalGravado()).as(caso)
                    .isEqualTo(lista.stream().map(LineaCalculada::getValorVenta).reduce(BigDecimal.ZERO, BigDecimal::add));
            // 3291: IGV total = 18% de la base. La SUNAT tolera 1; nosotros quedamos muy por debajo
            BigDecimal desvio = t.getTotalIgv().subtract(t.getTotalGravado().multiply(TASA)).abs();
            assertThat(desvio).as(caso).isLessThan(bd("0.50"));
        }
    }

    // --- El redondeo acumulado ---

    @Test
    void elPeorDesvioPorLineaEnTodosLosTotalesDeUnCentavoAMilSolesNoPasaDe0059() {
        BigDecimal peor = BigDecimal.ZERO;
        for (int centavos = 1; centavos <= 100_000; centavos++) {
            BigDecimal total = BigDecimal.valueOf(centavos, 2);
            LineaCalculada l = CalculadoraIgv.calcularLinea(total, UNO);
            BigDecimal desvio = l.getIgv().subtract(l.getValorVenta().multiply(TASA)).abs();
            if (desvio.compareTo(peor) > 0) {
                peor = desvio;
            }
        }
        assertThat(peor).isLessThanOrEqualTo(bd("0.0059"));
    }

    @Test
    void conMuchasLineasIdenticasYElPeorPrecioElDesvioSeAcumulaYSeRechazaAntesDeLlegarALaSunat() {
        // 0.23 es el precio con el peor desvío por línea (0.0058)
        LineaCalculada peorLinea = CalculadoraIgv.calcularLinea(bd("0.23"), UNO);

        int primeroQueFalla = -1;
        for (int n = 1; n <= 300 && primeroQueFalla < 0; n++) {
            try {
                CalculadoraIgv.calcularTotales(Collections.nCopies(n, peorLinea));
            } catch (IllegalArgumentException e) {
                primeroQueFalla = n;
                assertThat(e.getMessage()).contains("cuadrar el IGV");
            }
        }

        // 0.50 / 0.0058 = 86: el límite cae por esa zona, y la SUNAT toleraría hasta 1, el doble
        assertThat(primeroQueFalla).isBetween(80, 95);
    }

    @Test
    void conPocasLineasDelPeorPrecioTodoFunciona() {
        LineaCalculada peorLinea = CalculadoraIgv.calcularLinea(bd("0.23"), UNO);
        TotalesDocumento t = CalculadoraIgv.calcularTotales(Collections.nCopies(60, peorLinea));
        assertThat(t.getImporteTotal()).isEqualTo(bd("13.80"));
    }
}
