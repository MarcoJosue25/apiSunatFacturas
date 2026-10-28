package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.service.ResumenDiarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A la hora configurada (de Lima) arma el resumen de las boletas pendientes y lo envía. Se apaga con sunat.resumen.habilitado=false.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sunat.resumen.habilitado", havingValue = "true", matchIfMissing = true)
public class ResumenDiarioScheduler {

    private final ResumenDiarioService resumenDiarioService;

    @Scheduled(cron = "${sunat.resumen.cron:0 0 22 * * *}", zone = "America/Lima")
    public void generarYEnviar() {
        try {
            resumenDiarioService.generarYEnviar();
        } catch (RuntimeException e) {
            // Las boletas siguen pendientes: el resumen se arma la próxima vez
            log.error("Falló el armado del resumen diario", e);
        }
    }
}
