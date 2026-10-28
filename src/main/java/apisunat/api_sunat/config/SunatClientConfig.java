package apisunat.api_sunat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

// El RestClient con el que se habla con la SUNAT, con los timeouts de la configuración
@Configuration
public class SunatClientConfig {

    @Bean
    public RestClient sunatRestClient(SunatProperties propiedades) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(propiedades.getConexionTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofMillis(propiedades.getLecturaTimeoutMs()));
        return RestClient.builder().requestFactory(fabrica).build();
    }
}
