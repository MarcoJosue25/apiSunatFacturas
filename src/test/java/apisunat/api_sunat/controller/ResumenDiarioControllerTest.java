package apisunat.api_sunat.controller;

import apisunat.api_sunat.dto.ResumenResponse;
import apisunat.api_sunat.exception.ManejadorExcepciones;
import apisunat.api_sunat.exception.OperacionNoPermitidaException;
import apisunat.api_sunat.exception.ResumenNoEncontradoException;
import apisunat.api_sunat.exception.SunatNoDisponibleException;
import apisunat.api_sunat.model.enums.EstadoResumen;
import apisunat.api_sunat.service.ResumenDiarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumenDiarioController.class)
@Import(ManejadorExcepciones.class)
class ResumenDiarioControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResumenDiarioService servicio;

    private static ResumenResponse resumen(Long id, EstadoResumen estado) {
        return ResumenResponse.builder().id(id).nombre("RC-20261028-" + id).estado(estado).fechaReferencia(LocalDate.of(2026, 10, 28))
                .ticket("202610280001").cantidadBoletas(2).build();
    }

    @Test
    void generarDevuelveLosResumenesArmadosYEnviados() throws Exception {
        when(servicio.generarYEnviar()).thenReturn(List.of(4L, 5L));
        when(servicio.obtener(4L)).thenReturn(resumen(4L, EstadoResumen.ENVIADO));
        when(servicio.obtener(5L)).thenReturn(resumen(5L, EstadoResumen.ERROR_TEMPORAL));

        mvc.perform(post("/api/v1/resumenes/generar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("RC-20261028-4"))
                .andExpect(jsonPath("$[0].estado").value("ENVIADO"))
                .andExpect(jsonPath("$[0].cantidadBoletas").value(2))
                .andExpect(jsonPath("$[1].estado").value("ERROR_TEMPORAL"));
    }

    @Test
    void sinBoletasPendientesGenerarDevuelveUnaListaVacia() throws Exception {
        when(servicio.generarYEnviar()).thenReturn(List.of());

        mvc.perform(post("/api/v1/resumenes/generar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void obtenerDevuelveElResumenSinCamposVacios() throws Exception {
        when(servicio.obtener(4L)).thenReturn(resumen(4L, EstadoResumen.ENVIADO));

        mvc.perform(get("/api/v1/resumenes/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaReferencia").value("2026-10-28"))
                .andExpect(jsonPath("$.ticket").value("202610280001"))
                .andExpect(jsonPath("$.codigoSunat").doesNotExist());
    }

    @Test
    void unResumenQueNoExisteDa404ConSuPropioMensaje() throws Exception {
        when(servicio.obtener(999L)).thenThrow(new ResumenNoEncontradoException(999L));

        mvc.perform(get("/api/v1/resumenes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el resumen 999"));
    }

    @Test
    void unIdQueNoEsUnNumeroDa400() throws Exception {
        mvc.perform(get("/api/v1/resumenes/abc")).andExpect(status().isBadRequest());
        verify(servicio, never()).obtener(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void reenviarDevuelveElResumenYaEnviadoDeNuevo() throws Exception {
        when(servicio.reenviar(4L)).thenReturn(resumen(4L, EstadoResumen.ENVIADO));

        mvc.perform(post("/api/v1/resumenes/4/reenviar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVIADO"));
    }

    @Test
    void reenviarUnResumenQueNoEstaRechazadoDa409() throws Exception {
        when(servicio.reenviar(4L)).thenThrow(new OperacionNoPermitidaException("Solo se reenvía un resumen rechazado, y este está ACEPTADO"));

        mvc.perform(post("/api/v1/resumenes/4/reenviar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Solo se reenvía un resumen rechazado, y este está ACEPTADO"));
    }

    @Test
    void reenviarUnResumenQueNoExisteDa404() throws Exception {
        when(servicio.reenviar(999L)).thenThrow(new ResumenNoEncontradoException(999L));

        mvc.perform(post("/api/v1/resumenes/999/reenviar")).andExpect(status().isNotFound());
    }

    @Test
    void siLaSunatNoRespondeAlReenviarDa502() throws Exception {
        when(servicio.reenviar(4L)).thenThrow(new SunatNoDisponibleException("sin conexión", false, null));

        mvc.perform(post("/api/v1/resumenes/4/reenviar")).andExpect(status().isBadGateway());
    }

    @Test
    void generarConGetNoExiste() throws Exception {
        mvc.perform(get("/api/v1/resumenes/generar")).andExpect(status().isBadRequest());
    }
}
