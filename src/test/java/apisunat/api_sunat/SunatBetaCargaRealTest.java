package apisunat.api_sunat;

import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.model.enums.EstadoResumen;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.ResumenDiarioService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Carga contra el BETA real (solo con -Pbeta): 500 facturas y 1500 boletas por la API, en paralelo, y luego los resúmenes de las boletas.
// Cuántas se piden se cambia con -Dcarga.facturas=... -Dcarga.boletas=...
@Tag("beta")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class SunatBetaCargaRealTest {

    @LocalServerPort
    private int puerto;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private ResumenDiarioRepository resumenes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;
    @Autowired
    private ResumenDiarioService resumenService;

    private final List<Long> tiempos = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger erroresHttp = new AtomicInteger();
    private final Map<Integer, AtomicInteger> porCodigoHttp = new java.util.concurrent.ConcurrentHashMap<>();

    private void emitir(RestClient http, String ruta, String cuerpo) {
        long ini = System.nanoTime();
        try {
            int codigo = http.post().uri(ruta).contentType(MediaType.APPLICATION_JSON).body(cuerpo)
                    .exchange((p, r) -> r.getStatusCode().value());
            porCodigoHttp.computeIfAbsent(codigo, k -> new AtomicInteger()).incrementAndGet();
        } catch (RuntimeException e) {
            erroresHttp.incrementAndGet();
        }
        tiempos.add((System.nanoTime() - ini) / 1_000_000);
    }

    @Test
    void cargaDeFacturasYBoletasContraElBeta() throws Exception {
        int nFacturas = Integer.getInteger("carga.facturas", 500);
        int nBoletas = Integer.getInteger("carga.boletas", 1500);
        intentos.deleteAll();
        comprobantes.deleteAll();
        resumenes.deleteAll();
        series.deleteAll();
        RestClient http = RestClient.builder().baseUrl("http://localhost:" + puerto)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory()).build();
        String prefijo = "carga-" + System.currentTimeMillis() + "-";

        long inicio = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<?>> trabajos = new ArrayList<>();
        for (int i = 0; i < nFacturas; i++) {
            String id = prefijo + "f" + i;
            trabajos.add(pool.submit(() -> emitir(http, "/api/v1/facturas", """
                    {"idExterno": "%s", "cliente": {"ruc": "20123456786", "razonSocial": "CLIENTE DE PRUEBA SAC"},
                     "items": [{"descripcion": "Polo básico", "cantidad": 1, "precioUnitario": 0.50}]}""".formatted(id))));
        }
        for (int i = 0; i < nBoletas; i++) {
            String id = prefijo + "b" + i;
            trabajos.add(pool.submit(() -> emitir(http, "/api/v1/boletas", """
                    {"idExterno": "%s", "cliente": {"tipoDocumento": "1", "numeroDocumento": "46237547", "nombre": "LUANA PAZOS"},
                     "items": [{"descripcion": "Caramelo", "cantidad": 1, "precioUnitario": 0.10}]}""".formatted(id))));
        }
        for (Future<?> t : trabajos) {
            t.get(40, TimeUnit.MINUTES);
        }
        pool.shutdown();
        long msEmision = System.currentTimeMillis() - inicio;

        // Los resúmenes de las boletas (500 por resumen) y su ticket
        long iniResumen = System.currentTimeMillis();
        List<Long> ids = resumenService.generarYEnviar();
        long limite = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis();
        while (System.currentTimeMillis() < limite) {
            boolean pendiente = false;
            for (Long id : ids) {
                ResumenDiario r = resumenes.findById(id).orElseThrow();
                if (r.getEstado() == EstadoResumen.ENVIADO) {
                    resumenService.consultar(id);
                    pendiente = true;
                } else if (r.getEstado() == EstadoResumen.GENERADO || r.getEstado() == EstadoResumen.ERROR_TEMPORAL) {
                    resumenService.enviar(id);
                    pendiente = true;
                }
            }
            if (!pendiente) {
                break;
            }
            Thread.sleep(1500);
        }
        long msResumen = System.currentTimeMillis() - iniResumen;

        Map<EstadoComprobante, Integer> estados = new EnumMap<>(EstadoComprobante.class);
        int conMasDeUnIntento = 0;
        for (Comprobante c : comprobantes.findAll()) {
            estados.merge(c.getEstado(), 1, Integer::sum);
            if (c.getIntentosFallidos() > 0) {
                conMasDeUnIntento++;
            }
        }
        Map<EstadoResumen, Integer> estadosResumen = new EnumMap<>(EstadoResumen.class);
        resumenes.findAll().forEach(r -> estadosResumen.merge(r.getEstado(), 1, Integer::sum));
        List<Long> orden = new ArrayList<>(tiempos);
        Collections.sort(orden);

        System.out.println("=== CARGA " + nFacturas + " facturas + " + nBoletas + " boletas ===");
        System.out.println("HTTP: " + porCodigoHttp + " errores de conexión: " + erroresHttp.get());
        System.out.println("Emisión total: " + msEmision / 1000 + " s (" + (nFacturas + nBoletas) * 1000L / Math.max(1, msEmision) + " req/s)");
        System.out.println("Latencia POST ms: p50=" + orden.get(orden.size() / 2) + " p95=" + orden.get(orden.size() * 95 / 100) + " max=" + orden.get(orden.size() - 1));
        System.out.println("Resúmenes: " + ids.size() + " en " + msResumen / 1000 + " s, estados " + estadosResumen);
        System.out.println("Estados de los comprobantes: " + estados);
        System.out.println("Comprobantes con intentos fallidos: " + conMasDeUnIntento + ", intentos registrados: " + intentos.count());
        System.out.println("Números: facturas=" + comprobantes.findAll().stream().filter(c -> !c.esBoleta()).map(Comprobante::getCorrelativo).distinct().count()
                + " boletas=" + comprobantes.findAll().stream().filter(Comprobante::esBoleta).map(Comprobante::getCorrelativo).distinct().count());

        assertThat(erroresHttp.get()).isZero();
        assertThat(comprobantes.count()).isEqualTo(nFacturas + nBoletas);
    }
}
