package apisunat.api_sunat.service;

public interface ReintentoService {

    // Una pasada: recupera lo que quedó "enviándose" por una caída y vuelve a enviar los pendientes cuyo próximo intento ya llegó.
    // Devuelve cuántos comprobantes se trabajaron.
    int procesarPendientes();

    // Un solo comprobante: lo manda a vencido si pasó el plazo, o consulta la constancia y/o lo reenvía. Si otro proceso lo tomó
    // al mismo tiempo, no hace nada.
    void procesar(Long id);
}
