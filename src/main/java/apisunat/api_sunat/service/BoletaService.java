package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.FacturaResponse;

public interface BoletaService {

    // La boleta y si se creó en esta llamada (false: ya existía con ese idExterno y se devuelve la misma)
    record Emision(FacturaResponse boleta, boolean nueva) {
    }

    // Reserva el número, arma y firma la boleta y la guarda esperando su resumen diario. No habla con la SUNAT: las boletas se
    // informan después, todas juntas, en el resumen del día.
    Emision emitir(BoletaRequest request);

    FacturaResponse obtener(Long id);

    byte[] obtenerXml(Long id);
}
