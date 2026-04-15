package com.caseflow.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "caseflow.ai")
@Data
public class AppConfig {
    private String defaultLocale = "en";
    private int defaultTopK = 5;
    private int chunkSize = 500;
    private int chunkOverlap = 50;
}
