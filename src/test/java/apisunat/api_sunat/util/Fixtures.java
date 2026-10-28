package apisunat.api_sunat.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// Lee los archivos de prueba de src/test/resources (respuestas reales del beta, certificados, etc.)
public final class Fixtures {

    private Fixtures() {
    }

    public static String texto(String ruta) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/" + ruta)) {
            if (in == null) {
                throw new IllegalStateException("No existe el archivo de prueba " + ruta);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
