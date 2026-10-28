package apisunat.api_sunat.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

// Un reloj que los tests mueven a mano, para probar esperas de minutos y plazos de días sin esperar tiempo real
public class RelojMutable extends Clock {

    private volatile Instant ahora;
    private final ZoneId zona;

    public RelojMutable(Instant inicio) {
        this(inicio, ZoneOffset.UTC);
    }

    private RelojMutable(Instant inicio, ZoneId zona) {
        this.ahora = inicio;
        this.zona = zona;
    }

    public void fijar(Instant instante) {
        this.ahora = instante;
    }

    public void avanzar(Duration duracion) {
        this.ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId nuevaZona) {
        // Comparte el instante con el original: si se mueve uno, se mueven todos
        RelojMutable origen = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return nuevaZona;
            }

            @Override
            public Clock withZone(ZoneId otraZona) {
                return origen.withZone(otraZona);
            }

            @Override
            public Instant instant() {
                return origen.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
