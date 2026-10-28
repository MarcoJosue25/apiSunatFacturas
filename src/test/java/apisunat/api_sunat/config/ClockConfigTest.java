package apisunat.api_sunat.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClockConfigTest {

    @Autowired
    private Clock clock;

    @Test
    void elRelojDeLaAplicacionEsElDeLima() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(ClockConfig.ZONA_LIMA).isEqualTo(ZoneId.of("America/Lima"));
    }

    @Test
    void limaEstaSiempreCincoHorasDetrasDeUtcSinHorarioDeVerano() {
        for (int mes = 1; mes <= 12; mes++) {
            ZonedDateTime fecha = ZonedDateTime.of(2026, mes, 15, 12, 0, 0, 0, ClockConfig.ZONA_LIMA);
            assertThat(fecha.getOffset()).as("mes " + mes).isEqualTo(ZoneOffset.ofHours(-5));
        }
    }

    @Test
    void laHoraDelRelojEstaCercaDeLaHoraRealDeLima() {
        Instant ahora = Instant.now();
        Instant delReloj = clock.instant();
        assertThat(Duration.between(ahora, delReloj).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void conUnServidorEnOtraZonaLaFechaDeEmisionSigueSiendoLaDeLima() {
        // 2026-10-29 03:30 UTC es todavía 2026-10-28 22:30 en Lima: la factura lleva fecha del 28
        Clock fijo = Clock.fixed(Instant.parse("2026-10-29T03:30:00Z"), ClockConfig.ZONA_LIMA);
        assertThat(LocalDateTime.now(fijo)).isEqualTo(LocalDateTime.of(2026, 10, 28, 22, 30, 0));
        Clock enTokio = Clock.fixed(Instant.parse("2026-10-29T03:30:00Z"), ZoneId.of("Asia/Tokyo"));
        assertThat(LocalDateTime.now(enTokio).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 10, 29));
    }
}
