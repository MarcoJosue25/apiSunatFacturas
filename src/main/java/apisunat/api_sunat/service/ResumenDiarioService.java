package apisunat.api_sunat.service;

import apisunat.api_sunat.dto.ResumenResponse;

import java.util.List;

public interface ResumenDiarioService {

    // Junta las boletas que esperan resumen en resúmenes nuevos: uno por fecha de emisión y, si pasan de 500, varios.
    // No envía nada. Devuelve los ids de los resúmenes creados (vacío si no había boletas).
    List<Long> generarPendientes();

    // Envía el resumen a la SUNAT y guarda el ticket que da
    void enviar(Long id);

    // Pregunta a la SUNAT cómo le fue a un resumen enviado y, si ya terminó, resuelve sus boletas
    void consultar(Long id);

    // Un resumen rechazado se vuelve a enviar con el mismo nombre y el mismo contenido
    ResumenResponse reenviar(Long id);

    ResumenResponse obtener(Long id);

    // Una pasada: envía los resúmenes que no se enviaron y consulta los que esperan respuesta, si ya les toca. Devuelve cuántos trabajó.
    int procesarPendientes();

    // Lo que hace el planificador a la hora del resumen: generar y enviar
    List<Long> generarYEnviar();
}
