package apisunat.api_sunat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa las tareas programadas. Cada tarea se apaga por separado con su propia propiedad (sunat.reintentos.habilitado,
// sunat.resumen.habilitado), así que aquí no hay condición.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
