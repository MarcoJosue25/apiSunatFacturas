package apisunat.api_sunat.sunat.cdr;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

// Lo que dice la constancia de recepción (CDR) de la SUNAT sobre un comprobante
@Value
@Builder
public class CdrResultado {

    // Serie y número del comprobante al que responde, por ejemplo F001-1
    String idDocumento;

    // "0" si la SUNAT lo aceptó; si no, el código del error que lo rechazó
    String codigo;

    String descripcion;

    // Advertencias de una constancia aceptada (las notas "4xxx - texto")
    @Singular("observacion")
    List<Observacion> observaciones;

    public boolean aceptado() {
        return "0".equals(codigo);
    }

    // El código puede ser null si la nota no empieza con un código
    @Value
    public static class Observacion {
        String codigo;
        String mensaje;
    }
}
