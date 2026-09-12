package com.caseflow.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs the async mode state at startup so operators know at a glance
 * whether Kafka consumers are active or disabled.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncStartupLogger {

    private final AppConfig appConfig;

    @PostConstruct
    void logAsyncMode() {
        if (appConfig.getAsync().isEnabled()) {
            log.info("=== ASYNC INGEST MODE: ENABLED ===");
            log.info("Kafka consumers active for topics: {}, {}, {}",
                    appConfig.getAsync().getTopic().getTicketSync(),
                    appConfig.getAsync().getTopic().getPolicyIngest(),
                    appConfig.getAsync().getTopic().getTemplateIngest());
        } else {
            log.info("=== ASYNC INGEST MODE: DISABLED ===");
            log.info("Kafka consumers are NOT active. Sync REST endpoints remain fully available.");
            log.info("To enable: set caseflow.ai.async.enabled=true and configure KAFKA_BOOTSTRAP_SERVERS.");
        }

        if (appConfig.getAuth().isEnabled()) {
            log.info("Internal API auth: ENABLED (X-Internal-Api-Key required on all /api/** requests)");
        } else {
            log.info("Internal API auth: DISABLED (enable via caseflow.ai.auth.enabled=true before production)");
        }
    }
}
