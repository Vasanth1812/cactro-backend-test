package backend_test.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cactro Technical Assessment API")
                        .version("1.0.0")
                        .description("Backend RESTful API service built with Spring Boot 3 & Java 21 for Cactro Technical Assessment")
                        .contact(new Contact()
                                .name("Developer")
                                .email("vasanth.kumar@botsedge.ai"))
                        .license(new License().name("Apache 2.0").url("https://spring.io")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server"),
                        new Server().url("/").description("Current Server (Production / Render)")
                ));
    }
}
