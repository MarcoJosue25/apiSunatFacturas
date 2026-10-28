package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.exception.CertificadoInvalidoException;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.SolicitudInvalidaException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.BoletaService;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class BoletaServiceImplTest {

    @Autowired
    private BoletaService servicio;
    @Autowired
    private ComprobanteService facturas;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private ResumenDiarioRepository resumenes;
    @Autowired
    private SerieRepository series;
    @Autowired
    private SunatProperties propiedades;

    @MockitoBean
    private BillServiceClient cliente;

    private String ruta;
    private String clave;

    @BeforeEach
    void limpiar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        resumenes.deleteAll();
        series.deleteAll();
        ruta = propiedades.getCertificado().getRuta();
        clave = propiedades.getCertificado().getClave();
    }

    @AfterEach
    void restaurar() {
        propiedades.getCertificado().setRuta(ruta);
        propiedades.getCertificado().setClave(clave);
    }

    private static ItemRequest item(String cantidad, String precio) {
        return ItemRequest.builder().descripcion("Polo básico").cantidad(new BigDecimal(cantidad)).precioUnitario(new BigDecimal(precio)).build();
    }

    private static ClienteBoletaRequest dni() {
        return ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").nombre("Luana Pazos").build();
    }

    private static BoletaRequest boleta(String id, ClienteBoletaRequest cliente, ItemRequest... items) {
        return BoletaRequest.builder().idExterno(id).cliente(cliente).items(List.of(items)).build();
    }

    // --- Camino feliz ---

    @Test
    void unaBoletaQuedaGuardadaPendienteDeResumenConSuNumeroYSuXmlFirmado() {
        BoletaService.Emision e = servicio.emitir(boleta("b1", dni(), item("2", "35.00")));

        assertThat(e.nueva()).isTrue();
        FacturaResponse r = e.boleta();
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.PENDIENTE_DE_RESUMEN);
        assertThat(r.getNumero()).isEqualTo("B001-1");
        assertThat(r.getImporteTotal()).isEqualByComparingTo("70.00");
        assertThat(r.getCodigoSunat()).isNull();

        Comprobante c = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(c.getTipoDocumento()).isEqualTo("03");
        assertThat(c.getTipoDocumentoCliente()).isEqualTo("1");
        assertThat(c.getDocumentoCliente()).isEqualTo("46237547");
        assertThat(c.getRazonSocialCliente()).isEqualTo("Luana Pazos");
        assertThat(c.getTotalGravado()).isEqualByComparingTo("59.32");
        assertThat(c.getTotalIgv()).isEqualByComparingTo("10.68");
        assertThat(verifica(c.getXmlFirmado())).isTrue();
        assertThat(new XsdValidator().validarFactura(c.getXmlFirmado())).isEmpty();
        assertThat(new String(c.getXmlFirmado(), StandardCharsets.ISO_8859_1)).contains("<cbc:ID>B001-1</cbc:ID>");
    }

    @Test
    void elServicioDeFacturasNoMuestraUnaBoletaNiSuXml() {
        Long id = servicio.emitir(boleta("b1", dni(), item("1", "10.00"))).boleta().getId();

        assertThatThrownBy(() -> facturas.obtener(id)).isInstanceOf(ComprobanteNoEncontradoException.class);
        assertThatThrownBy(() -> facturas.obtenerXml(id)).isInstanceOf(ComprobanteNoEncontradoException.class);
    }

    @Test
    void emitirUnaBoletaNoHablaConLaSunat() {
        servicio.emitir(boleta("b1", dni(), item("2", "35.00")));
        verifyNoInteractions(cliente);
    }

    @Test
    void lasBoletasSiguenSuPropiaNumeracionSeparadaDeLasFacturas() {
        FacturaRequest f = FacturaRequest.builder().idExterno("f1")
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("X").build()).items(List.of(item("1", "10.00"))).build();
        org.mockito.Mockito.doThrow(new apisunat.api_sunat.exception.SunatNoDisponibleException("x", false, null)).when(cliente).sendBill(anyString(), any());
        String numeroFactura = facturas.emitir(f).factura().getNumero();

        assertThat(numeroFactura).isEqualTo("F001-1");
        assertThat(servicio.emitir(boleta("b1", dni(), item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-1");
        assertThat(servicio.emitir(boleta("b2", dni(), item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-2");
    }

    @Test
    void unaBoletaAnonimaSeGuardaConTipoCeroYGuion() {
        FacturaResponse r = servicio.emitir(boleta("b1", null, item("1", "10.00"))).boleta();

        Comprobante c = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(c.getTipoDocumentoCliente()).isEqualTo("0");
        assertThat(c.getDocumentoCliente()).isEqualTo("-");
        assertThat(c.getRazonSocialCliente()).isEqualTo("CLIENTES VARIOS");
    }

    // --- El límite de S/ 700 ---

    @ParameterizedTest
    @CsvSource({"1,700.00", "7,100.00", "2,350.00", "1,699.99", "1,0.10"})
    void hastaSetecientosSolesUnaBoletaAnonimaSePermite(String cantidad, String precio) {
        assertThat(servicio.emitir(boleta("b-" + cantidad + precio, null, item(cantidad, precio))).nueva()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"1,700.01", "1,701.00", "2,350.01", "1,5000.00"})
    void deMasDeSetecientosSolesUnaBoletaAnonimaSeRechazaYNoGastaNumero(String cantidad, String precio) {
        assertThatThrownBy(() -> servicio.emitir(boleta("b-malo", null, item(cantidad, precio))))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("necesita el documento del cliente");
        assertThat(comprobantes.count()).isZero();
        assertThat(servicio.emitir(boleta("b-bueno", null, item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-1");
    }

    @Test
    void deMasDeSetecientosSolesConClienteSinDocumentoTambienSeRechaza() {
        ClienteBoletaRequest sinDocumento = ClienteBoletaRequest.builder().tipoDocumento("0").nombre("Juan").build();
        assertThatThrownBy(() -> servicio.emitir(boleta("b1", sinDocumento, item("1", "800.00")))).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void deMasDeSetecientosSolesConDocumentoSePermite() {
        assertThat(servicio.emitir(boleta("b1", dni(), item("1", "5000.00"))).boleta().getImporteTotal()).isEqualByComparingTo("5000.00");
    }

    // --- Idempotencia ---

    @Test
    void elMismoIdExternoDaLaMismaBoletaSinGastarNumero() {
        BoletaService.Emision primera = servicio.emitir(boleta("b1", dni(), item("1", "10.00")));
        BoletaService.Emision segunda = servicio.emitir(boleta("b1", dni(), item("1", "10.00")));

        assertThat(segunda.nueva()).isFalse();
        assertThat(segunda.boleta().getId()).isEqualTo(primera.boleta().getId());
        assertThat(comprobantes.count()).isEqualTo(1);
        assertThat(servicio.emitir(boleta("b2", dni(), item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-2");
    }

    @Test
    void elMismoIdExternoMuchasVecesALaVezDaUnaSolaBoleta() throws Exception {
        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<BoletaService.Emision>> trabajos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    return servicio.emitir(boleta("unica", dni(), item("1", "10.00")));
                }));
            }
            salida.countDown();
            Set<Long> ids = new HashSet<>();
            int nuevas = 0;
            for (Future<BoletaService.Emision> t : trabajos) {
                BoletaService.Emision e = t.get(60, TimeUnit.SECONDS);
                ids.add(e.boleta().getId());
                nuevas += e.nueva() ? 1 : 0;
            }
            assertThat(ids).hasSize(1);
            assertThat(nuevas).isEqualTo(1);
            assertThat(comprobantes.count()).isEqualTo(1);
            assertThat(servicio.emitir(boleta("otra", dni(), item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-2");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void boletasDistintasALaVezRecibenNumerosSeguidos() throws Exception {
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<BoletaService.Emision>> trabajos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                String id = "b" + i;
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    return servicio.emitir(boleta(id, dni(), item("1", "10.00")));
                }));
            }
            salida.countDown();
            Set<String> numeros = new HashSet<>();
            for (Future<BoletaService.Emision> t : trabajos) {
                numeros.add(t.get(90, TimeUnit.SECONDS).boleta().getNumero());
            }
            Set<String> esperados = new HashSet<>();
            for (int i = 1; i <= hilos; i++) {
                esperados.add("B001-" + i);
            }
            assertThat(numeros).isEqualTo(esperados);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Fallas antes de gastar el número ---

    @Test
    void unItemConMontoInvalidoSeRechazaSinGastarNumero() {
        assertThatThrownBy(() -> servicio.emitir(boleta("b1", dni(), item("1", "0"))))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThat(comprobantes.count()).isZero();
        assertThat(servicio.emitir(boleta("b2", dni(), item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-1");
    }

    @Test
    void sinCertificadoNoSeGastaNumeroNiSeGuardaNada() {
        propiedades.getCertificado().setRuta(null);
        propiedades.getCertificado().setClave(null);

        assertThatThrownBy(() -> servicio.emitir(boleta("b1", dni(), item("1", "10.00")))).isInstanceOf(CertificadoInvalidoException.class);

        assertThat(comprobantes.count()).isZero();
        restaurar();
        assertThat(servicio.emitir(boleta("b1", dni(), item("1", "10.00"))).boleta().getNumero()).isEqualTo("B001-1");
    }

    // --- Consultas ---

    @Test
    void obtenerYObtenerXmlDevuelvenLaBoleta() {
        FacturaResponse r = servicio.emitir(boleta("b1", dni(), item("1", "10.00"))).boleta();

        assertThat(servicio.obtener(r.getId())).isEqualTo(r);
        assertThat(verifica(servicio.obtenerXml(r.getId()))).isTrue();
    }

    @Test
    void unIdQueNoExisteODeUnaFacturaDaNoEncontrado() {
        org.mockito.Mockito.doThrow(new apisunat.api_sunat.exception.SunatNoDisponibleException("x", false, null)).when(cliente).sendBill(anyString(), any());
        FacturaRequest f = FacturaRequest.builder().idExterno("f1")
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("X").build()).items(List.of(item("1", "10.00"))).build();
        Long idFactura = facturas.emitir(f).factura().getId();

        assertThatThrownBy(() -> servicio.obtener(999_999L)).isInstanceOf(ComprobanteNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.obtener(idFactura)).isInstanceOf(ComprobanteNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.obtenerXml(idFactura)).isInstanceOf(ComprobanteNoEncontradoException.class);
    }

    @Test
    void laFechaDeEmisionQuedaGuardadaEnHoraDeLima() {
        FacturaResponse r = servicio.emitir(boleta("b1", dni(), item("1", "10.00"))).boleta();
        assertThat(r.getFechaEmision()).isNotNull();
        assertThat(r.getFechaEmision()).isBefore(java.time.LocalDateTime.now().plusDays(1));
    }
}
