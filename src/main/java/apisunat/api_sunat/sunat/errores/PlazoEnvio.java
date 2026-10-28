package apisunat.api_sunat.sunat.errores;

import apisunat.api_sunat.config.SunatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// El plazo para enviar una factura: el día de emisión más N días calendario completos (N = sunat.plazo-envio-dias, hoy 1).
// Una factura emitida el 28 a las 22:30 con plazo 1 se puede enviar durante todo el 29; desde las 00:00 del 30 la SUNAT la rechazaría (2108).
@Component
@RequiredArgsConstructor
public class PlazoEnvio {

    private final SunatProperties propiedades;

    // El primer instante en que ya no se puede enviar
    public LocalDateTime limite(LocalDateTime fechaEmision) {
        return fechaEmision.toLocalDate().plusDays(propiedades.getPlazoEnvioDias() + 1L).atStartOfDay();
    }

    public boolean vencido(LocalDateTime fechaEmision, LocalDateTime ahora) {
        return !ahora.isBefore(limite(fechaEmision));
    }
}
