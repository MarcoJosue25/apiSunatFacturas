package apisunat.api_sunat.sunat.errores;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

// La tabla de códigos de error y observación de la SUNAT, leída de src/main/resources/sunat/codigos_sunat.csv al arrancar.
// Sale de las "Reglas de validación" oficiales (ver docs/sunat/NOTAS.md). Si el archivo está mal, la aplicación no arranca.
@Slf4j
@Component
public class CatalogoCodigos {

    static final String RECURSO = "/sunat/codigos_sunat.csv";
    private static final Pattern CODIGO = Pattern.compile("\\d{4}");

    private final Map<String, CodigoSunat> codigos;

    public CatalogoCodigos() {
        this(RECURSO);
    }

    // Con otro recurso solo para probar archivos dañados
    CatalogoCodigos(String recurso) {
        this.codigos = cargar(recurso);
        log.info("Tabla de códigos de la SUNAT cargada: {} códigos", codigos.size());
    }

    public Optional<CodigoSunat> buscar(String codigo) {
        // Map.copyOf no admite buscar null
        return codigo == null ? Optional.empty() : Optional.ofNullable(codigos.get(codigo));
    }

    public Collection<CodigoSunat> todos() {
        return codigos.values();
    }

    private static Map<String, CodigoSunat> cargar(String recurso) {
        InputStream entrada = CatalogoCodigos.class.getResourceAsStream(recurso);
        if (entrada == null) {
            throw new IllegalStateException("No se encontró la tabla de códigos de la SUNAT: " + recurso);
        }
        Map<String, CodigoSunat> resultado = new LinkedHashMap<>();
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            String encabezado = lector.readLine();
            if (!"codigo,tipo,hojas,descripcion".equals(encabezado)) {
                throw new IllegalStateException("La tabla de códigos de la SUNAT tiene un encabezado inesperado: " + encabezado);
            }
            String linea;
            int numero = 1;
            while ((linea = lector.readLine()) != null) {
                numero++;
                if (linea.isBlank()) {
                    continue;
                }
                List<String> campos = partir(linea, numero);
                if (campos.size() != 4) {
                    throw new IllegalStateException("Línea " + numero + " de la tabla de códigos: se esperaban 4 columnas y hay " + campos.size());
                }
                String codigo = campos.get(0);
                if (!CODIGO.matcher(codigo).matches()) {
                    throw new IllegalStateException("Línea " + numero + " de la tabla de códigos: código inválido '" + codigo + "'");
                }
                CodigoSunat.Tipo tipo;
                try {
                    tipo = CodigoSunat.Tipo.desde(campos.get(1));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Línea " + numero + " de la tabla de códigos: " + e.getMessage(), e);
                }
                CodigoSunat fila = new CodigoSunat(codigo, tipo, campos.get(2), campos.get(3));
                if (resultado.putIfAbsent(codigo, fila) != null) {
                    throw new IllegalStateException("Línea " + numero + " de la tabla de códigos: el código " + codigo + " está repetido");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la tabla de códigos de la SUNAT", e);
        }
        if (resultado.isEmpty()) {
            throw new IllegalStateException("La tabla de códigos de la SUNAT está vacía");
        }
        return Map.copyOf(resultado);
    }

    // CSV con campos entre comillas cuando llevan comas, y "" para una comilla dentro del campo
    static List<String> partir(String linea, int numeroDeLinea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') {
                        actual.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == ',') {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        if (entreComillas) {
            throw new IllegalStateException("Línea " + numeroDeLinea + " de la tabla de códigos: comillas sin cerrar");
        }
        campos.add(actual.toString());
        return campos;
    }
}
