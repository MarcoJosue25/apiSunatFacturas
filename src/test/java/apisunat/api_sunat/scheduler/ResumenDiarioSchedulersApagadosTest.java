package apisunat.api_sunat.scheduler;

import apisunat.api_sunat.service.ResumenDiarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

// En los tests normales los planificadores del resumen están apagados (lo pone el pom): no corre nada en segundo plano
@SpringBootTest
class ResumenDiarioSchedulersApagadosTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    void conElResumenApagadoNoExistenLosPlanificadores() {
        assertThat(contexto.getBeansOfType(ResumenDiarioScheduler.class)).isEmpty();
        assertThat(contexto.getBeansOfType(ConsultaTicketScheduler.class)).isEmpty();
    }

    @Test
    void elServicioDelResumenSiExistePorqueSePuedeLlamarADirecto() {
        assertThat(contexto.getBeansOfType(ResumenDiarioService.class)).hasSize(1);
    }
}
