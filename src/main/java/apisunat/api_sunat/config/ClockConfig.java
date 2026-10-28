package apisunat.api_sunat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// Un solo reloj para toda la aplicación, con la hora de Lima (UTC-5, sin horario de verano): las fechas de las facturas son del Perú
// aunque el servidor esté en otra zona. En los tests se reemplaza por un reloj fijo.
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @Bean
    public Clock clock() {
        return Clock.system(ZONA_LIMA);
    }
}
