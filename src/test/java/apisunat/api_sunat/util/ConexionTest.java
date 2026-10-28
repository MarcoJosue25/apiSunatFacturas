package apisunat.api_sunat.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Los datos de conexión a MySQL para los tests que no pasan por Spring: salen de las mismas variables de entorno que usa la aplicación
public final class ConexionTest {

    private ConexionTest() {
    }

    public static String url() {
        String url = System.getenv("DB_URL");
        return url != null ? url : "jdbc:mysql://localhost:3306/apisunat?createDatabaseIfNotExist=true";
    }

    public static String usuario() {
        return System.getenv().getOrDefault("DB_USUARIO", "apisunat");
    }

    public static String clave() {
        return System.getenv().getOrDefault("DB_CLAVE", "apisunat");
    }

    // La misma URL pero apuntando a otro esquema (por ejemplo uno vacío para probar las migraciones)
    public static String urlDeEsquema(String esquema) {
        String url = url();
        int barra = url.indexOf('/', "jdbc:mysql://".length());
        int interrogante = url.indexOf('?');
        String servidor = url.substring(0, barra);
        return servidor + "/" + esquema;
    }

    public static Connection conectarSinEsquema() throws SQLException {
        String url = url();
        int barra = url.indexOf('/', "jdbc:mysql://".length());
        return DriverManager.getConnection(url.substring(0, barra) + "/", usuario(), clave());
    }
}
