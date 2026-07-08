package org.murat.samsungichackathon.configs;

// config/AiConfig.java

import com.google.genai.errors.ServerException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

@Configuration
public class AiConfig {
    /** Auto-configure edilen GoogleGenAiChatModel'den ChatClient uretir. */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RetryTemplate geminiRetryTemplate() {
        // Google GenAI SDK'nin ServerException'i (5xx) icin acik retry.
        // spring.ai.retry devreye giremezse bu katman yakalar.
        return RetryTemplate.builder()
                .maxAttempts(4)
                .retryOn(ServerException.class)
                .exponentialBackoff(Duration.ofSeconds(2), 2, Duration.ofSeconds(10))
                .build();
    }
}
