package apisunat.api_sunat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiSunatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiSunatApplication.class, args);
    }
}
