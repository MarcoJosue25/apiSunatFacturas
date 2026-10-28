package apisunat.api_sunat.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// En los tests normales el planificador está apagado (lo pone el pom): no corre nada en segundo plano
@SpringBootTest
class ReintentoSchedulerApagadoTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void conLosReintentosApagadosNoExisteElPlanificador() {
        assertThat(contexto.getBeansOfType(ReintentoScheduler.class)).isEmpty();
    }

    @Test
    void elServicioDeReintentosSiExistePorqueSePuedeLlamarADirecto() {
        assertThat(contexto.getBeansOfType(apisunat.api_sunat.service.ReintentoService.class)).hasSize(1);
    }
}
