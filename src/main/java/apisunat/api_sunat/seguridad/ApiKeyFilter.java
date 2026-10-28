package apisunat.api_sunat.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Pide la llave de la API en la cabecera X-API-Key. La compara en tiempo constante: se comparan los SHA-256 de las dos llaves con
// MessageDigest.isEqual, así el tiempo de respuesta no depende de cuántos caracteres coincidan ni del largo de la llave.
// Si no hay llave configurada deja pasar todo (desarrollo); el perfil prod no arranca así.
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-API-Key";

    private final byte[] huellaEsperada;

    public ApiKeyFilter(String llave) {
        this.huellaEsperada = llave == null || llave.isEmpty() ? null : huella(llave);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (huellaEsperada == null || coincide(request.getHeader(CABECERA))) {
            chain.doFilter(request, response);
            return;
        }
        logger.warn("Petición rechazada por llave de API ausente o incorrecta: " + request.getMethod() + " " + request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "ApiKey");
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write("{\"mensaje\":\"Falta la llave de la API o es incorrecta\"}".getBytes(StandardCharsets.UTF_8));
    }

    private boolean coincide(String recibida) {
        // Sin cabecera también se hace la comparación (con texto vacío), para no responder más rápido en ese caso
        return MessageDigest.isEqual(huellaEsperada, huella(recibida == null ? "" : recibida));
    }

    private static byte[] huella(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Esta JVM no tiene SHA-256", e);
        }
    }

    public boolean estaActivo() {
        return huellaEsperada != null;
    }
}
