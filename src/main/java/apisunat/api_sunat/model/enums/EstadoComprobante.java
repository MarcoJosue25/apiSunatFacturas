package apisunat.api_sunat.model.enums;

import java.util.EnumSet;
import java.util.Set;

// En qué punto del camino está un comprobante. Los pasos permitidos están aquí para que ningún otro código los salte.
public enum EstadoComprobante {

    // Tiene número y XML firmado, y todavía no se envió
    PENDIENTE,

    // Se está enviando a la SUNAT en este momento
    ENVIANDO,

    ACEPTADO,
    ACEPTADO_CON_OBSERVACIONES,

    // La SUNAT lo procesó y no lo registró: el número queda consumido
    RECHAZADO,

    // La SUNAT no lo procesó por un problema del documento
    ERROR_DE_DATOS,

    // La SUNAT no lo procesó por un problema de configuración (perfil, certificado, RUC)
    ERROR_DE_CONFIGURACION,

    // No se sabe si la SUNAT lo recibió (sin conexión, timeout, respuesta ilegible) o estaba caída: se puede reintentar
    ERROR_TEMPORAL,

    // La SUNAT contestó algo que no se sabe interpretar, o dijo que ya lo tenía: alguien debe mirarlo
    REVISAR,

    // Pasó el plazo de envío sin que la SUNAT lo recibiera: ya no se puede enviar (la SUNAT lo rechazaría por fuera de plazo)
    VENCIDO;

    private static final Set<EstadoComprobante> DESDE_ENVIANDO = EnumSet.of(
            ACEPTADO, ACEPTADO_CON_OBSERVACIONES, RECHAZADO, ERROR_DE_DATOS, ERROR_DE_CONFIGURACION, ERROR_TEMPORAL, REVISAR);

    public boolean puedePasarA(EstadoComprobante destino) {
        return switch (this) {
            case PENDIENTE, ERROR_TEMPORAL -> destino == ENVIANDO || destino == VENCIDO;
            case ENVIANDO -> DESDE_ENVIANDO.contains(destino);
            default -> false;
        };
    }

    // El estado final que corresponde a la clase de respuesta de la SUNAT
    public static EstadoComprobante desde(ClaseResultado clase) {
        return switch (clase) {
            case ACEPTADO -> ACEPTADO;
            case ACEPTADO_CON_OBSERVACIONES -> ACEPTADO_CON_OBSERVACIONES;
            case RECHAZO -> RECHAZADO;
            case ERROR_DE_DATOS -> ERROR_DE_DATOS;
            case ERROR_DE_CONFIGURACION -> ERROR_DE_CONFIGURACION;
            case TRANSITORIO -> ERROR_TEMPORAL;
            case YA_REGISTRADO, DESCONOCIDO -> REVISAR;
        };
    }
}
