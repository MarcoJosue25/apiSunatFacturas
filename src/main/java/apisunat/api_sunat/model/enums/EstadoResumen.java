package apisunat.api_sunat.model.enums;

// En qué punto del camino está un resumen diario de boletas
public enum EstadoResumen {

    // Armado y firmado, todavía no se envió
    GENERADO,

    // Se envió y la SUNAT dio un ticket: se consulta hasta que termine de procesarlo
    ENVIADO,

    ACEPTADO,
    ACEPTADO_CON_OBSERVACIONES,

    // La SUNAT rechazó todo el resumen. Se corrige y se reenvía con el mismo nombre.
    RECHAZADO,

    // No se pudo enviar (la SUNAT no respondió): se reintenta
    ERROR_TEMPORAL,

    // La SUNAT contestó algo que no se sabe interpretar: alguien debe mirarlo
    REVISAR;

    public boolean puedePasarA(EstadoResumen destino) {
        return switch (this) {
            case GENERADO -> destino == ENVIADO || destino == ERROR_TEMPORAL || destino == RECHAZADO || destino == REVISAR;
            case ERROR_TEMPORAL -> destino == ENVIADO || destino == RECHAZADO || destino == REVISAR;
            case ENVIADO -> destino == ACEPTADO || destino == ACEPTADO_CON_OBSERVACIONES || destino == RECHAZADO || destino == REVISAR;
            case RECHAZADO -> destino == GENERADO;
            default -> false;
        };
    }

    public boolean terminado() {
        return this == ACEPTADO || this == ACEPTADO_CON_OBSERVACIONES || this == REVISAR;
    }
}
