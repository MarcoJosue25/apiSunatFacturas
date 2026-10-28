package apisunat.api_sunat.sunat.errores;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

// Cuánto esperar antes de volver a intentar un envío que falló: 1, 2, 5, 15, 30 y 60 minutos, y de ahí en adelante siempre 60.
// Sin aleatoriedad: con un solo emisor y unos pocos comprobantes no hay "estampida" de reintentos que repartir.
@Component
public class PoliticaBackoff {

    private static final List<Duration> ESCALONES = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofMinutes(60));

    // Lo que hay que esperar después del intento fallido número N (el primero es 1)
    public Duration espera(int intentosFallidos) {
        if (intentosFallidos < 1) {
            throw new IllegalArgumentException("El número de intento fallido debe ser 1 o más");
        }
        return ESCALONES.get(Math.min(intentosFallidos, ESCALONES.size()) - 1);
    }
}
