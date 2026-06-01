package com.mehmetali.ledger.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Payment Ledger API")
                        .version("1.0.0")
                        .description("Event-driven double-entry payment ledger. "
                                + "All mutating endpoints require an Idempotency-Key header (24h TTL). "
                                + "POST /payments returns 202 — Kafka processes asynchronously.")
                        .contact(new Contact()
                                .name("Mehmet Ali Bulut")
                                .email("malibulut221@gmail.com")));
    }
}
