package apisunat.api_sunat.sunat.calculo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraIgvLineaTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // Los valores esperados salen de un cálculo independiente (Python con Decimal), no de esta misma fórmula
    @ParameterizedTest(name = "precio {0} x cantidad {1}")
    @CsvSource({
            "35.00, 1, 29.6610169492, 29.66, 5.34, 35.00",
            "35.00, 2, 29.6610169492, 59.32, 10.68, 70.00",
            "10.00, 1, 8.4745762712, 8.47, 1.53, 10.00",
            "118.00, 1, 100.0000000000, 100.00, 18.00, 118.00",
            "0.01, 1, 0.0084745763, 0.01, 0.00, 0.01",
            "0.05, 1, 0.0423728814, 0.04, 0.01, 0.05",
            "0.99, 3, 0.8389830508, 2.52, 0.45, 2.97",
            "1.005, 1, 0.8516949153, 0.86, 0.15, 1.01",
            "0.015, 1, 0.0127118644, 0.02, 0.00, 0.02",
            "0.005, 1, 0.0042372881, 0.01, 0.00, 0.01",
            "15.50, 0.5, 13.1355932203, 6.57, 1.18, 7.75",
            "99.99, 7, 84.7372881356, 593.16, 106.77, 699.93",
            "1234.56, 3, 1046.2372881356, 3138.71, 564.97, 3703.68",
            "1.18, 1, 1.0000000000, 1.00, 0.18, 1.18",
            "2.36, 1, 2.0000000000, 2.00, 0.36, 2.36",
            "59.00, 1, 50.0000000000, 50.00, 9.00, 59.00",
            "100, 1, 84.7457627119, 84.75, 15.25, 100.00",
            "29.90, 2, 25.3389830508, 50.68, 9.12, 59.80",
            "29.90, 10, 25.3389830508, 253.39, 45.61, 299.00",
            "7.77, 13, 6.5847457627, 85.60, 15.41, 101.01",
            "0.333, 3, 0.2822033898, 0.85, 0.15, 1.00",
            "12.345, 2, 10.4618644068, 20.92, 3.77, 24.69",
            "999999.99, 1, 847457.6186440678, 847457.62, 152542.37, 999999.99",
            "5, 0.002, 4.2372881356, 0.01, 0.00, 0.01",
            "0.10, 0.1, 0.0847457627, 0.01, 0.00, 0.01",
            "4.5, 4.5, 3.8135593220, 17.16, 3.09, 20.25",
            "1000000, 1000, 847457.6271186441, 847457627.12, 152542372.88, 1000000000.00",
            "0.01, 1000, 0.0084745763, 8.47, 1.53, 10.00",
            "49.90, 12, 42.2881355932, 507.46, 91.34, 598.80",
            "3.333333, 3, 2.8248584746, 8.47, 1.53, 10.00",
            "17.5, 0.25, 14.8305084746, 3.71, 0.67, 4.38",
            "250, 3, 211.8644067797, 635.59, 114.41, 750.00",
            "8.99, 100, 7.6186440678, 761.86, 137.14, 899.00",
            "1, 1, 0.8474576271, 0.85, 0.15, 1.00",
            "2, 1, 1.6949152542, 1.69, 0.31, 2.00",
            "3, 1, 2.5423728814, 2.54, 0.46, 3.00",
            "4, 1, 3.3898305085, 3.39, 0.61, 4.00",
            "6, 1, 5.0847457627, 5.08, 0.92, 6.00",
            "7, 1, 5.9322033898, 5.93, 1.07, 7.00",
            "9, 1, 7.6271186441, 7.63, 1.37, 9.00",
            "11, 1, 9.3220338983, 9.32, 1.68, 11.00",
            "13, 1, 11.0169491525, 11.02, 1.98, 13.00",
            "0.9999999999, 1, 0.8474576270, 0.85, 0.15, 1.00",
            "123456789.123456789, 1, 104624397.5622515161, 104624397.56, 18832391.56, 123456789.12",
            "999999999999.99, 1, 847457627118.6355932203, 847457627118.64, 152542372881.35, 999999999999.99",
            "45.6789, 2.5, 38.7109322034, 96.78, 17.42, 114.20"
    })
    void calculaLosMismosNumerosQueUnCalculoIndependiente(String precio, String cantidad, String valorUnitario,
                                                           String valorVenta, String igv, String total) {
        LineaCalculada l = CalculadoraIgv.calcularLinea(bd(precio), bd(cantidad));

        assertThat(l.getValorUnitario()).isEqualTo(bd(valorUnitario));
        assertThat(l.getValorVenta()).isEqualTo(bd(valorVenta));
        assertThat(l.getIgv()).isEqualTo(bd(igv));
        assertThat(l.getTotalLinea()).isEqualTo(bd(total));
    }

    @Test
    void elPoloDeTreintaYCincoPorDosUnidadesDaLoDeLaDocumentacion() {
        LineaCalculada l = CalculadoraIgv.calcularLinea(bd("35.00"), bd("2"));
        assertThat(l.getTotalLinea()).isEqualTo(bd("70.00"));
        assertThat(l.getValorVenta()).isEqualTo(bd("59.32"));
        assertThat(l.getIgv()).isEqualTo(bd("10.68"));
        assertThat(l.getValorUnitario()).isEqualTo(bd("29.6610169492"));
    }

    @Test
    void guardaLaCantidadYElPrecioTalComoLlegaron() {
        LineaCalculada l = CalculadoraIgv.calcularLinea(bd("35.00"), bd("2"));
        assertThat(l.getCantidad()).isEqualTo(bd("2"));
        assertThat(l.getPrecioUnitario()).isEqualTo(bd("35.00"));
    }

    // --- Escalas: los montos siempre con 2 decimales y el valor unitario con 10, aunque la entrada venga de otra forma ---

    @ParameterizedTest
    @CsvSource({"35, 1", "35.0, 1.0", "35.000000, 1", "3.5E1, 1", "35.00, 1.00", "0.35E2, 1"})
    void laEscalaDeLaEntradaNoCambiaLaEscalaDeLaSalida(String precio, String cantidad) {
        LineaCalculada l = CalculadoraIgv.calcularLinea(bd(precio), bd(cantidad));
        assertThat(l.getValorVenta().scale()).isEqualTo(2);
        assertThat(l.getIgv().scale()).isEqualTo(2);
        assertThat(l.getTotalLinea().scale()).isEqualTo(2);
        assertThat(l.getValorUnitario().scale()).isEqualTo(10);
        assertThat(l.getTotalLinea()).isEqualTo(bd("35.00"));
    }

    // --- Redondeo de mitad hacia arriba: los empates sobre BigDecimal, sin errores de punto flotante ---

    @ParameterizedTest
    @CsvSource({"0.005, 0.01", "0.015, 0.02", "0.025, 0.03", "1.005, 1.01", "2.675, 2.68", "1.115, 1.12", "0.995, 1.00", "10.004, 10.00", "10.0049999, 10.00", "10.005, 10.01"})
    void elEmpateSeRedondeaHaciaArriba(String precio, String totalEsperado) {
        assertThat(CalculadoraIgv.calcularLinea(bd(precio), BigDecimal.ONE).getTotalLinea()).isEqualTo(bd(totalEsperado));
    }

    // --- Entradas inválidas ---

    @Test
    void sinPrecioOSinCantidadFalla() {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precio unitario es obligatorio");
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cantidad es obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "-100"})
    void elPrecioDebeSerMayorQueCero(String precio) {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(bd(precio), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("precio unitario debe ser mayor que cero");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0", "-1", "-0.5"})
    void laCantidadDebeSerMayorQueCero(String cantidad) {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(BigDecimal.TEN, bd(cantidad)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cantidad debe ser mayor que cero");
    }

    @Test
    void masDeDiezDecimalesEnElPrecioONaLaCantidadSeRechaza() {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(bd("1.12345678901"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 decimales");
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(BigDecimal.ONE, bd("0.12345678901")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10 decimales");
    }

    @Test
    void diezDecimalesJustosSePermitenYCerosSobrantesNoCuentan() {
        assertThat(CalculadoraIgv.calcularLinea(bd("1.1234567890"), BigDecimal.ONE).getTotalLinea()).isEqualTo(bd("1.12"));
        assertThat(CalculadoraIgv.calcularLinea(bd("1.12345678900000"), BigDecimal.ONE).getTotalLinea()).isEqualTo(bd("1.12"));
    }

    @Test
    void masDeDoceEnterosSeRechaza() {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(bd("1000000000000"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 dígitos enteros");
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(BigDecimal.ONE, bd("1000000000000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 dígitos enteros");
    }

    @Test
    void elTotalQueSePasaDeDoceEnterosPorLaMultiplicacionSeRechaza() {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(bd("999999999.99"), bd("10000")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("total de la línea");
    }

    @Test
    void unTotalDeDoceEnterosPorLaMultiplicacionSiSePermite() {
        assertThat(CalculadoraIgv.calcularLinea(bd("999999999.99"), bd("1000")).getTotalLinea()).isEqualTo(bd("999999999990.00"));
    }

    @Test
    void elMayorTotalPermitidoFunciona() {
        assertThat(CalculadoraIgv.calcularLinea(bd("999999999999.99"), BigDecimal.ONE).getTotalLinea()).isEqualTo(bd("999999999999.99"));
    }

    @Test
    void unTotalQueRedondeaACeroSeRechazaYUnCentavoSePermite() {
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(bd("0.001"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos 0.01");
        assertThatThrownBy(() -> CalculadoraIgv.calcularLinea(bd("0.0049"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("al menos 0.01");
        assertThat(CalculadoraIgv.calcularLinea(bd("0.005"), BigDecimal.ONE).getTotalLinea()).isEqualTo(bd("0.01"));
    }

    // --- Propiedades que deben cumplirse para cualquier precio y cantidad (20.000 casos con semilla fija) ---

    @Test
    void paraMilesDeCasosAleatoriosSiempreCuadraYCumpleLasReglasDeLaSunat() {
        Random azar = new Random(20261028L);
        for (int i = 0; i < 20_000; i++) {
            BigDecimal precio = BigDecimal.valueOf(1 + azar.nextInt(5_000_000), 2 + azar.nextInt(3));   // 0.0001 .. 50000
            BigDecimal cantidad = BigDecimal.valueOf(1 + azar.nextInt(100_000), azar.nextInt(4));         // 1 .. 100000 con hasta 3 decimales
            BigDecimal total = precio.multiply(cantidad).setScale(2, java.math.RoundingMode.HALF_UP);
            if (total.compareTo(new BigDecimal("0.01")) < 0) {
                continue;
            }

            LineaCalculada l = CalculadoraIgv.calcularLinea(precio, cantidad);

            String caso = "precio=" + precio + " cantidad=" + cantidad;
            // Lo que se le cobra al cliente es exactamente precio x cantidad redondeado
            assertThat(l.getTotalLinea()).as(caso).isEqualTo(total);
            assertThat(l.getValorVenta().add(l.getIgv())).as(caso).isEqualTo(l.getTotalLinea());
            assertThat(l.getIgv().signum()).as(caso).isGreaterThanOrEqualTo(0);
            // 3103: IGV = 18% de la base (la SUNAT tolera 1; nosotros quedamos a menos de 1 centavo)
            assertThat(l.getIgv().subtract(l.getValorVenta().multiply(new BigDecimal("0.18"))).abs())
                    .as(caso).isLessThanOrEqualTo(new BigDecimal("0.006"));
            // 3271: valor de venta = valor unitario x cantidad. Hay dos redondeos seguidos (el total y la base),
            // así que el máximo teórico es 0.005/1.18 + 0.005 = 0.0092: menos de un centavo
            assertThat(l.getValorUnitario().multiply(cantidad).subtract(l.getValorVenta()).abs())
                    .as(caso).isLessThanOrEqualTo(new BigDecimal("0.0093"));
            // 3270: precio unitario = (valor de venta + IGV) / cantidad
            BigDecimal precioRecalculado = l.getValorVenta().add(l.getIgv()).divide(cantidad, 10, java.math.RoundingMode.HALF_UP);
            assertThat(precioRecalculado.subtract(precio).abs().multiply(cantidad))
                    .as(caso).isLessThanOrEqualTo(new BigDecimal("0.006"));
        }
    }
}
