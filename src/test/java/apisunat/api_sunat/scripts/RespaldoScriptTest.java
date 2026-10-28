package apisunat.api_sunat.scripts;

import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.BoletaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// Se hace un respaldo de verdad con scripts/respaldo.sh y se restaura en otra base: los XML firmados vuelven idénticos, byte por byte.
// Necesita mysqldump y mysql en el PATH (si no están, el test se salta).
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class RespaldoScriptTest {

    private static final String BASE_RESTAURADA = "apisunat_restauracion_prueba";

    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.username}")
    private String usuario;
    @Value("${spring.datasource.password}")
    private String clave;
    @Autowired
    private BoletaService boletas;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private ResumenDiarioRepository resumenes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;

    private JdbcTemplate servidor() {
        return new JdbcTemplate(new DriverManagerDataSource(url.replaceFirst("/[^/?]+(\\?.*)?$", "/$1"), usuario, clave));
    }

    @AfterEach
    void limpiar() {
        servidor().execute("DROP DATABASE IF EXISTS " + BASE_RESTAURADA);
    }

    private static boolean existe(String programa) {
        try {
            return new ProcessBuilder(programa, "--version").redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private int ejecutar(List<String> comando, java.util.Map<String, String> entorno, Path salida) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(comando).redirectErrorStream(true).redirectOutput(salida.toFile());
        pb.environment().putAll(entorno);
        Process p = pb.start();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return p.exitValue();
    }

    @Test
    void elRespaldoSeRestauraEnOtraBaseConLosMismosXml(@TempDir Path carpeta) throws Exception {
        assumeTrue(existe("mysqldump") && existe("mysql"), "mysqldump y mysql no están instalados");
        Matcher m = Pattern.compile("^jdbc:mysql://([^:/]+):?(\\d*)/([^/?]+)").matcher(url);
        assertThat(m.find()).isTrue();
        String host = m.group(1), puerto = m.group(2).isEmpty() ? "3306" : m.group(2), base = m.group(3);

        intentos.deleteAll();
        comprobantes.deleteAll();
        resumenes.deleteAll();
        series.deleteAll();
        ItemRequest item = ItemRequest.builder().descripcion("Camión ñandú").cantidad(BigDecimal.ONE).precioUnitario(new BigDecimal("10.00")).build();
        Long id = boletas.emitir(BoletaRequest.builder().idExterno("respaldo-1").items(List.of(item))
                .cliente(ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").nombre("Luana").build()).build()).boleta().getId();
        byte[] xmlOriginal = comprobantes.findById(id).orElseThrow().getXmlFirmado();

        // 1. El respaldo, con el script
        Path salida = carpeta.resolve("salida.txt");
        int codigo = ejecutar(List.of("bash", new File("scripts/respaldo.sh").getAbsolutePath()),
                java.util.Map.of("DB_HOST", host, "DB_PUERTO", puerto, "DB_NOMBRE", base, "DB_USUARIO", usuario, "DB_CLAVE", clave,
                        "RESPALDO_DIR", carpeta.resolve("respaldos").toString()), salida);
        String texto = Files.readString(salida);
        assertThat(codigo).as(texto).isZero();
        Path respaldo = Path.of(texto.trim());
        assertThat(respaldo).exists().isNotEmptyFile();
        assertThat(respaldo.getFileName().toString()).matches(base + "-\\d{8}-\\d{6}\\.sql\\.gz");
        // Lo único que imprime es la ruta del archivo (la clave se pasa por el entorno y no sale en ninguna línea)
        assertThat(texto.trim().lines().count()).isEqualTo(1);

        // 2. Se restaura en una base nueva
        servidor().execute("DROP DATABASE IF EXISTS " + BASE_RESTAURADA);
        servidor().execute("CREATE DATABASE " + BASE_RESTAURADA);
        int restauracion = ejecutar(List.of("bash", "-c", "gunzip -c '" + respaldo + "' | mysql --host=" + host + " --port=" + puerto + " --user=" + usuario + " " + BASE_RESTAURADA),
                java.util.Map.of("MYSQL_PWD", clave), carpeta.resolve("restauracion.txt"));
        assertThat(restauracion).as(Files.readString(carpeta.resolve("restauracion.txt"))).isZero();

        // 3. Lo restaurado es lo mismo, byte por byte
        JdbcTemplate restaurada = new JdbcTemplate(new DriverManagerDataSource(url.replaceFirst("/[^/?]+(\\?.*)?$", "/" + BASE_RESTAURADA + "$1"), usuario, clave));
        assertThat(restaurada.queryForObject("SELECT COUNT(*) FROM comprobantes", Integer.class)).isEqualTo(1);
        byte[] xmlRestaurado = restaurada.queryForObject("SELECT xml_firmado FROM comprobantes WHERE id = ?", byte[].class, id);
        assertThat(xmlRestaurado).isEqualTo(xmlOriginal);
        Comprobante c = comprobantes.findById(id).orElseThrow();
        assertThat(restaurada.queryForObject("SELECT razon_social_cliente FROM comprobantes WHERE id = ?", String.class, id)).isEqualTo(c.getRazonSocialCliente());
    }

    @Test
    void sinLaClaveElScriptSeNiegaYNoDejaArchivos(@TempDir Path carpeta) throws Exception {
        assumeTrue(existe("mysqldump"), "mysqldump no está instalado");
        Path salida = carpeta.resolve("salida.txt");

        int codigo = ejecutar(List.of("bash", "-c", "env -u DB_CLAVE RESPALDO_DIR='" + carpeta.resolve("r") + "' bash '" + new File("scripts/respaldo.sh").getAbsolutePath() + "'"),
                java.util.Map.of(), salida);

        assertThat(codigo).isNotZero();
        assertThat(Files.readString(salida)).contains("Falta DB_CLAVE");
        assertThat(carpeta.resolve("r")).doesNotExist();
    }
}
