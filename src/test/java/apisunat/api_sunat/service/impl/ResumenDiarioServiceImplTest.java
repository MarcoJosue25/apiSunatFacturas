package apisunat.api_sunat.service.impl;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.ClienteBoletaRequest;
import apisunat.api_sunat.dto.ItemRequest;
import apisunat.api_sunat.dto.ResumenResponse;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.Comprobante;
import apisunat.api_sunat.model.ResumenDiario;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.model.enums.EstadoResumen;
import apisunat.api_sunat.repository.ComprobanteRepository;
import apisunat.api_sunat.repository.EnvioIntentoRepository;
import apisunat.api_sunat.repository.ResumenDiarioRepository;
import apisunat.api_sunat.repository.SerieRepository;
import apisunat.api_sunat.service.BoletaService;
import apisunat.api_sunat.service.ResumenDiarioService;
import apisunat.api_sunat.sunat.soap.BillServiceClient;
import apisunat.api_sunat.sunat.soap.RespuestaStatus;
import apisunat.api_sunat.sunat.xml.XsdValidator;
import apisunat.api_sunat.util.RelojMutable;
import apisunat.api_sunat.util.ZipUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static apisunat.api_sunat.util.FirmaTestUtil.verifica;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// El resumen diario con la base MySQL real, un reloj que se mueve a mano y la SUNAT simulada
@SpringBootTest(properties = {"sunat.certificado.ruta=src/test/resources/certificados/valido.p12", "sunat.certificado.clave=prueba123"})
class ResumenDiarioServiceImplTest {

