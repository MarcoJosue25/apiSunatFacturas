package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ResultadoEmision;

public interface FacturaService {

    // Arma, firma y envía una factura a la SUNAT con la serie y el correlativo indicados, y dice cómo respondió.
    // Si la SUNAT no responde (sin conexión, timeout), lanza SunatNoDisponibleException: en ese caso no se sabe si recibió la factura.
    ResultadoEmision emitir(FacturaRequest request, String serie, long correlativo);
}
