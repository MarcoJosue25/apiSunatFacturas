package apisunat.api_sunat.repository;

import apisunat.api_sunat.util.ConexionTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Las migraciones se aplican sobre un MySQL vacío (un esquema nuevo para esta prueba) y dejan lo que esperan las entidades
class MigracionesTest {

    private static final String ESQUEMA = "apisunat_migraciones_test";

    @BeforeEach
    @AfterEach
    void esquemaLimpio() throws SQLException {
        try (Connection c = ConexionTest.conectarSinEsquema(); Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS " + ESQUEMA);
        }
    }

    private Flyway flyway() throws SQLException {
        try (Connection c = ConexionTest.conectarSinEsquema(); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
        }
        return Flyway.configure()
                .dataSource(ConexionTest.urlDeEsquema(ESQUEMA), ConexionTest.usuario(), ConexionTest.clave())
                .locations("classpath:db/migration")
                .load();
    }

    private List<String> consultar(String sql) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (Connection c = java.sql.DriverManager.getConnection(ConexionTest.urlDeEsquema(ESQUEMA), ConexionTest.usuario(), ConexionTest.clave());
             Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                filas.add(r.getString(1));
            }
        }
        return filas;
    }

    @Test
    void lasMigracionesSeAplicanSobreUnMysqlVacio() throws SQLException {
        var resultado = flyway().migrate();

        assertThat(resultado.success).isTrue();
        assertThat(resultado.migrationsExecuted).isEqualTo(2);
        assertThat(consultar("SELECT table_name FROM information_schema.tables WHERE table_schema = '" + ESQUEMA + "' ORDER BY 1"))
                .contains("series", "comprobantes", "envio_intentos", "flyway_schema_history");
    }

    @Test
    void aplicarLasMigracionesDosVecesNoHaceNadaLaSegundaVez() throws SQLException {
        Flyway flyway = flyway();
        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void elCorrelativoYElIdExternoSonUnicos() throws SQLException {
        flyway().migrate();
        assertThat(consultar("SELECT index_name FROM information_schema.statistics WHERE table_schema = '" + ESQUEMA
                + "' AND non_unique = 0 AND index_name <> 'PRIMARY' GROUP BY index_name ORDER BY 1"))
                .containsExactlyInAnyOrder("uk_series_tipo_serie", "uk_comprobantes_id_externo", "uk_comprobantes_numero");
    }

    @Test
    void lasTablasUsanInnodbYUtf8mb4() throws SQLException {
        flyway().migrate();
        assertThat(consultar("SELECT DISTINCT engine FROM information_schema.tables WHERE table_schema = '" + ESQUEMA
                + "' AND table_name <> 'flyway_schema_history'")).containsExactly("InnoDB");
        assertThat(consultar("SELECT DISTINCT SUBSTRING_INDEX(table_collation, '_', 1) FROM information_schema.tables WHERE table_schema = '"
                + ESQUEMA + "' AND table_name <> 'flyway_schema_history'")).containsExactly("utf8mb4");
    }

    @Test
    void losIntentosDependenDeUnComprobanteQueExiste() throws SQLException {
        flyway().migrate();
        assertThat(consultar("SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = '" + ESQUEMA
                + "' AND constraint_type = 'FOREIGN KEY'")).containsExactly("fk_envio_intentos_comprobante");
    }

    @Test
    void elXmlYLaConstanciaPuedenPesarMasDe64Kb() throws SQLException {
        flyway().migrate();
        assertThat(consultar("SELECT data_type FROM information_schema.columns WHERE table_schema = '" + ESQUEMA
                + "' AND table_name = 'comprobantes' AND column_name IN ('xml_firmado', 'cdr_zip')")).containsExactly("longblob", "longblob");
    }

    @Test
    void laMigracionDeReintentosAgregaLasColumnasYElIndice() throws SQLException {
        flyway().migrate();
        assertThat(consultar("SELECT column_name FROM information_schema.columns WHERE table_schema = '" + ESQUEMA
                + "' AND table_name = 'comprobantes' AND column_name IN ('intentos_fallidos', 'proximo_intento') ORDER BY 1"))
                .containsExactly("intentos_fallidos", "proximo_intento");
        assertThat(consultar("SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = '" + ESQUEMA
                + "' AND index_name = 'ix_comprobantes_reintento'")).containsExactly("ix_comprobantes_reintento");
    }

    @Test
    void laMigracionDeReintentosConservaLosComprobantesQueYaExistian() throws SQLException {
        // Se aplica solo la V1, se inserta un comprobante y luego se aplica la V2 sobre esos datos
        Flyway soloV1 = Flyway.configure()
                .dataSource(ConexionTest.urlDeEsquema(ESQUEMA), ConexionTest.usuario(), ConexionTest.clave())
                .locations("classpath:db/migration").target("1").load();
        try (Connection c = ConexionTest.conectarSinEsquema(); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + ESQUEMA + " CHARACTER SET utf8mb4");
        }
        soloV1.migrate();
        try (Connection c = java.sql.DriverManager.getConnection(ConexionTest.urlDeEsquema(ESQUEMA), ConexionTest.usuario(), ConexionTest.clave());
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO comprobantes (id_externo, tipo_documento, serie, correlativo, fecha_emision, ruc_cliente, razon_social_cliente,"
                    + " importe_total, estado, creado_en, actualizado_en) VALUES ('viejo', '01', 'F001', 1, NOW(), '20123456786', 'X', 10.00,"
                    + " 'ACEPTADO', NOW(3), NOW(3))");
        }

        Flyway.configure().dataSource(ConexionTest.urlDeEsquema(ESQUEMA), ConexionTest.usuario(), ConexionTest.clave())
                .locations("classpath:db/migration").load().migrate();

        assertThat(consultar("SELECT CONCAT(intentos_fallidos, '/', IFNULL(proximo_intento, 'null')) FROM comprobantes WHERE id_externo = 'viejo'"))
                .containsExactly("0/null");
    }
}
