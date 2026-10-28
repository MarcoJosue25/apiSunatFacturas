package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.service.ReintentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// Cada cierto tiempo vuelve a enviar lo que quedó pendiente. Se apaga con sunat.reintentos.habilitado=false (como en los tests).
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sunat.reintentos.habilitado", havingValue = "true", matchIfMissing = true)
public class ReintentoScheduler {

    private final ReintentoService reintentoService;

    @Scheduled(fixedDelayString = "${sunat.reintentos.intervalo-segundos:60}", initialDelayString = "${sunat.reintentos.intervalo-segundos:60}",
            timeUnit = TimeUnit.SECONDS)
    public void reintentar() {
        try {
            reintentoService.procesarPendientes();
        } catch (RuntimeException e) {
            // Si una pasada falla (por ejemplo la base de datos no responde), la siguiente lo vuelve a intentar
            log.error("Falló la pasada de reintentos", e);
        }
    }
}
