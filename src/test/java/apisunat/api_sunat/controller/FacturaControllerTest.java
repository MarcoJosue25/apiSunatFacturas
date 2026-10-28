package apisunat.api_sunat.controller;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.FacturaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.exception.CertificadoInvalidoException;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.FirmaException;
import apisunat.api_sunat.exception.ManejadorExcepciones;
import apisunat.api_sunat.exception.SolicitudInvalidaException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.service.ComprobanteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FacturaController.class)
@Import(ManejadorExcepciones.class)
class FacturaControllerTest {

    private static final String CUERPO_VALIDO = """
            {"idExterno": "pedido-1",
             "cliente": {"ruc": "20123456786", "razonSocial": "CLIENTE SAC"},
             "items": [{"descripcion": "Polo básico negro", "cantidad": 2, "precioUnitario": 35.00}]}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ComprobanteService servicio;

    @MockitoBean
    private SunatProperties propiedades;

    @BeforeEach
    void preparar() {
        when(propiedades.getRuc()).thenReturn("20100066603");
    }

    private static FacturaResponse respuesta(EstadoComprobante estado) {
        return FacturaResponse.builder().id(7L).idExterno("pedido-1").numero("F001-4355").estado(estado)
                .fechaEmision(LocalDateTime.of(2026, 10, 28, 10, 30)).importeTotal(new BigDecimal("70.00"))
                .codigoSunat("0").descripcionSunat("La Factura numero F001-4355, ha sido aceptada")
                .observaciones(List.of("4031 - Debe indicar el nombre comercial")).build();
    }

    // --- POST: estados ---

    @Test
    void unaFacturaNuevaYAceptadaDa201ConSusDatos() throws Exception {
        when(servicio.emitir(any())).thenReturn(new ComprobanteService.Emision(respuesta(EstadoComprobante.ACEPTADO), true));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.idExterno").value("pedido-1"))
                .andExpect(jsonPath("$.numero").value("F001-4355"))
                .andExpect(jsonPath("$.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.importeTotal").value(70.00))
                .andExpect(jsonPath("$.codigoSunat").value("0"))
                .andExpect(jsonPath("$.observaciones[0]").value("4031 - Debe indicar el nombre comercial"));
    }

    @Test
    void unaFacturaRechazadaTambienDa201PorqueElNumeroYaSeGasto() throws Exception {
        when(servicio.emitir(any())).thenReturn(new ComprobanteService.Emision(respuesta(EstadoComprobante.RECHAZADO), true));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
    }

    @Test
    void siLaSunatNoRespondioLaFacturaQuedoGuardadaYDa202() throws Exception {
        when(servicio.emitir(any())).thenReturn(new ComprobanteService.Emision(respuesta(EstadoComprobante.ERROR_TEMPORAL), true));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("ERROR_TEMPORAL"));
    }

    @Test
    void unaPeticionRepetidaDa200ConLaFacturaQueYaExistia() throws Exception {
        when(servicio.emitir(any())).thenReturn(new ComprobanteService.Emision(respuesta(EstadoComprobante.ACEPTADO), false));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("F001-4355"));
    }

    @Test
    void elCuerpoLlegaAlServicioComoUnaFacturaCompleta() throws Exception {
        when(servicio.emitir(any())).thenReturn(new ComprobanteService.Emision(respuesta(EstadoComprobante.ACEPTADO), true));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO)).andExpect(status().isCreated());

        ArgumentCaptor<FacturaRequest> captura = ArgumentCaptor.forClass(FacturaRequest.class);
        verify(servicio).emitir(captura.capture());
        FacturaRequest r = captura.getValue();
        assertThat(r.getIdExterno()).isEqualTo("pedido-1");
        assertThat(r.getCliente().getRuc()).isEqualTo("20123456786");
        assertThat(r.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getDescripcion()).isEqualTo("Polo básico negro");
            assertThat(i.getCantidad()).isEqualByComparingTo("2");
            assertThat(i.getPrecioUnitario()).isEqualByComparingTo("35.00");
            assertThat(i.getUnidadMedida()).isEqualTo("NIU");
        });
    }

    // --- POST: validación (nada llega al servicio) ---

    private void rechaza(String cuerpo, String mensaje) throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        verifyNoInteractions(servicio);
    }

    @Test
    void sinIdExternoSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("\"idExterno\": \"pedido-1\",", ""), "El idExterno es obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"con espacio", "ñandú", "a/b", "a;b", "<script>"})
    void unIdExternoConCaracteresNoPermitidosOMuyLargoSeRechaza(String id) throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO.replace("pedido-1", id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.startsWith("El idExterno debe tener")));
        verifyNoInteractions(servicio);
    }

    @Test
    void unIdExternoDeMasDe100CaracteresSeRechazaYUnoDeExactamente100SePermite() throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO.replace("pedido-1", "x".repeat(101))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);

        when(servicio.emitir(any())).thenReturn(new ComprobanteService.Emision(respuesta(EstadoComprobante.ACEPTADO), true));
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO.replace("pedido-1", "x".repeat(100))))
                .andExpect(status().isCreated());
    }

    @Test
    void unIdExternoVacioSeRechaza() throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO.replace("pedido-1", "")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void sinClienteSeRechaza() throws Exception {
        rechaza("""
                {"idExterno": "p1", "items": [{"descripcion": "x", "cantidad": 1, "precioUnitario": 10}]}""", "El cliente es obligatorio");
    }

    @Test
    void conUnRucMalSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("20123456786", "20123456787"), "El RUC del cliente no es válido");
    }

    @Test
    void sinItemsSeRechaza() throws Exception {
        rechaza("""
                {"idExterno": "p1", "cliente": {"ruc": "20123456786", "razonSocial": "X"}, "items": []}""", "La factura necesita al menos un ítem");
    }

    @Test
    void sinLaListaDeItemsSeRechaza() throws Exception {
        rechaza("""
                {"idExterno": "p1", "cliente": {"ruc": "20123456786", "razonSocial": "X"}}""", "La factura necesita al menos un ítem");
    }

    @Test
    void conUnMontoEnCeroSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("35.00", "0"), "El precio unitario del ítem debe ser mayor que cero");
    }

    @Test
    void conUnMontoNegativoSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("35.00", "-5"), "El precio unitario del ítem debe ser mayor que cero");
    }

    @Test
    void conUnaCantidadEnCeroSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("\"cantidad\": 2", "\"cantidad\": 0"), "La cantidad del ítem debe ser mayor que cero");
    }

    @Test
    void sinDescripcionDelItemSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("Polo básico negro", "  "), "La descripción del ítem no puede estar vacía");
    }

    @Test
    void sinRazonSocialDelClienteSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("CLIENTE SAC", ""), "La razón social del cliente no puede estar vacía");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{", "no es json", "[]", "{\"idExterno\": }", "\"texto\""})
    void unCuerpoQueNoEsUnJsonValidoDa400(String cuerpo) throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(cuerpo == null ? "" : cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido"));
        verifyNoInteractions(servicio);
    }

    @Test
    void textoEnUnNumeroDa400() throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO.replace("35.00", "\"mucho\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void sinTipoDeContenidoJsonDa415() throws Exception {
        mvc.perform(post("/api/v1/facturas").contentType(MediaType.TEXT_PLAIN).content(CUERPO_VALIDO))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(servicio);
    }

    // --- POST: errores del servicio ---

    @Test
    void unaSolicitudQueNoSePuedeConvertirEnFacturaDa400ConSuMensaje() throws Exception {
        when(servicio.emitir(any())).thenThrow(new SolicitudInvalidaException("Ítem 1: el precio es demasiado alto", null));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ítem 1: el precio es demasiado alto"));
    }

    @Test
    void unProblemaConElCertificadoDa500SinContarDetallesInternos() throws Exception {
        when(servicio.emitir(any())).thenThrow(new CertificadoInvalidoException("No se encontró el archivo del certificado: /secreto/ruta/cert.p12"));

        MvcResult r = mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("El servicio no pudo firmar la factura: revise la configuración del certificado"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("/secreto").doesNotContain("cert.p12");
    }

    @Test
    void unaFallaDeFirmaDa500Generico() throws Exception {
        when(servicio.emitir(any())).thenThrow(new FirmaException("No se pudo firmar el XML: detalle interno", null));

        mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle interno"))));
    }

    @Test
    void unErrorInesperadoDa500ConMensajeGenericoYSinElTextoOriginal() throws Exception {
        when(servicio.emitir(any())).thenThrow(new IllegalStateException("jdbc:mysql://servidor-interno:3306 clave=secreta"));

        MvcResult r = mvc.perform(post("/api/v1/facturas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("Error interno"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("secreta").doesNotContain("servidor-interno");
    }

    // --- GET ---

    @Test
    void obtenerDevuelveLaFactura() throws Exception {
        when(servicio.obtener(7L)).thenReturn(respuesta(EstadoComprobante.ACEPTADO));

        mvc.perform(get("/api/v1/facturas/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.numero").value("F001-4355"));
    }

    @Test
    void unIdQueNoExisteDa404() throws Exception {
        when(servicio.obtener(999L)).thenThrow(new ComprobanteNoEncontradoException(999L));

        mvc.perform(get("/api/v1/facturas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el comprobante 999"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "1e3", "-", "99999999999999999999999"})
    void unIdQueNoEsUnNumeroDa400(String id) throws Exception {
        mvc.perform(get("/api/v1/facturas/" + id)).andExpect(status().isBadRequest());
        verify(servicio, never()).obtener(anyLong());
    }

    @Test
    void elXmlSeDescargaEnIso88591ComoAdjuntoConElNombreDeLaSunat() throws Exception {
        byte[] xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>ñ</a>".getBytes(StandardCharsets.ISO_8859_1);
        when(servicio.obtener(7L)).thenReturn(respuesta(EstadoComprobante.ACEPTADO));
        when(servicio.obtenerXml(7L)).thenReturn(xml);

        MvcResult r = mvc.perform(get("/api/v1/facturas/7/xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"20100066603-01-F001-4355.xml\""))
                .andReturn();

        assertThat(r.getResponse().getContentType()).containsIgnoringCase("application/xml").containsIgnoringCase("ISO-8859-1");
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(xml);
    }

    @Test
    void elXmlDeUnIdQueNoExisteDa404() throws Exception {
        when(servicio.obtener(999L)).thenThrow(new ComprobanteNoEncontradoException(999L));

        mvc.perform(get("/api/v1/facturas/999/xml")).andExpect(status().isNotFound());
    }

    @Test
    void laRespuestaNoTraeElXmlNiLaConstanciaNiCamposInternos() throws Exception {
        when(servicio.obtener(7L)).thenReturn(respuesta(EstadoComprobante.ACEPTADO));

        MvcResult r = mvc.perform(get("/api/v1/facturas/7")).andReturn();

        String json = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("xml").doesNotContain("cdr").doesNotContain("version").doesNotContain("clave")
                .doesNotContain("password").doesNotContain("certificado");
    }

    @Test
    void sunatNoDisponibleNoSeFiltraComoDetalleInternoEnUnGet() throws Exception {
        when(servicio.obtener(7L)).thenThrow(new SunatNoDisponibleException("sin conexión", false, null));
        mvc.perform(get("/api/v1/facturas/7")).andExpect(status().isBadGateway());
    }
}
