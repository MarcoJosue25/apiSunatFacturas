package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.exception.CertificadoInvalidoException;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.SolicitudInvalidaException;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.EnvioIntento;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.service.FacturaService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
import java.util.concurrent.atomic.AtomicInteger;

import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// El recorrido con la base de datos MySQL real y la SUNAT simulada
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class ComprobanteServiceImplTest {

    @Autowired
    private ComprobanteService servicio;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;
    @Autowired
    private SunatProperties propiedades;

    @MockitoBean
    private BillServiceClient cliente;

    @MockitoSpyBean
    private FacturaService facturaService;

    private String rutaCertificado;
    private String claveCertificado;

    @BeforeEach
    void limpiar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
        rutaCertificado = propiedades.getCertificado().getRuta();
        claveCertificado = propiedades.getCertificado().getClave();
    }

    @AfterEach
    void restaurar() {
        propiedades.getCertificado().setRuta(rutaCertificado);
        propiedades.getCertificado().setClave(claveCertificado);
    }

    // --- Datos ---

    private static FacturaRequest factura(String idExterno) {
        return FacturaRequest.builder().idExterno(idExterno)
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(List.of(ItemRequest.builder().descripcion("Polo básico negro").cantidad(new BigDecimal("2"))
                        .precioUnitario(new BigDecimal("35.00")).build()))
                .build();
    }

    // La constancia que contestaría la SUNAT para el número que se le mandó (sale del nombre del ZIP)
    private static byte[] constancia(String nombreZip, String fixture, String codigo) {
        String numero = nombreZip.substring(nombreZip.indexOf("-01-") + 4, nombreZip.length() - 4);
        String xml = Fixtures.texto(fixture).replace("FA01-981", numero).replace("FT01-982", numero).replace(">2047<", ">" + codigo + "<");
        return ZipUtil.comprimir("R-" + nombreZip.replace(".zip", ".xml"), xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    private void sunatAcepta() {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0")).when(cliente).sendBill(anyString(), any());
    }

    private void sunatRechaza(String codigo) {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/rechazado.xml", codigo)).when(cliente).sendBill(anyString(), any());
    }

    // --- Camino feliz ---

    @Test
    void unaFacturaAceptadaQuedaGuardadaConSuNumeroSuXmlYSuConstancia() {
        sunatAcepta();

        ComprobanteService.Emision emision = servicio.emitir(factura("pedido-1"));

        assertThat(emision.nueva()).isTrue();
        FacturaResponse r = emision.factura();
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(r.getNumero()).isEqualTo("F001-1");
        assertThat(r.getIdExterno()).isEqualTo("pedido-1");
        assertThat(r.getImporteTotal()).isEqualByComparingTo("70.00");
        assertThat(r.getCodigoSunat()).isEqualTo("0");
        assertThat(r.getDescripcionSunat()).isEqualTo("La Factura numero F001-1, ha sido aceptada");
        assertThat(r.getObservaciones()).isEmpty();

        Comprobante guardado = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(guardado.getXmlFirmado()).isNotEmpty();
        assertThat(verifica(guardado.getXmlFirmado())).isTrue();
        assertThat(guardado.getCdrZip()).isNotEmpty();
        assertThat(guardado.getRucCliente()).isEqualTo("20123456786");
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(r.getId())).singleElement()
                .satisfies(i -> {
                    assertThat(i.getResultado()).isEqualTo("ACEPTADO");
                    assertThat(i.getCodigo()).isEqualTo("0");
                });
    }

    @Test
    void elZipQueSeEnviaTieneElNombreYElXmlGuardado() {
        sunatAcepta();

        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        ArgumentCaptor<String> nombre = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> zip = ArgumentCaptor.forClass(byte[].class);
        verify(cliente).sendBill(nombre.capture(), zip.capture());
        assertThat(nombre.getValue()).isEqualTo("20100066603-01-F001-1.zip");
        assertThat(ZipUtil.leerXmlUnico(zip.getValue()).contenido()).isEqualTo(servicio.obtenerXml(r.getId()));
    }

    @Test
    void lasFacturasSiguenElOrdenDelCorrelativoSinSaltos() {
        sunatAcepta();

        List<String> numeros = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            numeros.add(servicio.emitir(factura("pedido-" + i)).factura().getNumero());
        }

        assertThat(numeros).containsExactly("F001-1", "F001-2", "F001-3", "F001-4", "F001-5");
    }

    @Test
    void conObservacionesQuedanGuardadasUnaPorLinea() {
        when(cliente.sendBill(anyString(), any())).thenAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado-con-observaciones.xml", "0"));

        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO_CON_OBSERVACIONES);
        assertThat(r.getObservaciones()).containsExactly("4031 - Debe indicar el nombre comercial", "4001 - El numero de RUC del receptor no existe.");
    }

    // --- Rechazos y errores de la SUNAT ---

    @Test
    void unRechazoConsumeElNumeroYLaSiguienteFacturaUsaElOtro() {
        sunatRechaza("3270");
        FacturaResponse rechazada = servicio.emitir(factura("pedido-1")).factura();
        sunatAcepta();
        FacturaResponse siguiente = servicio.emitir(factura("pedido-2")).factura();

        assertThat(rechazada.getEstado()).isEqualTo(EstadoComprobante.RECHAZADO);
        assertThat(rechazada.getCodigoSunat()).isEqualTo("3270");
        assertThat(rechazada.getNumero()).isEqualTo("F001-1");
        assertThat(siguiente.getNumero()).isEqualTo("F001-2");
        assertThat(comprobantes.findById(rechazada.getId()).orElseThrow().numero()).isEqualTo("F001-1");
    }

    @ParameterizedTest
    @CsvSource({"0111,ERROR_DE_CONFIGURACION", "0151,ERROR_DE_DATOS", "0306,ERROR_DE_DATOS", "2335,ERROR_DE_CONFIGURACION",
            "1033,REVISAR", "0200,REVISAR", "0100,ERROR_TEMPORAL", "0109,ERROR_TEMPORAL"})
    void unErrorSoapDejaElComprobanteEnSuEstadoYGuardaElIntento(String codigo, EstadoComprobante esperado) {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatFaultException("S:Client." + codigo, "Texto " + codigo, codigo));

        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        assertThat(r.getEstado()).isEqualTo(esperado);
        assertThat(r.getCodigoSunat()).isEqualTo(codigo);
        assertThat(r.getDescripcionSunat()).isEqualTo("Texto " + codigo);
        Comprobante c = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(c.getCdrZip()).isNull();
        assertThat(c.getXmlFirmado()).isNotEmpty();
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(r.getId())).hasSize(1);
    }

    // --- La SUNAT caída ---

    @Test
    void siLaSunatNoRespondeLaFacturaQuedaGuardadaConSuNumeroParaReintentar() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatNoDisponibleException("La SUNAT no respondió a tiempo", true, null));

        ComprobanteService.Emision emision = servicio.emitir(factura("pedido-1"));

        assertThat(emision.nueva()).isTrue();
        assertThat(emision.factura().getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(emision.factura().getNumero()).isEqualTo("F001-1");
        assertThat(emision.factura().getDescripcionSunat()).isEqualTo("La SUNAT no respondió a tiempo");
        Comprobante c = comprobantes.findById(emision.factura().getId()).orElseThrow();
        assertThat(c.getXmlFirmado()).isNotEmpty();
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(c.getId())).singleElement()
                .satisfies(i -> assertThat(i.getResultado()).isEqualTo("SIN_RESPUESTA"));
    }

    @Test
    void cadaFalloProgramaElSiguienteIntentoConLaEsperaQueCorresponde() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatNoDisponibleException("sin conexión", false, null));

        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();
        Comprobante primero = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(primero.getIntentosFallidos()).isEqualTo(1);
        // espera de 1 minuto después del primer fallo
        assertThat(java.time.Duration.between(primero.getActualizadoEn(), primero.getProximoIntento())).isEqualTo(java.time.Duration.ofMinutes(1));

        servicio.enviar(r.getId());
        Comprobante segundo = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(segundo.getIntentosFallidos()).isEqualTo(2);
        assertThat(java.time.Duration.between(segundo.getActualizadoEn(), segundo.getProximoIntento())).isEqualTo(java.time.Duration.ofMinutes(2));
    }

    @Test
    void unaFacturaAceptadaNoTieneProximoIntento() {
        sunatAcepta();
        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();
        Comprobante c = comprobantes.findById(r.getId()).orElseThrow();
        assertThat(c.getProximoIntento()).isNull();
        assertThat(c.getIntentosFallidos()).isZero();
    }

    @Test
    void unaRespuestaIlegibleDeLaSunatTambienDejaLaFacturaParaReintentar() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatRespuestaInvalidaException("html"));

        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
    }

    @Test
    void despuesDeCaerseLaSunatSePuedeReenviarLaMismaFacturaConElMismoNumeroYElMismoXml() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new SunatNoDisponibleException("sin conexión", false, null));
        FacturaResponse caida = servicio.emitir(factura("pedido-1")).factura();
        byte[] xmlAntes = servicio.obtenerXml(caida.getId());

        sunatAcepta();
        FacturaResponse reenviada = servicio.enviar(caida.getId());

        assertThat(reenviada.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(reenviada.getNumero()).isEqualTo("F001-1");
        assertThat(servicio.obtenerXml(caida.getId())).isEqualTo(xmlAntes);
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(caida.getId())).extracting(EnvioIntento::getResultado)
                .containsExactly("SIN_RESPUESTA", "ACEPTADO");
        verify(cliente, times(2)).sendBill(anyString(), any());
    }

    @Test
    void unaFacturaYaAceptadaNoSeVuelveAEnviar() {
        sunatAcepta();
        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        assertThatThrownBy(() -> servicio.enviar(r.getId())).isInstanceOf(IllegalStateException.class);
        verify(cliente, times(1)).sendBill(anyString(), any());
        assertThat(servicio.obtener(r.getId()).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    @Test
    void siFallaAlgoInesperadoAlEnviarNoQuedaAtascadaEnEnviando() {
        when(cliente.sendBill(anyString(), any())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> servicio.emitir(factura("pedido-1"))).isInstanceOf(IllegalStateException.class);

        Comprobante c = comprobantes.findByIdExterno("pedido-1").orElseThrow();
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(c.getDescripcionSunat()).isEqualTo("Error interno al enviar");
    }

    // --- Interbloqueos de MySQL ---

    @Test
    void siMysqlAbortaLaTransaccionPorInterbloqueoSeVuelveAIntentarYNoSePierdeNingunNumero() {
        sunatAcepta();
        // El interbloqueo ocurre DESPUÉS de reservar el número, dentro de la misma transacción
        doThrow(new org.springframework.dao.CannotAcquireLockException("Deadlock found when trying to get lock"))
                .doCallRealMethod().when(facturaService).prepararXml(any(), anyString(), anyLong(), any());

        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        assertThat(r.getNumero()).isEqualTo("F001-1");
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(comprobantes.count()).isEqualTo(1);
        verify(facturaService, times(2)).prepararXml(any(), anyString(), anyLong(), any());
    }

    @Test
    void siElInterbloqueoSeRepiteTresVecesSeDaPorVencidoYNoQuedaNadaGuardado() {
        sunatAcepta();
        doThrow(new org.springframework.dao.CannotAcquireLockException("Deadlock found when trying to get lock"))
                .when(facturaService).prepararXml(any(), anyString(), anyLong(), any());

        assertThatThrownBy(() -> servicio.emitir(factura("pedido-1")))
                .isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);

        verify(facturaService, times(3)).prepararXml(any(), anyString(), anyLong(), any());
        assertThat(comprobantes.count()).isZero();
        verify(cliente, never()).sendBill(anyString(), any());
        org.mockito.Mockito.reset(facturaService);
        sunatAcepta();
        assertThat(servicio.emitir(factura("pedido-2")).factura().getNumero()).isEqualTo("F001-1");
    }

    // --- Idempotencia ---

    @Test
    void elMismoIdExternoDosVecesDaLaMismaFacturaYSoloSeEnviaUnaVez() {
        sunatAcepta();

        ComprobanteService.Emision primera = servicio.emitir(factura("pedido-1"));
        ComprobanteService.Emision segunda = servicio.emitir(factura("pedido-1"));

        assertThat(primera.nueva()).isTrue();
        assertThat(segunda.nueva()).isFalse();
        assertThat(segunda.factura().getId()).isEqualTo(primera.factura().getId());
        assertThat(segunda.factura().getNumero()).isEqualTo("F001-1");
        assertThat(segunda.factura().getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(comprobantes.count()).isEqualTo(1);
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void unaPeticionRepetidaSeResuelveSinArmarNiFirmarNada() {
        sunatAcepta();
        FacturaResponse original = servicio.emitir(factura("pedido-1")).factura();

        // Sin certificado no se podría firmar: si aun así devuelve la factura, no armó ni firmó nada
        propiedades.getCertificado().setRuta(null);
        propiedades.getCertificado().setClave(null);
        ComprobanteService.Emision repetida = servicio.emitir(factura("pedido-1"));

        assertThat(repetida.nueva()).isFalse();
        assertThat(repetida.factura().getId()).isEqualTo(original.getId());
    }

    @Test
    void elMismoIdExternoConOtrosDatosDevuelveLaFacturaOriginalSinCambiarla() {
        sunatAcepta();
        FacturaResponse original = servicio.emitir(factura("pedido-1")).factura();

        FacturaRequest otra = factura("pedido-1");
        otra.getItems().get(0).setCantidad(new BigDecimal("50"));
        FacturaResponse repetida = servicio.emitir(otra).factura();

        assertThat(repetida.getId()).isEqualTo(original.getId());
        assertThat(repetida.getImporteTotal()).isEqualByComparingTo("70.00");
    }

    @Test
    void elMismoIdExternoMuchasVecesALaVezDaUnaSolaFacturaYNoGastaNumerosDeMas() throws Exception {
        sunatAcepta();
        int hilos = 12;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ComprobanteService.Emision>> trabajos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    return servicio.emitir(factura("pedido-unico"));
                }));
            }
            salida.countDown();
            Set<Long> ids = new HashSet<>();
            int nuevas = 0;
            for (Future<ComprobanteService.Emision> t : trabajos) {
                ComprobanteService.Emision e = t.get(60, TimeUnit.SECONDS);
                ids.add(e.factura().getId());
                nuevas += e.nueva() ? 1 : 0;
            }
            assertThat(ids).hasSize(1);
            assertThat(nuevas).isEqualTo(1);
            assertThat(comprobantes.count()).isEqualTo(1);
            verify(cliente, times(1)).sendBill(anyString(), any());

            // y no se gastó ningún número de más: la siguiente factura es la 2
            assertThat(servicio.emitir(factura("otro")).factura().getNumero()).isEqualTo("F001-2");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void facturasDistintasALaVezRecibenNumerosDistintosYSeguidos() throws Exception {
        sunatAcepta();
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<ComprobanteService.Emision>> trabajos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                String id = "pedido-" + i;
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    return servicio.emitir(factura(id));
                }));
            }
            salida.countDown();
            Set<String> numeros = new HashSet<>();
            for (Future<ComprobanteService.Emision> t : trabajos) {
                FacturaResponse r = t.get(90, TimeUnit.SECONDS).factura();
                assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
                numeros.add(r.getNumero());
            }
            Set<String> esperados = new HashSet<>();
            for (int i = 1; i <= hilos; i++) {
                esperados.add("F001-" + i);
            }
            assertThat(numeros).isEqualTo(esperados);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- Lo que falla antes de gastar el número ---

    @Test
    void unaFacturaConDatosInvalidosSeRechazaYNoGastaNumeroNiGuardaNada() {
        sunatAcepta();
        FacturaRequest invalida = factura("pedido-malo");
        invalida.getItems().get(0).setPrecioUnitario(BigDecimal.ZERO);

        assertThatThrownBy(() -> servicio.emitir(invalida)).isInstanceOf(SolicitudInvalidaException.class);

        assertThat(comprobantes.count()).isZero();
        verify(cliente, never()).sendBill(anyString(), any());
        assertThat(servicio.emitir(factura("pedido-bueno")).factura().getNumero()).isEqualTo("F001-1");
    }

    @Test
    void sinCertificadoLaFacturaNoGastaNumeroNiSeGuardaNiSeEnvia() {
        sunatAcepta();
        propiedades.getCertificado().setRuta(null);
        propiedades.getCertificado().setClave(null);

        assertThatThrownBy(() -> servicio.emitir(factura("pedido-1"))).isInstanceOf(CertificadoInvalidoException.class);

        assertThat(comprobantes.count()).isZero();
        verify(cliente, never()).sendBill(anyString(), any());

        restaurar();
        assertThat(servicio.emitir(factura("pedido-1")).factura().getNumero()).isEqualTo("F001-1");
    }

    // --- Consultas ---

    @Test
    void obtenerDevuelveLaFacturaGuardada() {
        sunatAcepta();
        FacturaResponse creada = servicio.emitir(factura("pedido-1")).factura();

        FacturaResponse leida = servicio.obtener(creada.getId());

        assertThat(leida).isEqualTo(creada);
    }

    @Test
    void unIdQueNoExisteDaNoEncontrado() {
        assertThatThrownBy(() -> servicio.obtener(999_999L)).isInstanceOf(ComprobanteNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.obtenerXml(999_999L)).isInstanceOf(ComprobanteNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.enviar(999_999L)).isInstanceOf(ComprobanteNoEncontradoException.class);
    }

    @Test
    void elXmlGuardadoEsElQueVerificaConSuFirma() {
        sunatAcepta();
        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();

        byte[] xml = servicio.obtenerXml(r.getId());

        assertThat(verifica(xml)).isTrue();
        assertThat(new String(xml, StandardCharsets.ISO_8859_1)).contains("<cbc:ID>F001-1</cbc:ID>");
    }

    @Test
    void laFechaDeEmisionEsLaDeLimaYQuedaGuardada() {
        sunatAcepta();
        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();
        assertThat(r.getFechaEmision()).isNotNull();
        assertThat(r.getFechaEmision()).isBefore(java.time.LocalDateTime.now().plusDays(1));
    }

    @Test
    void repetirLaPeticionNoAgregaIntentosNuevos() {
        sunatAcepta();
        FacturaResponse r = servicio.emitir(factura("pedido-1")).factura();
        servicio.emitir(factura("pedido-1"));
        assertThat(intentos.findByComprobanteIdOrderByIdAsc(r.getId())).hasSize(1);
        AtomicInteger cuenta = new AtomicInteger();
        intentos.findAll().forEach(i -> cuenta.incrementAndGet());
        assertThat(cuenta.get()).isEqualTo(1);
    }
}
