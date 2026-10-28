package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.service.ResumenDiarioService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumenDiarioSchedulersUnitarioTest {

    @Test
    void cadaEjecucionDelDiaArmaYEnviaElResumen() {
        ResumenDiarioService servicio = mock(ResumenDiarioService.class);
        new ResumenDiarioScheduler(servicio).generarYEnviar();
        verify(servicio, times(1)).generarYEnviar();
    }

    @Test
    void siElArmadoFallaElPlanificadorNoLanzaNadaYLaSiguienteVezSiCorre() {
        ResumenDiarioService servicio = mock(ResumenDiarioService.class);
        when(servicio.generarYEnviar()).thenThrow(new IllegalStateException("la base no responde")).thenReturn(java.util.List.of());
        ResumenDiarioScheduler scheduler = new ResumenDiarioScheduler(servicio);

        assertThatCode(scheduler::generarYEnviar).doesNotThrowAnyException();
        assertThatCode(scheduler::generarYEnviar).doesNotThrowAnyException();

        verify(servicio, times(2)).generarYEnviar();
    }

    @Test
    void cadaPasadaDeConsultaTrabajaLosPendientes() {
        ResumenDiarioService servicio = mock(ResumenDiarioService.class);
        new ConsultaTicketScheduler(servicio).consultar();
        verify(servicio, times(1)).procesarPendientes();
    }

    @Test
    void siUnaPasadaDeConsultaFallaNoLanzaNada() {
        ResumenDiarioService servicio = mock(ResumenDiarioService.class);
        when(servicio.procesarPendientes()).thenThrow(new IllegalStateException("la base no responde")).thenReturn(0);
        ConsultaTicketScheduler scheduler = new ConsultaTicketScheduler(servicio);

        assertThatCode(scheduler::consultar).doesNotThrowAnyException();
        assertThatCode(scheduler::consultar).doesNotThrowAnyException();

        verify(servicio, times(2)).procesarPendientes();
    }
}
