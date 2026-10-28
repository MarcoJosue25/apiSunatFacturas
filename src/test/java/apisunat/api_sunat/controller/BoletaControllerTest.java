package apisunat.api_sunat.controller;

import apisunat.api_sunat.config.SunatProperties;
import apisunat.api_sunat.dto.BoletaRequest;
import apisunat.api_sunat.dto.FacturaResponse;
import apisunat.api_sunat.exception.ComprobanteNoEncontradoException;
import apisunat.api_sunat.exception.ManejadorExcepciones;
import apisunat.api_sunat.exception.SolicitudInvalidaException;
import apisunat.api_sunat.model.enums.EstadoComprobante;
import apisunat.api_sunat.service.BoletaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoletaController.class)
@Import(ManejadorExcepciones.class)
class BoletaControllerTest {

    private static final String CUERPO_VALIDO = """
            {"idExterno": "venta-1",
             "cliente": {"tipoDocumento": "1", "numeroDocumento": "46237547", "nombre": "Luana Pazos"},
             "items": [{"descripcion": "Polo básico", "cantidad": 2, "precioUnitario": 35.00}]}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BoletaService servicio;

    @MockitoBean
    private SunatProperties propiedades;

    @BeforeEach
    void preparar() {
        when(propiedades.getRuc()).thenReturn("20100066603");
    }

    private static FacturaResponse respuesta() {
        return FacturaResponse.builder().id(9L).idExterno("venta-1").numero("B001-12").estado(EstadoComprobante.PENDIENTE_DE_RESUMEN)
                .fechaEmision(LocalDateTime.of(2026, 10, 28, 10, 30)).importeTotal(new BigDecimal("70.00")).build();
    }

    @Test
    void unaBoletaNuevaDa201ConSuEstadoPendienteDeResumen() throws Exception {
        when(servicio.emitir(any())).thenReturn(new BoletaService.Emision(respuesta(), true));

        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.numero").value("B001-12"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_DE_RESUMEN"))
                .andExpect(jsonPath("$.codigoSunat").doesNotExist());
    }

    @Test
    void unaPeticionRepetidaDa200ConLaMismaBoleta() throws Exception {
        when(servicio.emitir(any())).thenReturn(new BoletaService.Emision(respuesta(), false));

        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("B001-12"));
    }

    @Test
    void elCuerpoLlegaAlServicioComoUnaBoletaCompleta() throws Exception {
        when(servicio.emitir(any())).thenReturn(new BoletaService.Emision(respuesta(), true));

        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO)).andExpect(status().isCreated());

        ArgumentCaptor<BoletaRequest> captura = ArgumentCaptor.forClass(BoletaRequest.class);
        verify(servicio).emitir(captura.capture());
        BoletaRequest r = captura.getValue();
        assertThat(r.getIdExterno()).isEqualTo("venta-1");
        assertThat(r.getCliente().getTipoDocumento()).isEqualTo("1");
        assertThat(r.getCliente().getNumeroDocumento()).isEqualTo("46237547");
        assertThat(r.getItems()).hasSize(1);
    }

    @Test
    void sinClienteLaBoletaEsAnonimaYSeAcepta() throws Exception {
        when(servicio.emitir(any())).thenReturn(new BoletaService.Emision(respuesta(), true));

        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idExterno\": \"v2\", \"items\": [{\"descripcion\": \"x\", \"cantidad\": 1, \"precioUnitario\": 10}]}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<BoletaRequest> captura = ArgumentCaptor.forClass(BoletaRequest.class);
        verify(servicio).emitir(captura.capture());
        assertThat(captura.getValue().getCliente()).isNull();
    }

    private void rechaza(String cuerpo, String mensaje) throws Exception {
        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(mensaje));
        verifyNoInteractions(servicio);
    }

    @Test
    void sinIdExternoSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("\"idExterno\": \"venta-1\",", ""), "El idExterno es obligatorio");
    }

    @Test
    void sinItemsSeRechaza() throws Exception {
        rechaza("{\"idExterno\": \"v1\", \"items\": []}", "La boleta necesita al menos un ítem");
    }

    @Test
    void conUnTipoDeDocumentoQueNoExisteSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("\"tipoDocumento\": \"1\"", "\"tipoDocumento\": \"9\""), "El tipo de documento del cliente debe ser 0, 1, 4, 6 o 7");
    }

    @Test
    void unDniQueNoTieneOchoDigitosSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("46237547", "123"), "El número de documento del cliente no corresponde a su tipo de documento");
    }

    @Test
    void conUnMontoEnCeroSeRechaza() throws Exception {
        rechaza(CUERPO_VALIDO.replace("35.00", "0"), "El precio unitario del ítem debe ser mayor que cero");
    }

    @Test
    void unJsonRotoDa400() throws Exception {
        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El cuerpo de la petición no es un JSON válido"));
        verifyNoInteractions(servicio);
    }

    @Test
    void unaBoletaQueElServicioRechazaDa400ConSuMensaje() throws Exception {
        when(servicio.emitir(any())).thenThrow(new SolicitudInvalidaException("Una boleta de más de S/ 700 necesita el documento del cliente", null));

        mvc.perform(post("/api/v1/boletas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Una boleta de más de S/ 700 necesita el documento del cliente"));
    }

    @Test
    void obtenerDevuelveLaBoleta() throws Exception {
        when(servicio.obtener(9L)).thenReturn(respuesta());

        mvc.perform(get("/api/v1/boletas/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("B001-12"));
    }

    @Test
    void unaBoletaQueNoExisteDa404() throws Exception {
        when(servicio.obtener(999L)).thenThrow(new ComprobanteNoEncontradoException(999L));

        mvc.perform(get("/api/v1/boletas/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/boletas/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void elXmlSeDescargaEnIso88591ConElNombreDeLaSunatParaBoletas() throws Exception {
        byte[] xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><a>ñ</a>".getBytes(StandardCharsets.ISO_8859_1);
        when(servicio.obtener(9L)).thenReturn(respuesta());
        when(servicio.obtenerXml(9L)).thenReturn(xml);

        MvcResult r = mvc.perform(get("/api/v1/boletas/9/xml"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"20100066603-03-B001-12.xml\""))
                .andReturn();

        assertThat(r.getResponse().getContentType()).containsIgnoringCase("application/xml").containsIgnoringCase("ISO-8859-1");
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(xml);
    }
}