    // 2026-10-28 10:00 en Lima (UTC-5)
    private static final Instant INICIO = Instant.parse("2026-10-28T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);

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
    private ResumenDiarioService servicio;
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
    @Autowired
    private SunatProperties propiedades;

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

    // --- Ayudas ---

    private Long boleta(String id, String precio) {
        return boleta(id, precio, ClienteBoletaRequest.builder().tipoDocumento("1").numeroDocumento("46237547").nombre("Luana Pazos").build());
    }

    private Long boleta(String id, String precio, ClienteBoletaRequest cliente) {
        ItemRequest item = ItemRequest.builder().descripcion("Polo").cantidad(BigDecimal.ONE).precioUnitario(new BigDecimal(precio)).build();
        return boletas.emitir(BoletaRequest.builder().idExterno(id).cliente(cliente).items(List.of(item)).build()).boleta().getId();
    }

    private static String cdrAceptado(String descripcion, String... notas) {
        StringBuilder n = new StringBuilder();
        for (String nota : notas) {
            n.append("<cbc:Note>").append(nota).append("</cbc:Note>");
        }
        return base64(cdr("0", descripcion, n.toString()));
    }

    private static String cdrRechazado(String codigo, String descripcion) {
        return base64(cdr(codigo, descripcion, ""));
    }

    private static byte[] cdr(String codigo, String descripcion, String notas) {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><ar:ApplicationResponse"
                + " xmlns:ar=\"urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">" + notas
                + "<cac:DocumentResponse><cac:Response><cbc:ReferenceID>RC-20261028-1</cbc:ReferenceID><cbc:ResponseCode>" + codigo
                + "</cbc:ResponseCode><cbc:Description>" + descripcion + "</cbc:Description></cac:Response></cac:DocumentResponse>"
                + "</ar:ApplicationResponse>";
        return ZipUtil.comprimir("R-20123456786-RC-20261028-1.xml", xml.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String base64(byte[] zip) {
        return Base64.getEncoder().encodeToString(zip);
    }

    private Long resumenEnviado() {
        boleta("b1", "10.00");
        boleta("b2", "20.00");
        doReturn("202610280001").when(cliente).sendSummary(anyString(), any());
        Long id = servicio.generarPendientes().get(0);
        servicio.enviar(id);
        return id;
    }

    // --- generar ---

    @Test
    void sinBoletasNoSeGeneraNadaNiSeHablaConLaSunat() {
        assertThat(servicio.generarPendientes()).isEmpty();
        assertThat(servicio.generarYEnviar()).isEmpty();
        assertThat(resumenes.count()).isZero();
        verifyNoInteractions(cliente);
    }

    @Test
    void lasBoletasDelDiaSeJuntanEnUnResumenFirmadoYValidoConElNombreDelDia() {
        Long b1 = boleta("b1", "10.00");
        Long b2 = boleta("b2", "20.00");

        List<Long> creados = servicio.generarPendientes();

        assertThat(creados).hasSize(1);
        ResumenDiario r = resumenes.findById(creados.get(0)).orElseThrow();
        assertThat(r.getNombre()).isEqualTo("RC-20261028-1");
        assertThat(r.getFechaReferencia()).isEqualTo(HOY);
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.GENERADO);
        assertThat(verifica(r.getXmlFirmado())).isTrue();
        assertThat(new XsdValidator().validarResumen(r.getXmlFirmado())).isEmpty();
        String xml = new String(r.getXmlFirmado(), StandardCharsets.ISO_8859_1);
        assertThat(xml).contains("<cbc:ID>RC-20261028-1</cbc:ID>").contains("B001-1").contains("B001-2");
        for (Long id : List.of(b1, b2)) {
            Comprobante c = comprobantes.findById(id).orElseThrow();
            assertThat(c.getEstado()).isEqualTo(EstadoComprobante.EN_RESUMEN);
            assertThat(c.getResumenId()).isEqualTo(r.getId());
        }
        verifyNoInteractions(cliente);
    }

    @Test
    void unaBoletaAnonimaVaEnElResumenConGuionComoDocumento() {
        boleta("b1", "10.00", null);

        Long id = servicio.generarPendientes().get(0);

        String xml = new String(resumenes.findById(id).orElseThrow().getXmlFirmado(), StandardCharsets.ISO_8859_1);
        assertThat(xml).contains("<cbc:CustomerAssignedAccountID>-</cbc:CustomerAssignedAccountID>").contains("<cbc:AdditionalAccountID>-</cbc:AdditionalAccountID>");
    }

    @Test
    void generarDosVecesNoRepiteLasBoletas() {
        boleta("b1", "10.00");
        assertThat(servicio.generarPendientes()).hasSize(1);

        assertThat(servicio.generarPendientes()).isEmpty();
        assertThat(resumenes.count()).isEqualTo(1);
    }

    @Test
    void unaBoletaNuevaDelMismoDiaVaEnElSiguienteResumenDelDia() {
        boleta("b1", "10.00");
        servicio.generarPendientes();
        boleta("b2", "10.00");

        Long id = servicio.generarPendientes().get(0);

        assertThat(resumenes.findById(id).orElseThrow().getNombre()).isEqualTo("RC-20261028-2");
    }

    @Test
    void lasBoletasDeDiasDistintosNoSeMezclan() {
        Long ayer = boleta("b1", "10.00");
        reloj.avanzar(Duration.ofDays(1));
        Long hoy = boleta("b2", "10.00");

        List<Long> creados = servicio.generarPendientes();

        assertThat(creados).hasSize(2);
        Comprobante a = comprobantes.findById(ayer).orElseThrow();
        Comprobante h = comprobantes.findById(hoy).orElseThrow();
        assertThat(a.getResumenId()).isNotEqualTo(h.getResumenId());
        assertThat(resumenes.findById(a.getResumenId()).orElseThrow().getNombre()).isEqualTo("RC-20261028-1");
        assertThat(resumenes.findById(h.getResumenId()).orElseThrow().getNombre()).isEqualTo("RC-20261029-1");
    }

    @Test
    void quinientasBoletasCabenEnUnSoloResumen() {
        for (int i = 1; i <= 500; i++) {
            boleta("b" + i, "1.00");
        }

        assertThat(servicio.generarPendientes()).hasSize(1);
        assertThat(comprobantes.countByResumenId(resumenes.findAll().get(0).getId())).isEqualTo(500);
    }

    @Test
    void quinientasUnaBoletasSePartenEnQuinientasYUna() {
        for (int i = 1; i <= 501; i++) {
            boleta("b" + i, "1.00");
        }

        List<Long> creados = servicio.generarPendientes();

        assertThat(creados).hasSize(2);
        assertThat(comprobantes.countByResumenId(creados.get(0))).isEqualTo(500);
        assertThat(comprobantes.countByResumenId(creados.get(1))).isEqualTo(1);
        assertThat(resumenes.findById(creados.get(0)).orElseThrow().getNombre()).isEqualTo("RC-20261028-1");
        assertThat(resumenes.findById(creados.get(1)).orElseThrow().getNombre()).isEqualTo("RC-20261028-2");
        // La que quedó con una sola boleta es la última, no una del medio
        assertThat(comprobantes.findByResumenIdOrderByCorrelativo(creados.get(1)).get(0).getCorrelativo()).isEqualTo(501);
    }

    // --- enviar ---

    @Test
    void alEnviarElResumenSeMandaEnUnZipConElNombreDeLaSunatYQuedaEnviadoConSuTicket() {
        Long id = resumenEnviado();

        ArgumentCaptor<String> nombre = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> zip = ArgumentCaptor.forClass(byte[].class);
        verify(cliente).sendSummary(nombre.capture(), zip.capture());
        assertThat(nombre.getValue()).isEqualTo(propiedades.getRuc() + "-RC-20261028-1.zip");
        ZipUtil.Entrada entrada = ZipUtil.leerXmlUnico(zip.getValue());
        assertThat(entrada.nombre()).isEqualTo(propiedades.getRuc() + "-RC-20261028-1.xml");
        assertThat(entrada.contenido()).isEqualTo(resumenes.findById(id).orElseThrow().getXmlFirmado());

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ENVIADO);
        assertThat(r.getTicket()).isEqualTo("202610280001");
        assertThat(r.getProximoIntento()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 10, 1));
    }

