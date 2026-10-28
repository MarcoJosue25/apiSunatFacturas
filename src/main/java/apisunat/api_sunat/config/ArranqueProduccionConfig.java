package apisunat.api_sunat.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// Con el perfil prod la configuración se revisa ANTES de conectarse a la base de datos: si no, Flyway ya habría migrado el esquema de una base
// de producción cuando la aplicación se niega a arrancar por tener la configuración de pruebas.
@Configuration
@Profile("prod")
public class ArranqueProduccionConfig {

    private static final String BEAN_BASE_DE_DATOS = "dataSource";

    @Bean
    static BeanFactoryPostProcessor validarAntesDeConectarALaBase() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition(BEAN_BASE_DE_DATOS)) {
                BeanDefinition base = beanFactory.getBeanDefinition(BEAN_BASE_DE_DATOS);
                String[] actuales = base.getDependsOn() == null ? new String[0] : base.getDependsOn();
                String[] nuevas = java.util.Arrays.copyOf(actuales, actuales.length + 1);
                nuevas[actuales.length] = "validadorProduccion";
                base.setDependsOn(nuevas);
            }
        };
    }
}
