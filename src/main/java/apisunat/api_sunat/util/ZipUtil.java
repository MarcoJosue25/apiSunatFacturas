package apisunat.api_sunat.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// El ZIP con el que la SUNAT recibe el comprobante (un solo XML adentro) y en el que devuelve la constancia de recepción (CDR).
public final class ZipUtil {

    // Una constancia real pesa unos pocos KB; este tope evita que una respuesta rara (un "zip bomb") llene la memoria
    public static final int MAX_BYTES_DESCOMPRIMIDOS = 5 * 1024 * 1024;

    // Fecha fija para que el mismo XML dé siempre exactamente el mismo ZIP (sirve para los reintentos)
    private static final LocalDateTime FECHA_FIJA = LocalDateTime.of(2000, 1, 1, 0, 0, 0);

    private ZipUtil() {
    }

    public record Entrada(String nombre, byte[] contenido) {
    }

    // Un ZIP con un solo archivo, sin carpetas
    public static byte[] comprimir(String nombreArchivo, byte[] contenido) {
        if (nombreArchivo == null || nombreArchivo.isBlank() || nombreArchivo.contains("/") || nombreArchivo.contains("\\")) {
            throw new IllegalArgumentException("El nombre del archivo del ZIP no es válido");
        }
        if (contenido == null || contenido.length == 0) {
            throw new IllegalArgumentException("No hay contenido para comprimir");
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream(contenido.length / 2 + 128);
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            ZipEntry entrada = new ZipEntry(nombreArchivo);
            entrada.setTimeLocal(FECHA_FIJA);
            zip.putNextEntry(entrada);
            zip.write(contenido);
            zip.closeEntry();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo comprimir el archivo", e);
        }
        return salida.toByteArray();
    }

    // El único archivo XML del ZIP, ignorando las carpetas (la SUNAT a veces agrega una vacía llamada "dummy/"; su nombre termina en "/", no en ".xml").
    // Falla si no es un ZIP, si no hay ningún XML, si hay más de uno o si lo descomprimido pasa del tope.
    public static Entrada leerXmlUnico(byte[] zip) {
        if (zip == null || zip.length == 0) {
            throw new IllegalArgumentException("El ZIP está vacío");
        }
        Entrada encontrada = null;
        int total = 0;
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry actual;
            while ((actual = entrada.getNextEntry()) != null) {
                byte[] contenido = leerHasta(entrada, MAX_BYTES_DESCOMPRIMIDOS - total);
                total += contenido.length;
                if (!actual.getName().toLowerCase().endsWith(".xml")) {
                    continue;
                }
                if (encontrada != null) {
                    throw new IllegalArgumentException("El ZIP tiene más de un archivo XML");
                }
                encontrada = new Entrada(actual.getName(), contenido);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("El ZIP está dañado o no es un ZIP", e);
        }
        if (encontrada == null) {
            throw new IllegalArgumentException("El ZIP no tiene ningún archivo XML");
        }
        return encontrada;
    }

    private static byte[] leerHasta(ZipInputStream entrada, int maximo) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int leidos;
        while ((leidos = entrada.read(buffer)) != -1) {
            if (salida.size() + leidos > maximo) {
                throw new IllegalArgumentException("El ZIP descomprimido pasa de " + MAX_BYTES_DESCOMPRIMIDOS + " bytes");
            }
            salida.write(buffer, 0, leidos);
        }
        return salida.toByteArray();
    }
}
