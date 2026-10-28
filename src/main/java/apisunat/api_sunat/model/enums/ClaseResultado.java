package apisunat.api_sunat.model.enums;

// Qué clase de resultado es una respuesta de la SUNAT y, por lo tanto, qué se hace con ella.
// Solo TRANSITORIO se puede volver a intentar sin cambiar nada: todo lo demás necesita una decisión o una corrección.
public enum ClaseResultado {

    // La SUNAT registró el comprobante sin advertencias
    ACEPTADO,

    // La SUNAT lo registró y avisó de cosas por mejorar; es válido, no se reenvía
    ACEPTADO_CON_OBSERVACIONES,

    // La SUNAT procesó el documento y no lo registró. En una factura el número queda consumido: hay que emitir otra con otro número.
    RECHAZO,

    // El documento no se procesó por un problema nuestro (el XML, el ZIP, el nombre del archivo). Se corrige y se reenvía.
    ERROR_DE_DATOS,

    // El problema no es del documento sino de la configuración (perfil, certificado, RUC). Hay que avisar al dueño.
    ERROR_DE_CONFIGURACION,

    // El comprobante ya se había registrado antes (con el mismo número). Antes de dar por perdido el envío hay que consultar la constancia.
    YA_REGISTRADO,

    // Falla pasajera de la SUNAT o de la red; se puede volver a intentar
    TRANSITORIO,

    // Un código que no está en la tabla de la SUNAT: no se sabe qué es, así que nunca se reintenta
    DESCONOCIDO;

    public boolean esReintentable() {
        return this == TRANSITORIO;
    }
}
