package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.ResultadoEmision;

import java.time.LocalDateTime;

public interface FacturaService {

    // Arma, firma y envía una factura a la SUNAT con la serie y el correlativo indicados, y dice cómo respondió.
    // Si la SUNAT no responde (sin conexión, timeout), lanza SunatNoDisponibleException: en ese caso no se sabe si recibió la factura.
    ResultadoEmision emitir(FacturaRequest request, String serie, long correlativo);

    // Los dos pasos de emitir por separado, porque entre ellos se guarda el comprobante en la base:

    // Arma el XML, lo firma y comprueba que cumpla el esquema. No habla con la SUNAT.
    byte[] prepararXml(FacturaRequest request, String serie, long correlativo, LocalDateTime emision);

    // Manda un XML ya firmado a la SUNAT. Un error SOAP sale como resultado; si la SUNAT no responde, lanza la excepción.
    ResultadoEmision enviar(String serie, long correlativo, byte[] xmlFirmado);

    // Lo mismo para una boleta (que no se envía sola a la SUNAT, sino dentro de un resumen diario)
    byte[] prepararXmlBoleta(BoletaRequest request, String serie, long correlativo, LocalDateTime emision);
}
