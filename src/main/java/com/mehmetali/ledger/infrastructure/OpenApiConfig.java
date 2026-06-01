package com.mehmetali.ledger.infrastructure;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenAPI() {
        SecurityScheme apiKeyScheme = new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-API-Key");

        return new OpenAPI()
            .info(new Info()
                .title("Mini Payment Ledger API")
                .version("1.0.0")
                .description("Event-driven double-entry payment ledger. "
                    + "All endpoints require X-API-Key header. "
                    + "All mutating endpoints require Idempotency-Key (24h TTL). "
                    + "POST /payments returns 202 — Kafka processes asynchronously.")
                .contact(new Contact()
                    .name("Mehmet Ali Bulut")
                    .email("malibulut221@gmail.com")))
            .components(new Components().addSecuritySchemes("apiKey", apiKeyScheme))
            .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
