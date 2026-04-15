package com.caseflow.ai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI caseflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CaseFlow AI Service API")
                        .description("AI orchestration layer for CaseFlow ticket workflows")
                        .version("1.0.0"));
    }
}
