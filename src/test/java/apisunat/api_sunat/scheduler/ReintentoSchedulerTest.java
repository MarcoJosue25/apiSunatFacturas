package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.util.Fixtures;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

// Con el planificador encendido de verdad (cada segundo) y el reloj de la aplicación movido a mano: la SUNAT vuelve y el
// comprobante se envía solo, sin que nadie llame al servicio
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123",
        "sunat.reintentos.habilitado=true", "sunat.reintentos.intervalo-segundos=1"})
class ReintentoSchedulerTest {

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMutable relojMutable() {
            return new RelojMutable(Instant.parse("2026-10-28T15:00:00Z"));
        }
    }

    @Autowired
    private RelojMutable reloj;
    @Autowired
    private ComprobanteService comprobanteService;
    @Autowired
    private ComprobanteRepository comprobantes;
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
        series.deleteAll();
        reloj.fijar(Instant.parse("2026-10-28T15:00:00Z"));
        reset(cliente);
    }

    private static FacturaRequest factura(String id) {
        return FacturaRequest.builder().idExterno(id)
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(List.of(ItemRequest.builder().descripcion("Polo").cantidad(new BigDecimal("1")).precioUnitario(new BigDecimal("10.00")).build()))
                .build();
    }

    private EstadoComprobante esperarEstado(Long id, EstadoComprobante esperado, Duration maximo) throws InterruptedException {
        long limite = System.currentTimeMillis() + maximo.toMillis();
        EstadoComprobante actual = null;
        while (System.currentTimeMillis() < limite) {
            actual = comprobantes.findById(id).orElseThrow().getEstado();
            if (actual == esperado) {
                return actual;
            }
            Thread.sleep(200);
        }
        return actual;
    }

    @Test
    void apagoLaSunatEmitoLaRestauroYElComprobanteSeEnviaSolo() throws Exception {
        doThrow(new SunatNoDisponibleException("sin conexión", false, null)).when(cliente).sendBill(anyString(), any());
        FacturaResponse r = comprobanteService.emitir(factura("pedido-1")).factura();
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);

        // La SUNAT vuelve y pasan 2 minutos (en el reloj de la aplicación); el planificador hace el resto
        doAnswer(i -> {
            String nombre = i.getArgument(0);
            String numero = nombre.substring(nombre.indexOf("-01-") + 4, nombre.length() - 4);
            String xml = Fixtures.texto("sunat/cdr/aceptado.xml").replace("FA01-981", numero);
            return ZipUtil.comprimir("R-" + nombre.replace(".zip", ".xml"), xml.getBytes(StandardCharsets.ISO_8859_1));
        }).when(cliente).sendBill(anyString(), any());
        reloj.avanzar(Duration.ofMinutes(2));

        assertThat(esperarEstado(r.getId(), EstadoComprobante.ACEPTADO, Duration.ofSeconds(30))).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(comprobantes.findById(r.getId()).orElseThrow().getCdrZip()).isNotEmpty();
    }
}
