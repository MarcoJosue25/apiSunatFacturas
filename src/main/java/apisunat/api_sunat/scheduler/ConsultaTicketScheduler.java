package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.service.ResumenDiarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// Cada cierto tiempo envía los resúmenes que no salieron y consulta los tickets que esperan respuesta. Se apaga con sunat.resumen.habilitado=false.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sunat.resumen.habilitado", havingValue = "true", matchIfMissing = true)
public class ConsultaTicketScheduler {

    private final ResumenDiarioService resumenDiarioService;

    @Scheduled(fixedDelayString = "${sunat.resumen.intervalo-consulta-segundos:60}", initialDelayString = "${sunat.resumen.intervalo-consulta-segundos:60}",
            timeUnit = TimeUnit.SECONDS)
    public void consultar() {
        try {
            resumenDiarioService.procesarPendientes();
        } catch (RuntimeException e) {
            log.error("Falló la pasada de consulta de tickets", e);
        }
    }
}
