package apisunat.api_sunat.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa las tareas programadas, salvo que se apaguen con sunat.reintentos.habilitado=false
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sunat.reintentos.habilitado", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
