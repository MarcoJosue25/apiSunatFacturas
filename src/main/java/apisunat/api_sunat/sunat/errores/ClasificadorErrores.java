package apisunat.api_sunat.sunat.errores;

import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.enums.ClaseResultado;
import apisunat.api_sunat.sunat.cdr.CdrResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

// Decide qué clase de resultado es cada código de la SUNAT. Los rangos del manual (0100-999 excepciones, 1000-1999 formato,
// 2000-3999 rechazos, 4000+ observaciones) sirven de base, pero tienen excepciones, así que lo importante se decide código por código
// (ver docs/sunat/NOTAS.md, §5). Lo que no está en la tabla de la SUNAT es DESCONOCIDO y nunca se reintenta.
@Component
@RequiredArgsConstructor
public class ClasificadorErrores {

    // Solo estos dos son fallas pasajeras de la SUNAT: 0100 (no puede responder, "intente nuevamente") y 0109 (autenticación caída)
    private static final Set<String> TRANSITORIOS = Set.of("0100", "0109");

    // 0111 sin perfil para enviar, 0154 RUC que no corresponde al usuario, 1078 emisor no autorizado, 1080 debe usar otro sistema,
    // 1086 sujeto sin capacidad operativa, 2325 certificado distinto al comunicado, 2326 certificado dado de baja, 2335 documento alterado
    private static final Set<String> CONFIGURACION = Set.of("0111", "0154", "1078", "1080", "1086", "2325", "2326", "2335");

    // Errores nuestros aunque estén en el rango 0xxx: nombre del ZIP (0151), ZIP vacío, corrupto o con más de un archivo (0155 a 0158),
    // nombre del XML (0159 a 0161), XML que no se puede leer (0306), ticket que no existe (0127) y boleta enviada por sendBill (1079)
    private static final Set<String> DATOS_FUERA_DE_RANGO = Set.of(
            "0127", "0151", "0155", "0156", "0157", "0158", "0159", "0160", "0161", "0306", "1079");

    // Casos de 1xxx que no son "corregir y reenviar"
    private static final Map<String, ClaseResultado> ESPECIALES = Map.of(
            "1032", ClaseResultado.RECHAZO,        // ya informado y con estado anulado o rechazado
            "1033", ClaseResultado.YA_REGISTRADO); // registrado antes con otros datos

    private final CatalogoCodigos catalogo;

    public ClaseResultado clasificar(String codigo) {
        if (codigo == null) {
            return ClaseResultado.DESCONOCIDO;
        }
        CodigoSunat fila = catalogo.buscar(codigo).orElse(null);
        if (fila == null) {
            return ClaseResultado.DESCONOCIDO;
        }
        if (TRANSITORIOS.contains(codigo)) {
            return ClaseResultado.TRANSITORIO;
        }
        if (CONFIGURACION.contains(codigo)) {
            return ClaseResultado.ERROR_DE_CONFIGURACION;
        }
        if (DATOS_FUERA_DE_RANGO.contains(codigo)) {
            return ClaseResultado.ERROR_DE_DATOS;
        }
        ClaseResultado especial = ESPECIALES.get(codigo);
        if (especial != null) {
            return especial;
        }
        if (fila.tipo() == CodigoSunat.Tipo.OBSERVACION) {
            return ClaseResultado.ACEPTADO_CON_OBSERVACIONES;
        }
        int numero = Integer.parseInt(codigo);
        if (numero >= 1000 && numero < 2000) {
            return ClaseResultado.ERROR_DE_DATOS;
        }
        if (numero >= 2000 && numero < 4000) {
            // Incluye los dos códigos que a veces son observación y a veces error (2917 y 3219): por precaución se tratan como rechazo
            return ClaseResultado.RECHAZO;
        }
        // Un código de la tabla que no encaja en ninguna regla: no se sabe qué hacer con él
        return ClaseResultado.DESCONOCIDO;
    }

    // Una constancia aceptada lo es con o sin observaciones; una rechazada se clasifica por su código
    public ClaseResultado clasificar(CdrResultado cdr) {
        if (cdr.aceptado()) {
            return cdr.getObservaciones().isEmpty() ? ClaseResultado.ACEPTADO : ClaseResultado.ACEPTADO_CON_OBSERVACIONES;
        }
        ClaseResultado clase = clasificar(cdr.getCodigo());
        // Una constancia con código distinto de cero nunca es una aceptación, aunque el código sea de los de observación
        return clase == ClaseResultado.ACEPTADO_CON_OBSERVACIONES ? ClaseResultado.DESCONOCIDO : clase;
    }

    public ClaseResultado clasificar(SunatFaultException fault) {
        return clasificar(fault.getCodigo());
    }

    // Sin conexión, timeout o error HTTP sin detalle: la SUNAT no estaba disponible, se puede volver a intentar
    public ClaseResultado clasificar(SunatNoDisponibleException e) {
        return ClaseResultado.TRANSITORIO;
    }
}
