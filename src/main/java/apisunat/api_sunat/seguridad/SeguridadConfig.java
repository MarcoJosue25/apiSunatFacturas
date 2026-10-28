package apisunat.api_sunat.seguridad;

import apisunat.api_sunat.config.SunatProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Pone la llave de la API delante de todo lo que cuelga de /api
@Slf4j
@Configuration
public class SeguridadConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyFilter> filtroDeLlave(SunatProperties propiedades) {
        ApiKeyFilter filtro = new ApiKeyFilter(propiedades.getApiKey());
        if (!filtro.estaActivo()) {
            log.warn("La API no pide llave (sunat.api-key vacía): cualquiera que llegue al puerto puede emitir comprobantes. Solo para desarrollo.");
        }
        FilterRegistrationBean<ApiKeyFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.addUrlPatterns("/api/*");
        registro.setName("filtroDeLlave");
        return registro;
    }
}
