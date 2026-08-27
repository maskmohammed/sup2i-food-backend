package com.sup2i.food.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    // Kept in sync with pom.xml's <version>.
    private static final String API_VERSION = "0.0.1-SNAPSHOT";

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI sup2iFoodOpenApi() {

        return new OpenAPI()
            .info(
                new Info()
                    .title("SUP2I FOOD API")
                    .description(
                        "Backend REST API for the SUP2I FOOD university catering "
                            + "platform: catalog, inventory, orders, and authentication."
                    )
                    .version(API_VERSION)
            )
            .components(
                new Components()
                    .addSecuritySchemes(
                        BEARER_SCHEME_NAME,
                        new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
            .addSecurityItem(
                new SecurityRequirement()
                    .addList(BEARER_SCHEME_NAME)
            );
    }
}
