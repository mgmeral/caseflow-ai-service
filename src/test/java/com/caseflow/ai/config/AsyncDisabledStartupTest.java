package com.caseflow.ai.config;

import com.caseflow.ai.messaging.consumer.PolicyAiIngestConsumer;
import com.caseflow.ai.messaging.consumer.TemplateAiIngestConsumer;
import com.caseflow.ai.messaging.consumer.TicketAiSyncConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that the service boots successfully without Kafka configured or connected,
 * and that Kafka consumer beans are NOT created when async is disabled (the default).
 *
 * <p>Uses H2 (configured in src/test/resources/application.yml) with Flyway disabled.
 * Provides stub Spring AI beans to avoid needing a running Ollama or Qdrant instance.
 *
 * <p>This is the critical safety test: it must pass in any environment (CI, local dev)
 * regardless of whether Kafka, Ollama, or Qdrant are available.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "caseflow.ai.async.enabled=false",
        // Intentionally unreachable — confirms Kafka connection is NOT attempted on startup
        "spring.kafka.bootstrap-servers=localhost:19999"
})
class AsyncDisabledStartupTest {

    @TestConfiguration
    static class StubAiConfig {

        @Bean
        @Primary
        VectorStore testVectorStore() {
            VectorStore mock = mock(VectorStore.class);
            when(mock.similaritySearch(org.mockito.ArgumentMatchers.any(org.springframework.ai.vectorstore.SearchRequest.class)))
                    .thenReturn(Collections.emptyList());
            return mock;
        }

        @Bean
        @Primary
        EmbeddingModel testEmbeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        @Primary
        ChatClient testChatClient() {
            return mock(ChatClient.class);
        }
    }

    @Autowired private ApplicationContext context;

    @Test
    void applicationContext_loadsSuccessfully_withoutKafkaOrExternalAiServices() {
        assertThat(context).isNotNull();
    }

    @Test
    void asyncConfig_isDisabledByDefault() {
        AppConfig config = context.getBean(AppConfig.class);
        assertThat(config.getAsync().isEnabled()).isFalse();
    }

    @Test
    void ticketAiSyncConsumer_isNotCreated_whenAsyncDisabled() {
        assertThatThrownBy(() -> context.getBean(TicketAiSyncConsumer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void policyAiIngestConsumer_isNotCreated_whenAsyncDisabled() {
        assertThatThrownBy(() -> context.getBean(PolicyAiIngestConsumer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void templateAiIngestConsumer_isNotCreated_whenAsyncDisabled() {
        assertThatThrownBy(() -> context.getBean(TemplateAiIngestConsumer.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void internalAuthConfig_isNotCreated_whenAuthDisabled() {
        assertThatThrownBy(() -> context.getBean(InternalAuthConfig.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
