package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.model.enums.EstadoResumen;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.BoletaService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatus;
import apisunat.api_sunat.util.RelojMutable;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

// Con los dos planificadores del resumen encendidos de verdad (cada segundo) y el reloj de la aplicación movido a mano: la boleta pasa sola
// de "pendiente de resumen" a "aceptada" sin que nadie llame a los servicios
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123",
        "sunat.resumen.habilitado=true", "sunat.resumen.cron=* * * * * *", "sunat.resumen.intervalo-consulta-segundos=1"})
class ResumenDiarioSchedulerTest {

    private static final Instant INICIO = Instant.parse("2026-10-28T15:00:00Z");

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable(INICIO);
        }
    }

    @Autowired
    private RelojMutable reloj;
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
    @MockitoBean
    private BillServiceClient cliente;

    @BeforeEach
    void preparar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        resumenes.deleteAll();
        series.deleteAll();
        reloj.fijar(INICIO);
        reset(cliente);
    }

    private static <T> T esperar(Supplier<T> lectura, T esperado, Duration maximo) throws InterruptedException {
        long limite = System.currentTimeMillis() + maximo.toMillis();
        T actual = lectura.get();
        while (!esperado.equals(actual) && System.currentTimeMillis() < limite) {
            Thread.sleep(200);
            actual = lectura.get();
        }
        return actual;
    }

    private static String cdrAceptado() {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><ar:ApplicationResponse"
                + " xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>RC-20261028-1</cbc:ReferenceID><cbc:ResponseCode>0</cbc:ResponseCode>"
                + "<cbc:Description>aceptado</cbc:Description></cac:Response></cac:DocumentResponse></ar:ApplicationResponse>";
        return Base64.getEncoder().encodeToString(ZipUtil.comprimir("R-RC-20261028-1.xml", xml.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void laBoletaPasaSolaDePendienteDeResumenAAceptada() throws Exception {
        ItemRequest item = ItemRequest.builder().descripcion("Polo").cantidad(BigDecimal.ONE).precioUnitario(new BigDecimal("10.00")).build();
        Long id = boletas.emitir(BoletaRequest.builder().idExterno("b1").items(List.of(item))
                .cliente(ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").nombre("Luana").build()).build()).boleta().getId();
        doReturn("T1").when(cliente).sendSummary(anyString(), any());
        doReturn(RespuestaStatus.builder().statusCode("0").content(cdrAceptado()).build()).when(cliente).getStatus("T1");

        // El planificador de las 22:00 (aquí, cada segundo) arma y envía el resumen
        EstadoResumen enviado = esperar(() -> resumenes.findAll().stream().map(ResumenDiario::getEstado).findFirst().orElse(null),
                EstadoResumen.ENVIADO, Duration.ofSeconds(30));
        assertThat(enviado).isEqualTo(EstadoResumen.ENVIADO);

        // Pasa el minuto de espera (en el reloj de la aplicación) y el otro planificador consulta el ticket
        reloj.avanzar(Duration.ofMinutes(2));
        EstadoComprobante estado = esperar(() -> comprobantes.findById(id).orElseThrow().getEstado(), EstadoComprobante.ACEPTADO, Duration.ofSeconds(30));
        assertThat(estado).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(resumenes.findAll().get(0).getEstado()).isEqualTo(EstadoResumen.ACEPTADO);
    }
}
