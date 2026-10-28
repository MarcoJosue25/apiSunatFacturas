package apisunat.api_sunat.service;

public interface SerieService {

    // Crea la serie si todavía no existe. Se llama ANTES de abrir la transacción que pide el número.
    void asegurar(String tipoDocumento, String serie);

    // El siguiente correlativo de la serie, sin saltos ni repetidos aunque lo pidan muchos hilos a la vez.
    // Debe llamarse dentro de la transacción que guarda el comprobante: si esa transacción se deshace, el número vuelve a quedar libre.
    long siguiente(String tipoDocumento, String serie);
}
