package com.ledgerxlite.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerXOpenAPI() {
        // Server info
        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080/api");
        localServer.setDescription("Local Development Server");

        // Contact info
        Contact contact = new Contact();
        contact.setName("LedgerX Team");
        contact.setEmail("support@ledgerx.local");

        // API Info
        Info info = new Info()
                .title("LedgerX Lite API")
                .version("1.0.0")
                .description("Transaction-safe financial ledger system with append-only architecture")
                .contact(contact);

        // Security scheme name
        String securitySchemeName = "bearerAuth";

        // Return OpenAPI bean with JWT security
        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer))
                // Add security globally
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                );
    }
}
