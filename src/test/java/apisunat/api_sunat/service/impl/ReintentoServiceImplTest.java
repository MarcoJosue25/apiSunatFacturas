package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.ClienteRequest;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.EnvioIntento;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.ComprobanteService;
import apisunat.api_sunat.service.ReintentoService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatusCdr;
import apisunat.api_sunat.util.Fixtures;
import apisunat.api_sunat.util.RelojMutable;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// Los reintentos con la base de datos MySQL real, un reloj que se mueve a mano y la SUNAT simulada
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class ReintentoServiceImplTest {

    // 2026-10-28 10:00 en Lima (UTC-5)
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
    private ComprobanteService comprobanteService;
    @Autowired
    private ReintentoService reintentoService;
    @Autowired
    private ComprobanteRepository comprobantes;
    @Autowired
    private EnvioIntentoRepository intentos;
    @Autowired
    private SerieRepository series;
    @Autowired
    private SunatProperties propiedades;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private BillServiceClient cliente;

    @BeforeEach
    void preparar() {
        intentos.deleteAll();
        comprobantes.deleteAll();
        series.deleteAll();
        reloj.fijar(INICIO);
        reset(cliente);
        propiedades.getConsultaCdr().setHabilitada(false);
        propiedades.getReintentos().setLote(20);
        propiedades.setPlazoEnvioDias(1);
    }

    @AfterEach
    void restaurar() {
        propiedades.getConsultaCdr().setHabilitada(false);
        propiedades.getReintentos().setLote(20);
        propiedades.setPlazoEnvioDias(1);
    }

    // --- Datos ---

    private static FacturaRequest factura(String idExterno) {
        return FacturaRequest.builder().idExterno(idExterno)
                .cliente(ClienteRequest.builder().ruc("20123456786").razonSocial("CLIENTE SAC").build())
                .items(List.of(ItemRequest.builder().descripcion("Polo").cantidad(new BigDecimal("2")).precioUnitario(new BigDecimal("35.00")).build()))
                .build();
    }

    private static byte[] constancia(String nombreZip, String fixture, String codigo) {
        String numero = nombreZip.substring(nombreZip.indexOf("-01-") + 4, nombreZip.length() - 4);
        String xml = Fixtures.texto(fixture).replace("FA01-981", numero).replace("FT01-982", numero).replace(">2047<", ">" + codigo + "<");
        return ZipUtil.comprimir("R-" + nombreZip.replace(".zip", ".xml"), xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    private void sunatCaida() {
        doThrow(new SunatNoDisponibleException("sin conexión", false, null)).when(cliente).sendBill(anyString(), any());
    }

    private void sunatAcepta() {
        doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0")).when(cliente).sendBill(anyString(), any());
    }

    private Comprobante estado(FacturaResponse r) {
        return comprobantes.findById(r.getId()).orElseThrow();
    }

    private FacturaResponse emitirConSunatCaida(String id) {
        sunatCaida();
        FacturaResponse r = comprobanteService.emitir(factura(id)).factura();
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        return r;
    }

    private List<String> resultados(FacturaResponse r) {
        return intentos.findByComprobanteIdOrderByIdAsc(r.getId()).stream().map(EnvioIntento::getResultado).toList();
    }

    // --- Falla, falla, funciona ---

    @Test
    void fallaFallaFuncionaCadaVezEnElMomentoQueToca() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        verify(cliente, times(1)).sendBill(anyString(), any());

        // Todavía no pasó el minuto de espera: no hace nada
        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(reintentoService.procesarPendientes()).isZero();
        verify(cliente, times(1)).sendBill(anyString(), any());

        // Pasó el minuto: reintenta, falla otra vez y ahora debe esperar 2 minutos
        reloj.avanzar(Duration.ofSeconds(31));
        assertThat(reintentoService.procesarPendientes()).isEqualTo(1);
        verify(cliente, times(2)).sendBill(anyString(), any());
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(estado(r).getIntentosFallidos()).isEqualTo(2);

        // A los 90 segundos todavía no toca (faltan 30 s de los 2 minutos)
        reloj.avanzar(Duration.ofSeconds(90));
        assertThat(reintentoService.procesarPendientes()).isZero();

        // Pasados los 2 minutos la SUNAT ya funciona
        reloj.avanzar(Duration.ofSeconds(31));
        sunatAcepta();
        assertThat(reintentoService.procesarPendientes()).isEqualTo(1);

        Comprobante c = estado(r);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(c.getCdrZip()).isNotEmpty();
        assertThat(c.getProximoIntento()).isNull();
        assertThat(resultados(r)).containsExactly("SIN_RESPUESTA", "SIN_RESPUESTA", "ACEPTADO");
        verify(cliente, times(3)).sendBill(anyString(), any());
    }

    @Test
    void despuesDeAceptadaYaNoSeVuelveATocar() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        reloj.avanzar(Duration.ofMinutes(2));
        sunatAcepta();
        reintentoService.procesarPendientes();

        reloj.avanzar(Duration.ofHours(5));
        assertThat(reintentoService.procesarPendientes()).isZero();
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    @Test
    void elMismoXmlFirmadoSeReenviaSinCambios() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        byte[] antes = estado(r).getXmlFirmado();

        reloj.avanzar(Duration.ofMinutes(2));
        reintentoService.procesarPendientes();

        assertThat(estado(r).getXmlFirmado()).isEqualTo(antes);
    }

    // --- Qué no se reintenta ---

    @ParameterizedTest
    @CsvSource({"3270,RECHAZADO", "0306,ERROR_DE_DATOS", "0111,ERROR_DE_CONFIGURACION", "1033,REVISAR", "0200,REVISAR"})
    void loQueTieneUnaRespuestaDefinitivaNuncaSeReintenta(String codigo, EstadoComprobante esperado) {
        if (codigo.equals("3270")) {
            doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/rechazado.xml", "3270")).when(cliente).sendBill(anyString(), any());
        } else {
            doThrow(new SunatFaultException("S:Client." + codigo, "x", codigo)).when(cliente).sendBill(anyString(), any());
        }
        FacturaResponse r = comprobanteService.emitir(factura("pedido-1")).factura();
        assertThat(r.getEstado()).isEqualTo(esperado);
        verify(cliente, times(1)).sendBill(anyString(), any());

        reloj.avanzar(Duration.ofHours(3));
        assertThat(reintentoService.procesarPendientes()).isZero();

        verify(cliente, times(1)).sendBill(anyString(), any());
        assertThat(estado(r).getEstado()).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"0100", "0109"})
    void losDosCodigosTransitoriosDeLaSunatSiSeReintentan(String codigo) {
        doThrow(new SunatFaultException("S:Server." + codigo, "intente nuevamente", codigo)).when(cliente).sendBill(anyString(), any());
        FacturaResponse r = comprobanteService.emitir(factura("pedido-1")).factura();
        assertThat(r.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(estado(r).getProximoIntento()).isNotNull();

        reloj.avanzar(Duration.ofMinutes(2));
        sunatAcepta();
        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    // --- Vencimiento ---

    @Test
    void pasaAVencidoAlCumplirseElPlazoYNoAntes() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");   // emitida el 28 a las 10:00 Lima; plazo de 1 día

        // El 29 a las 23:59:59 todavía se puede enviar
        reloj.fijar(Instant.parse("2026-10-30T04:59:59Z"));
        reintentoService.procesarPendientes();
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        verify(cliente, times(2)).sendBill(anyString(), any());

        // Ya pasó el 29: a las 00:03 del 30 (05:03 UTC) toca el siguiente intento, pero el plazo terminó a las 00:00. (El límite
        // exacto de las 00:00:00 está probado en PlazoEnvioTest.)
        reloj.fijar(Instant.parse("2026-10-30T05:03:00Z"));
        reintentoService.procesarPendientes();

        Comprobante c = estado(r);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.VENCIDO);
        assertThat(c.getProximoIntento()).isNull();
        verify(cliente, times(2)).sendBill(anyString(), any());   // no se envió otra vez
        assertThat(resultados(r)).containsExactly("SIN_RESPUESTA", "SIN_RESPUESTA", "VENCIDO");
    }

    @Test
    void unComprobanteVencidoYaNoVuelveACandidatoNiSeEnvia() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        reloj.avanzar(Duration.ofDays(3));
        reintentoService.procesarPendientes();
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.VENCIDO);

        sunatAcepta();
        reloj.avanzar(Duration.ofDays(1));
        assertThat(reintentoService.procesarPendientes()).isZero();
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.VENCIDO);
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void elPlazoConfigurableAlargaElTiempoParaEnviar() {
        propiedades.setPlazoEnvioDias(3);
        FacturaResponse r = emitirConSunatCaida("pedido-1");

        reloj.fijar(Instant.parse("2026-10-31T04:59:59Z"));   // el 30 a las 23:59:59 Lima
        reintentoService.procesarPendientes();
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);

        reloj.fijar(Instant.parse("2026-11-02T05:00:00Z"));   // el 2 de noviembre 00:00 Lima: el plazo de 3 días terminó el 31
        reintentoService.procesarPendientes();
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.VENCIDO);
    }

    @Test
    void unaFacturaPendienteQueNuncaSeEnvioTambienPuedeVencer() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        jdbc.update("UPDATE comprobantes SET estado = 'PENDIENTE' WHERE id = ?", r.getId());

        reloj.avanzar(Duration.ofDays(3));
        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.VENCIDO);
    }

    // --- Espera entre intentos ---

    @Test
    void laEsperaEntreIntentosEs1_2_5_15_30_60_y_60Minutos() {
        propiedades.setPlazoEnvioDias(30);
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        List<Long> minutos = new ArrayList<>();
        minutos.add(minutosHastaElProximo(r));
        for (int i = 0; i < 6; i++) {
            reloj.avanzar(Duration.ofMinutes(minutosHastaElProximo(r)).plusSeconds(1));
            assertThat(reintentoService.procesarPendientes()).isEqualTo(1);
            minutos.add(minutosHastaElProximo(r));
        }
        assertThat(minutos).containsExactly(1L, 2L, 5L, 15L, 30L, 60L, 60L);
    }

    private long minutosHastaElProximo(FacturaResponse r) {
        Comprobante c = estado(r);
        LocalDateTime ahora = LocalDateTime.ofInstant(reloj.instant(), java.time.ZoneId.of("America/Lima"));
        return Math.round(Duration.between(ahora, c.getProximoIntento()).toSeconds() / 60.0);
    }

    // --- Lote y orden ---

    @Test
    void cadaPasadaTomaComoMuchoUnLoteYLosMasAtrasadosPrimero() {
        sunatCaida();
        List<FacturaResponse> todas = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            todas.add(comprobanteService.emitir(factura("pedido-" + i)).factura());
            reloj.avanzar(Duration.ofSeconds(10));
        }
        propiedades.getReintentos().setLote(2);
        reloj.avanzar(Duration.ofMinutes(10));
        reset(cliente);
        sunatAcepta();

        assertThat(reintentoService.procesarPendientes()).isEqualTo(2);
        assertThat(estado(todas.get(0)).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(estado(todas.get(1)).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(estado(todas.get(2)).getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);

        assertThat(reintentoService.procesarPendientes()).isEqualTo(2);
        assertThat(reintentoService.procesarPendientes()).isEqualTo(1);
        assertThat(reintentoService.procesarPendientes()).isZero();
        assertThat(comprobantes.findAll()).allMatch(c -> c.getEstado() == EstadoComprobante.ACEPTADO);
    }

    @Test
    void siUnoFallaConUnErrorInesperadoLosDemasSiSeProcesan() {
        sunatCaida();
        FacturaResponse a = comprobanteService.emitir(factura("a")).factura();
        FacturaResponse b = comprobanteService.emitir(factura("b")).factura();
        reloj.avanzar(Duration.ofMinutes(2));
        reset(cliente);
        doThrow(new IllegalStateException("bug")).doAnswer(i -> constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0"))
                .when(cliente).sendBill(anyString(), any());

        reintentoService.procesarPendientes();

        assertThat(List.of(estado(a).getEstado(), estado(b).getEstado())).containsExactlyInAnyOrder(EstadoComprobante.ERROR_TEMPORAL, EstadoComprobante.ACEPTADO);
    }

    // --- Dos procesos a la vez ---

    @Test
    void dosHilosQueTomanElMismoPendienteLoEnvianUnaSolaVez() throws Exception {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        reloj.avanzar(Duration.ofMinutes(2));
        reset(cliente);
        doAnswer(i -> {
            Thread.sleep(300);   // el envío tarda: el otro hilo llega mientras tanto
            return constancia(i.getArgument(0), "sunat/cdr/aceptado.xml", "0");
        }).when(cliente).sendBill(anyString(), any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<?>> trabajos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    reintentoService.procesar(r.getId());
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> t : trabajos) {
                t.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        verify(cliente, times(1)).sendBill(anyString(), any());
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    @Test
    void ocho_hilosProcesandoLasMismasPasadasNoDuplicanNingunEnvio() throws Exception {
        sunatCaida();
        List<FacturaResponse> todas = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            todas.add(comprobanteService.emitir(factura("pedido-" + i)).factura());
        }
        reloj.avanzar(Duration.ofMinutes(2));
        reset(cliente);
        sunatAcepta();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<?>> trabajos = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                trabajos.add(pool.submit(() -> {
                    salida.await();
                    reintentoService.procesarPendientes();
                    return null;
                }));
            }
            salida.countDown();
            for (Future<?> t : trabajos) {
                t.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        verify(cliente, times(6)).sendBill(anyString(), any());
        assertThat(comprobantes.findAll()).hasSize(6).allMatch(c -> c.getEstado() == EstadoComprobante.ACEPTADO);
        for (FacturaResponse r : todas) {
            assertThat(resultados(r)).containsExactly("SIN_RESPUESTA", "ACEPTADO");
        }
    }

    // --- Sobrevivir a un reinicio ---

    @Test
    void unComprobanteQueQuedoEnviandoPorUnaCaidaSeRecuperaYSeReenvia() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        // Simula la caída: quedó "ENVIANDO" desde hace una hora
        jdbc.update("UPDATE comprobantes SET estado = 'ENVIANDO', actualizado_en = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 28, 9, 0)), r.getId());
        reloj.avanzar(Duration.ofMinutes(1));
        reset(cliente);
        sunatAcepta();

        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(resultados(r)).containsExactly("SIN_RESPUESTA", "SIN_RESPUESTA", "ACEPTADO");
    }

    @Test
    void unComprobanteQueSeEnviaAhoraMismoNoSeRecuperaNiSeToca() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        jdbc.update("UPDATE comprobantes SET estado = 'ENVIANDO', actualizado_en = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 10, 28, 9, 55)), r.getId());   // hace 5 minutos; el máximo es 10
        reset(cliente);

        assertThat(reintentoService.procesarPendientes()).isZero();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ENVIANDO);
        verify(cliente, never()).sendBill(anyString(), any());
    }

    // --- Consultar la constancia antes de reenviar ---

    private void consultaResponde(String codigo, String mensaje, byte[] constancia) {
        doAnswer(i -> RespuestaStatusCdr.builder().statusCode(codigo).statusMessage(mensaje).constancia(constancia).build())
                .when(cliente).getStatusCdr(anyString(), anyString(), anyString(), anyString());
    }

    private FacturaResponse fallidaConConsultaHabilitada() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        propiedades.getConsultaCdr().setHabilitada(true);
        reloj.avanzar(Duration.ofMinutes(2));
        return r;
    }

    @Test
    void siLaSunatYaTeniaLaFacturaSeGuardaSuConstanciaYNoSeReenvia() {
        FacturaResponse r = fallidaConConsultaHabilitada();
        consultaResponde("0001", "El comprobante existe y está aceptado.", constancia("20100066603-01-F001-1.zip", "sunat/cdr/aceptado.xml", "0"));

        reintentoService.procesarPendientes();

        Comprobante c = estado(r);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(c.getCdrZip()).isNotEmpty();
        assertThat(c.getCodigoSunat()).isEqualTo("0");
        verify(cliente, times(1)).sendBill(anyString(), any());   // solo el primer intento
        verify(cliente).getStatusCdr("20100066603", "01", "F001", "1");
        assertThat(resultados(r)).containsExactly("SIN_RESPUESTA", "ACEPTADO");
    }

    @Test
    void siLaSunatYaLaTeniaRechazadaSeGuardaComoRechazada() {
        FacturaResponse r = fallidaConConsultaHabilitada();
        consultaResponde("0002", "El comprobante existe pero está rechazado.", constancia("20100066603-01-F001-1.zip", "sunat/cdr/rechazado.xml", "3270"));

        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.RECHAZADO);
        assertThat(estado(r).getCodigoSunat()).isEqualTo("3270");
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void siLaSunatDiceQueExisteSinMandarLaConstanciaSeGuardaElEstado() {
        FacturaResponse r = fallidaConConsultaHabilitada();
        consultaResponde("0001", "El comprobante existe y está aceptado.", null);

        reintentoService.procesarPendientes();

        Comprobante c = estado(r);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(c.getCdrZip()).isNull();
        assertThat(c.getDescripcionSunat()).isEqualTo("El comprobante existe y está aceptado.");
    }

    @Test
    void siLaSunatDiceQueNoExisteSeEnviaDeVerdad() {
        FacturaResponse r = fallidaConConsultaHabilitada();
        consultaResponde("0011", "El comprobante de pago electrónico no existe.", null);
        reset(cliente);
        consultaResponde("0011", "no existe", null);
        sunatAcepta();

        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @ParameterizedTest
    @CsvSource({"0003", "0008", "0012", "0099"})
    void conCualquierOtroCodigoDeLaConsultaNoSeReenviaYSeDejaParaRevisar(String codigo) {
        FacturaResponse r = fallidaConConsultaHabilitada();
        consultaResponde(codigo, "mensaje " + codigo, null);

        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.REVISAR);
        assertThat(estado(r).getCodigoSunat()).isEqualTo(codigo);
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void siLaConsultaNoRespondeSeEsperaYNoSeReenviaALoLoco() {
        FacturaResponse r = fallidaConConsultaHabilitada();
        doThrow(new SunatNoDisponibleException("sin conexión", false, null)).when(cliente).getStatusCdr(anyString(), anyString(), anyString(), anyString());
        int fallosAntes = estado(r).getIntentosFallidos();

        reintentoService.procesarPendientes();

        Comprobante c = estado(r);
        assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        assertThat(c.getIntentosFallidos()).isEqualTo(fallosAntes);
        assertThat(c.getProximoIntento()).isAfter(LocalDateTime.ofInstant(reloj.instant(), java.time.ZoneId.of("America/Lima")));
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void siLaConsultaDaUnErrorSoapTampocoSeReenvia() {
        FacturaResponse r = fallidaConConsultaHabilitada();
        doThrow(new SunatFaultException("S:Client.0111", "sin perfil", "0111")).when(cliente).getStatusCdr(anyString(), anyString(), anyString(), anyString());

        reintentoService.procesarPendientes();

        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ERROR_TEMPORAL);
        verify(cliente, times(1)).sendBill(anyString(), any());
    }

    @Test
    void conLaConsultaApagadaNuncaSeConsulta() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        reloj.avanzar(Duration.ofMinutes(2));
        sunatAcepta();

        reintentoService.procesarPendientes();

        verify(cliente, never()).getStatusCdr(anyString(), anyString(), anyString(), anyString());
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    @Test
    void unComprobanteQueNuncaSeEnvioNoNecesitaConsultaPrevia() {
        FacturaResponse r = emitirConSunatCaida("pedido-1");
        jdbc.update("UPDATE comprobantes SET estado = 'PENDIENTE', intentos_fallidos = 0 WHERE id = ?", r.getId());
        propiedades.getConsultaCdr().setHabilitada(true);
        reloj.avanzar(Duration.ofMinutes(2));
        reset(cliente);
        sunatAcepta();

        reintentoService.procesarPendientes();

        verify(cliente, never()).getStatusCdr(anyString(), anyString(), anyString(), anyString());
        assertThat(estado(r).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }
}
