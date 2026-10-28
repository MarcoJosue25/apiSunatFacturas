package apisunat.api_sunat.sunat.errores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaBackoffTest {

    private final PoliticaBackoff politica = new PoliticaBackoff();

    @ParameterizedTest
    @CsvSource({"1,1", "2,2", "3,5", "4,15", "5,30", "6,60"})
    void losPrimerosSeisIntentosEsperan1_2_5_15_30_y_60Minutos(int intento, long minutos) {
        assertThat(politica.espera(intento)).isEqualTo(Duration.ofMinutes(minutos));
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 8, 10, 100, 1000, Integer.MAX_VALUE})
    void despuesDelSextoSiempreEsperaUnaHora(int intento) {
        assertThat(politica.espera(intento)).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void laEsperaNuncaBajaConLosIntentos() {
        Duration anterior = Duration.ZERO;
        for (int i = 1; i <= 50; i++) {
            Duration actual = politica.espera(i);
            assertThat(actual).isGreaterThanOrEqualTo(anterior);
            anterior = actual;
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void unIntentoMenorQueUnoSeRechaza(int intento) {
        assertThatThrownBy(() -> politica.espera(intento)).isInstanceOf(IllegalArgumentException.class);
    }
}
