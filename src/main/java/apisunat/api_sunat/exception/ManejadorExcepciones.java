package apisunat.api_sunat.exception;

import apisunat.api_sunat.dto.MensajeError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@Slf4j
@RestControllerAdvice
public class ManejadorExcepciones {

    private static final int MAX_DETALLE = 300;

    // La SUNAT contestó con un error SOAP
    @ExceptionHandler(SunatFaultException.class)
    public ResponseEntity<MensajeError> manejarFault(SunatFaultException e) {
        log.warn("La SUNAT respondió con un error SOAP {}", e.getCodigo());
        String detalle = e.getFaultString() == null ? "" : ": " + recortar(e.getFaultString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new MensajeError("La SUNAT respondió con un error" + detalle, e.getCodigo()));
    }

    // No hubo conexión o no respondió: 504 si fue por tiempo, 502 si no
    @ExceptionHandler(SunatNoDisponibleException.class)
    public ResponseEntity<MensajeError> manejarNoDisponible(SunatNoDisponibleException e) {
        log.warn("SUNAT no disponible: {}", e.getMessage());
        HttpStatus estado = e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(estado).body(new MensajeError(e.getMessage(), null));
    }

    @ExceptionHandler(SunatRespuestaInvalidaException.class)
    public ResponseEntity<MensajeError> manejarRespuestaInvalida(SunatRespuestaInvalidaException e) {
        log.warn("Respuesta inválida de la SUNAT: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new MensajeError("La SUNAT respondió algo que no se pudo entender", null));
    }

    // Falla la validación de un parámetro de la URL, por ejemplo un ticket que no es un número
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<MensajeError> manejarValidacion(HandlerMethodValidationException e) {
        String mensaje = e.getAllErrors().isEmpty() ? "La petición no es válida" : e.getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(new MensajeError(mensaje, null));
    }

    // Cualquier otra cosa. A quien llama no se le cuenta nada interno: el detalle queda solo en el log.
    // Los errores que Spring ya sabe responder (404, 405...) mantienen su código y no se convierten en 500.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MensajeError> manejarInesperado(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse errorDeSpring) {
            HttpStatusCode codigo = errorDeSpring.getStatusCode();
            HttpStatus estado = HttpStatus.resolve(codigo.value());
            return ResponseEntity.status(codigo).body(new MensajeError(estado != null ? estado.getReasonPhrase() : "Error", null));
        }
        log.error("Error inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MensajeError("Error interno", null));
    }

    private static String recortar(String texto) {
        return texto.length() <= MAX_DETALLE ? texto : texto.substring(0, MAX_DETALLE) + "...";
    }
}
