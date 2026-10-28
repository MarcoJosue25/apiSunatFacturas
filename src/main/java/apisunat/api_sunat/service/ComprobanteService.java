package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;

public interface ComprobanteService {

    // La factura y si se creó en esta llamada (false: ya existía con ese idExterno y se devuelve la misma, sin enviar nada de nuevo)
    record Emision(FacturaResponse factura, boolean nueva) {
    }

    // Reserva el número, guarda la factura con su XML firmado, la envía a la SUNAT y guarda lo que contestó.
    // Si la SUNAT no responde, la factura queda en ERROR_TEMPORAL (guardada, con su número) para reenviarla después.
    Emision emitir(FacturaRequest request);

    FacturaResponse obtener(Long id);

    // El XML firmado tal como se envió, en ISO-8859-1
    byte[] obtenerXml(Long id);

    // Envía a la SUNAT un comprobante ya guardado (PENDIENTE o ERROR_TEMPORAL) y guarda el resultado
    FacturaResponse enviar(Long id);
}