    @Test
    void sinConexionConLaSunatElResumenEsperaYSeReintentaConBackoff() {
        boleta("b1", "10.00");
        Long id = servicio.generarPendientes().get(0);
        doThrow(new SunatNoDisponibleException("sin red", false, null)).when(cliente).sendSummary(anyString(), any());

        servicio.enviar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ERROR_TEMPORAL);
        assertThat(r.getIntentosFallidos()).isEqualTo(1);
        assertThat(r.getProximoIntento()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 10, 1));

        doReturn("T1").when(cliente).sendSummary(anyString(), any());
        servicio.enviar(id);
        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ENVIADO);
    }

    @Test
    void unErrorPasajeroDeLaSunatAlEnviarTambienEspera() {
        boleta("b1", "10.00");
        Long id = servicio.generarPendientes().get(0);
        doThrow(new SunatFaultException("soap-env:Server", "Error interno", "0109")).when(cliente).sendSummary(anyString(), any());

        servicio.enviar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ERROR_TEMPORAL);
    }

    @Test
    void unErrorDeDatosAlEnviarRechazaElResumenYSusBoletasSiguenEnEl() {
        Long b = boleta("b1", "10.00");
        Long id = servicio.generarPendientes().get(0);
        doThrow(new SunatFaultException("soap-env:Client", "El archivo ZIP esta corrupto", "0306")).when(cliente).sendSummary(anyString(), any());

        servicio.enviar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.RECHAZADO);
        assertThat(r.getCodigoSunat()).isEqualTo("0306");
        assertThat(comprobantes.findById(b).orElseThrow().getEstado()).isEqualTo(EstadoComprobante.EN_RESUMEN);
    }

    @Test
    void unErrorDeConfiguracionAlEnviarDejaElResumenParaRevisar() {
        boleta("b1", "10.00");
        Long id = servicio.generarPendientes().get(0);
        doThrow(new SunatFaultException("soap-env:Client", "Usuario o clave incorrecta", "0111")).when(cliente).sendSummary(anyString(), any());

        servicio.enviar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.REVISAR);
    }

    @Test
    void unResumenYaEnviadoNoSeEnviaOtraVez() {
        Long id = resumenEnviado();

        assertThatThrownBy(() -> servicio.enviar(id)).isInstanceOf(IllegalStateException.class);
        verify(cliente, times(1)).sendSummary(anyString(), any());
    }

    // --- consultar ---

    @Test
    void mientrasLaSunatSigueProcesandoElResumenSigueEnviadoYSeConsultaMasTarde() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("98").content("En proceso").build()).when(cliente).getStatus("202610280001");

        reloj.avanzar(Duration.ofMinutes(1));
        servicio.consultar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ENVIADO);
        assertThat(r.getIntentosFallidos()).isEqualTo(1);
        assertThat(r.getProximoIntento()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 10, 2));

        // La siguiente espera es más larga (2 minutos)
        reloj.avanzar(Duration.ofMinutes(1));
        servicio.consultar(id);
        assertThat(resumenes.findById(id).orElseThrow().getProximoIntento()).isEqualTo(java.time.LocalDateTime.of(2026, 10, 28, 10, 4));
    }

    @Test
    void cuandoLaSunatAceptaElResumenTodasSusBoletasQuedanAceptadas() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("0").content(cdrAceptado("El Resumen diario RC-20261028-1 ha sido aceptado")).build())
                .when(cliente).getStatus("202610280001");

        servicio.consultar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ACEPTADO);
        assertThat(r.getCodigoSunat()).isEqualTo("0");
        assertThat(r.getCdrZip()).isNotEmpty();
        assertThat(r.getProximoIntento()).isNull();
        for (Comprobante c : comprobantes.findByResumenIdOrderByCorrelativo(id)) {
            assertThat(c.getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
            assertThat(c.getCodigoSunat()).isEqualTo("0");
            assertThat(c.getObservaciones()).isNull();
        }
    }

    @Test
    void elCodigoNoventaYNueveTambienTraeLaConstancia() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("99").content(cdrAceptado("aceptado")).build()).when(cliente).getStatus(anyString());

        servicio.consultar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ACEPTADO);
    }

    @Test
    void unaObservacionQueNombraUnaBoletaSeAnotaSoloEnEsaBoleta() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("0")
                .content(cdrAceptado("aceptado", "4000 - La boleta B001-2 tiene un dato dudoso", "4443 - Se envió fuera de plazo")).build())
                .when(cliente).getStatus(anyString());

        servicio.consultar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ACEPTADO_CON_OBSERVACIONES);
        assertThat(r.getDescripcionSunat()).contains("4443 - Se envió fuera de plazo").doesNotContain("B001-2 tiene");
        List<Comprobante> cs = comprobantes.findByResumenIdOrderByCorrelativo(id);
        assertThat(cs.get(0).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(cs.get(0).getObservaciones()).isNull();
        assertThat(cs.get(1).getEstado()).isEqualTo(EstadoComprobante.ACEPTADO_CON_OBSERVACIONES);
        assertThat(cs.get(1).getObservaciones()).isEqualTo("4000 - La boleta B001-2 tiene un dato dudoso");
    }

    @Test
    void elNumeroB001UnoNoSeConfundeConB001Diez() {
        assertThat(ResumenDiarioServiceImpl.menciona("la boleta B001-10 falló", "B001-1")).isFalse();
        assertThat(ResumenDiarioServiceImpl.menciona("la boleta B001-1 falló", "B001-1")).isTrue();
        assertThat(ResumenDiarioServiceImpl.menciona("B001-1, B001-2", "B001-2")).isTrue();
        assertThat(ResumenDiarioServiceImpl.menciona("XB001-1", "B001-1")).isFalse();
        assertThat(ResumenDiarioServiceImpl.menciona(null, "B001-1")).isFalse();
    }

    @Test
    void cuandoLaSunatRechazaElResumenQuedaRechazadoYLasBoletasSiguenEnEl() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("99").content(cdrRechazado("2399", "La boleta B001-1 ya fue informada")).build())
                .when(cliente).getStatus(anyString());

        servicio.consultar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.RECHAZADO);
        assertThat(r.getCodigoSunat()).isEqualTo("2399");
        List<Comprobante> cs = comprobantes.findByResumenIdOrderByCorrelativo(id);
        assertThat(cs).allMatch(c -> c.getEstado() == EstadoComprobante.EN_RESUMEN);
        // La que la SUNAT nombra queda con la explicación; la otra no
        assertThat(cs.get(0).getCodigoSunat()).isEqualTo("2399");
        assertThat(cs.get(0).getDescripcionSunat()).contains("B001-1");
        assertThat(cs.get(1).getCodigoSunat()).isNull();
    }

    @Test
    void unResumenRechazadoSeReenviaConElMismoNombreYElMismoXml() {
        Long id = resumenEnviado();
        byte[] xml = resumenes.findById(id).orElseThrow().getXmlFirmado();
        doReturn(RespuestaStatus.builder().statusCode("99").content(cdrRechazado("2399", "mal")).build()).when(cliente).getStatus(anyString());
        servicio.consultar(id);
        doReturn("T2").when(cliente).sendSummary(anyString(), any());

        ResumenResponse r = servicio.reenviar(id);

        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ENVIADO);
        assertThat(r.getTicket()).isEqualTo("T2");
        assertThat(r.getNombre()).isEqualTo("RC-20261028-1");
        assertThat(resumenes.findById(id).orElseThrow().getXmlFirmado()).isEqualTo(xml);
        verify(cliente, times(2)).sendSummary(eq(propiedades.getRuc() + "-RC-20261028-1.zip"), any());
    }

    @Test
    void unResumenAceptadoNoSePuedeReenviar() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("0").content(cdrAceptado("ok")).build()).when(cliente).getStatus(anyString());
        servicio.consultar(id);

        assertThatThrownBy(() -> servicio.reenviar(id)).isInstanceOf(IllegalStateException.class);
        verify(cliente, times(1)).sendSummary(anyString(), any());
    }

    @Test
    void unTicketQueLaSunatNoConoceDejaElResumenParaRevisar() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("127").content("El ticket no existe").build()).when(cliente).getStatus(anyString());

        servicio.consultar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.REVISAR);
        assertThat(r.getCodigoSunat()).isEqualTo("127");
    }

    @Test
    void unaConstanciaQueNoSePuedeLeerDejaElResumenParaRevisarSinTocarLasBoletas() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("0").content(Base64.getEncoder().encodeToString("no soy un zip".getBytes())).build())
                .when(cliente).getStatus(anyString());

        servicio.consultar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.REVISAR);
        assertThat(comprobantes.findByResumenIdOrderByCorrelativo(id)).allMatch(c -> c.getEstado() == EstadoComprobante.EN_RESUMEN);
    }

    @Test
    void sinConstanciaEnLaRespuestaTambienQuedaParaRevisar() {
        Long id = resumenEnviado();
        doReturn(RespuestaStatus.builder().statusCode("0").content("").build()).when(cliente).getStatus(anyString());

        servicio.consultar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.REVISAR);
    }

    @Test
    void sinConexionAlConsultarElResumenSigueEnviadoYSeVuelveAPreguntar() {
        Long id = resumenEnviado();
        doThrow(new SunatNoDisponibleException("sin red", true, null)).when(cliente).getStatus(anyString());

        servicio.consultar(id);

        ResumenDiario r = resumenes.findById(id).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ENVIADO);
        assertThat(r.getIntentosFallidos()).isEqualTo(1);
    }

    @Test
    void unErrorDeDatosAlConsultarRechazaElResumen() {
        Long id = resumenEnviado();
        doThrow(new SunatFaultException("soap-env:Client", "Datos invalidos", "0151")).when(cliente).getStatus(anyString());

        servicio.consultar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.RECHAZADO);
    }

    @Test
    void unErrorPasajeroAlConsultarSoloPosponeLaConsulta() {
        Long id = resumenEnviado();
        doThrow(new SunatFaultException("soap-env:Server", "Error interno", "0100")).when(cliente).getStatus(anyString());

        servicio.consultar(id);

        assertThat(resumenes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ENVIADO);
    }

    @Test
    void unResumenSinEnviarNoSeConsulta() {
        boleta("b1", "10.00");
        Long id = servicio.generarPendientes().get(0);

        assertThatThrownBy(() -> servicio.consultar(id)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(cliente);
    }

    // --- obtener, procesar, generarYEnviar ---

    @Test
    void obtenerDevuelveLaFichaDelResumenConSuCantidadDeBoletas() {
        Long id = resumenEnviado();

        ResumenResponse r = servicio.obtener(id);

        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getNombre()).isEqualTo("RC-20261028-1");
        assertThat(r.getEstado()).isEqualTo(EstadoResumen.ENVIADO);
        assertThat(r.getFechaReferencia()).isEqualTo(HOY);
        assertThat(r.getTicket()).isEqualTo("202610280001");
        assertThat(r.getCantidadBoletas()).isEqualTo(2);
    }

    @Test
    void obtenerUnResumenQueNoExisteFalla() {
        assertThatThrownBy(() -> servicio.obtener(999_999L)).isInstanceOf(ComprobanteNoEncontradoException.class);
    }

    @Test
    void generarYEnviarArmaYMandaCadaResumen() {
        boleta("b1", "10.00");
        doReturn("T1").when(cliente).sendSummary(anyString(), any());

        List<Long> creados = servicio.generarYEnviar();

        assertThat(creados).hasSize(1);
        assertThat(resumenes.findById(creados.get(0)).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ENVIADO);
    }

    @Test
    void siUnResumenFallaAlEnviarLosDemasSeSiguenEnviando() {
        boleta("b1", "10.00");
        reloj.avanzar(Duration.ofDays(1));
        boleta("b2", "10.00");
        doThrow(new RuntimeException("boom")).doReturn("T2").when(cliente).sendSummary(anyString(), any());

        List<Long> creados = servicio.generarYEnviar();

        assertThat(creados).hasSize(2);
        verify(cliente, times(2)).sendSummary(anyString(), any());
        assertThat(resumenes.findById(creados.get(1)).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ENVIADO);
    }

    @Test
    void procesarPendientesEnviaLosQueEsperanYConsultaLosQueYaLesToca() {
        boleta("b1", "10.00");
        Long primero = servicio.generarPendientes().get(0);
        doThrow(new SunatNoDisponibleException("sin red", false, null)).when(cliente).sendSummary(anyString(), any());
        servicio.enviar(primero);   // queda ERROR_TEMPORAL para dentro de 1 minuto
        assertThat(servicio.procesarPendientes()).isZero();   // todavía no le toca

        reloj.avanzar(Duration.ofMinutes(1));
        doReturn("T1").when(cliente).sendSummary(anyString(), any());
        assertThat(servicio.procesarPendientes()).isEqualTo(1);
        assertThat(resumenes.findById(primero).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ENVIADO);

        doReturn(RespuestaStatus.builder().statusCode("0").content(cdrAceptado("ok")).build()).when(cliente).getStatus("T1");
        assertThat(servicio.procesarPendientes()).isZero();   // la consulta es en 1 minuto
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(servicio.procesarPendientes()).isEqualTo(1);
        assertThat(resumenes.findById(primero).orElseThrow().getEstado()).isEqualTo(EstadoResumen.ACEPTADO);
        verify(cliente, never()).sendBill(anyString(), any());
    }
}
