package apisunat.api_sunat.controller;

import apisunat.api_sunat.dto.EstadoTicketResponse;
import apisunat.api_sunat.exception.ManejadorExcepciones;
import apisunat.api_sunat.exception.SunatFaultException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.exception.SunatRespuestaInvalidaException;
import apisunat.api_sunat.service.SunatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SunatController.class)
@Import(ManejadorExcepciones.class)
class SunatControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SunatService sunatService;

    // --- Camino feliz ---

    @Test
    void devuelveElEstadoDelTicketComoJson() throws Exception {
        when(sunatService.consultarTicket("201100000011227"))
                .thenReturn(new EstadoTicketResponse("0127", "El ticket no existe"));

        mvc.perform(get("/api/v1/sunat/tickets/201100000011227"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.statusCode").value("0127"))
                .andExpect(jsonPath("$.content").value("El ticket no existe"));
    }

    @Test
    void unContentNuloSeResponseComoNulo() throws Exception {
        when(sunatService.consultarTicket("1")).thenReturn(new EstadoTicketResponse("98", null));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value("98"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void unTicketDeTreintaDigitosEsElMaximoPermitido() throws Exception {
        String ticket = "1".repeat(30);
        when(sunatService.consultarTicket(ticket)).thenReturn(new EstadoTicketResponse("0", "x"));

        mvc.perform(get("/api/v1/sunat/tickets/" + ticket)).andExpect(status().isOk());
    }

    // --- Validación del ticket: la SUNAT ni se entera ---

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12a", "12%2034", "-1", "1.5", "1e5", "%0A", "12%0A", "1%00", "%3Cscript%3E", "'%3B%20DROP", "%E2%82%AC", "١٢٣"})
    void unTicketQueNoEsUnNumeroEsUnaPeticionInvalida(String ticket) throws Exception {
        mvc.perform(get("/api/v1/sunat/tickets/" + ticket))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El ticket debe ser un número de hasta 30 dígitos"))
                .andExpect(jsonPath("$.codigoSunat").doesNotExist());
        verify(sunatService, never()).consultarTicket(anyString());
    }

    @Test
    void unTicketDeTreintaYUnDigitosEsInvalido() throws Exception {
        mvc.perform(get("/api/v1/sunat/tickets/" + "1".repeat(31))).andExpect(status().isBadRequest());
        verify(sunatService, never()).consultarTicket(anyString());
    }

    // --- Errores de la SUNAT ---

    @Test
    void unErrorSoapDeLaSunatEs502ConSuCodigo() throws Exception {
        when(sunatService.consultarTicket("1"))
                .thenThrow(new SunatFaultException("soap-env:Client.0200", "No se pudo procesar su solicitud", "0200"));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigoSunat").value("0200"))
                .andExpect(jsonPath("$.mensaje").value("La SUNAT respondió con un error: No se pudo procesar su solicitud"));
    }

    @Test
    void unErrorSoapSinCodigoNoIncluyeElCampoCodigoSunat() throws Exception {
        when(sunatService.consultarTicket("1")).thenThrow(new SunatFaultException("S:Server", "falló", null));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.codigoSunat").doesNotExist());
    }

    @Test
    void unDetalleMuyLargoDeLaSunatSeRecorta() throws Exception {
        when(sunatService.consultarTicket("1")).thenThrow(new SunatFaultException("S:Server", "x".repeat(5000), null));

        String cuerpo = mvc.perform(get("/api/v1/sunat/tickets/1")).andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(cuerpo.length()).isLessThan(500);
    }

    @Test
    void siLaSunatNoRespondeATiempoEs504() throws Exception {
        when(sunatService.consultarTicket("1")).thenThrow(new SunatNoDisponibleException("La SUNAT no respondió a tiempo", true, null));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.mensaje").value("La SUNAT no respondió a tiempo"));
    }

    @Test
    void siNoSePuedeConectarConLaSunatEs502() throws Exception {
        when(sunatService.consultarTicket("1")).thenThrow(new SunatNoDisponibleException("No se pudo conectar con la SUNAT", false, null));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje").value("No se pudo conectar con la SUNAT"));
    }

    @Test
    void unaRespuestaIlegibleDeLaSunatEs502SinContarLosDetalles() throws Exception {
        when(sunatService.consultarTicket("1")).thenThrow(new SunatRespuestaInvalidaException("detalle interno: <html> raro"));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensaje").value("La SUNAT respondió algo que no se pudo entender"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle interno"))));
    }

    // --- Lo inesperado no se filtra ---

    @Test
    void unErrorInesperadoEs500SinStacktraceNiMensajeInterno() throws Exception {
        when(sunatService.consultarTicket("1")).thenThrow(new IllegalStateException("clave-secreta en la base jdbc:mysql://interno"));

        mvc.perform(get("/api/v1/sunat/tickets/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("Error interno"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreta"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at apisunat"))));
    }

    // --- Los errores de Spring conservan su código y no se vuelven 500 ---

    @Test
    void unaRutaQueNoExisteEs404() throws Exception {
        mvc.perform(get("/api/v1/sunat/nada")).andExpect(status().isNotFound());
    }

    @Test
    void unMetodoNoPermitidoEs405() throws Exception {
        mvc.perform(post("/api/v1/sunat/tickets/1")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/sunat/tickets/1")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void sinTicketEnLaRutaEs404() throws Exception {
        mvc.perform(get("/api/v1/sunat/tickets/")).andExpect(status().isNotFound());
    }
}
