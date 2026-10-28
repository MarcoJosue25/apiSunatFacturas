package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.service.ReintentoService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReintentoSchedulerUnitarioTest {

    @Test
    void cadaEjecucionHaceUnaPasadaDeReintentos() {
        ReintentoService servicio = mock(ReintentoService.class);
        new ReintentoScheduler(servicio).reintentar();
        verify(servicio, times(1)).procesarPendientes();
    }

    @Test
    void siUnaPasadaFallaElPlanificadorNoLanzaNadaYLaSiguienteSiCorre() {
        ReintentoService servicio = mock(ReintentoService.class);
        when(servicio.procesarPendientes()).thenThrow(new IllegalStateException("la base no responde")).thenReturn(0);
        ReintentoScheduler scheduler = new ReintentoScheduler(servicio);

        assertThatCode(scheduler::reintentar).doesNotThrowAnyException();
        assertThatCode(scheduler::reintentar).doesNotThrowAnyException();

        verify(servicio, times(2)).procesarPendientes();
    }
}
