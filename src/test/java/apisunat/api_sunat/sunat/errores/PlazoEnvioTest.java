package apisunat.api_sunat.sunat.errores;

import apisunat.api_sunat.config.SunatProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PlazoEnvioTest {

    private static PlazoEnvio conPlazo(int dias) {
        SunatProperties p = new SunatProperties();
        p.setPlazoEnvioDias(dias);
        return new PlazoEnvio(p);
    }

    @Test
    void conPlazoDeUnDiaUnaFacturaDelDia28SePuedeEnviarTodoElDia29() {
        PlazoEnvio plazo = conPlazo(1);
        LocalDateTime emision = LocalDateTime.of(2026, 10, 28, 22, 30);

        assertThat(plazo.limite(emision)).isEqualTo(LocalDateTime.of(2026, 10, 30, 0, 0));
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 28, 22, 31))).isFalse();
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 29, 0, 0))).isFalse();
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 29, 23, 59, 59))).isFalse();
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 29, 23, 59, 59, 999_999_999))).isFalse();
    }

    @Test
    void venceExactamenteALasCeroHorasDelDiaSiguienteAlUltimoDiaDelPlazo() {
        PlazoEnvio plazo = conPlazo(1);
        LocalDateTime emision = LocalDateTime.of(2026, 10, 28, 22, 30);

        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 30, 0, 0))).isTrue();
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 30, 0, 0, 1))).isTrue();
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 11, 15, 12, 0))).isTrue();
    }

    @Test
    void laHoraDeEmisionNoCambiaElLimite() {
        PlazoEnvio plazo = conPlazo(1);
        assertThat(plazo.limite(LocalDateTime.of(2026, 10, 28, 0, 0))).isEqualTo(LocalDateTime.of(2026, 10, 30, 0, 0));
        assertThat(plazo.limite(LocalDateTime.of(2026, 10, 28, 23, 59, 59))).isEqualTo(LocalDateTime.of(2026, 10, 30, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({"0,2026-10-29T00:00", "1,2026-10-30T00:00", "3,2026-11-01T00:00", "30,2026-11-28T00:00"})
    void elLimiteDependeDeLosDiasDelPlazo(int dias, String esperado) {
        assertThat(conPlazo(dias).limite(LocalDateTime.of(2026, 10, 28, 10, 0))).isEqualTo(LocalDateTime.parse(esperado));
    }

    @Test
    void conPlazoCeroSoloSePuedeEnviarElMismoDia() {
        PlazoEnvio plazo = conPlazo(0);
        LocalDateTime emision = LocalDateTime.of(2026, 10, 28, 10, 0);
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 28, 23, 59, 59))).isFalse();
        assertThat(plazo.vencido(emision, LocalDateTime.of(2026, 10, 29, 0, 0))).isTrue();
    }

    @Test
    void cruzaBienElFinDeMesYDeAno() {
        assertThat(conPlazo(1).limite(LocalDateTime.of(2026, 12, 31, 20, 0))).isEqualTo(LocalDateTime.of(2027, 1, 2, 0, 0));
        assertThat(conPlazo(1).limite(LocalDateTime.of(2028, 2, 28, 20, 0))).isEqualTo(LocalDateTime.of(2028, 3, 1, 0, 0)); // 2028 es bisiesto
        assertThat(conPlazo(1).limite(LocalDateTime.of(2027, 2, 28, 20, 0))).isEqualTo(LocalDateTime.of(2027, 3, 2, 0, 0));
    }

    @Test
    void elPlazoPorDefectoEsDeUnDia() {
        assertThat(new SunatProperties().getPlazoEnvioDias()).isEqualTo(1);
    }
}
