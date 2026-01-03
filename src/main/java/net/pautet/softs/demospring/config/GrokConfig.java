package net.pautet.softs.demospring.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Configuration
public class GrokConfig {

    @Value("${XAI_API_KEY}")
    private String apiKey;

    @Value("${XAI_MODEL:grok-4}")
    private String model;

    @Bean
    @Primary
    public ChatModel grokChatModel(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            ObservationRegistry observationRegistry) {

        // Create a logging interceptor
        ClientHttpRequestInterceptor loggingInterceptor = new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
                log.info("=== GROK API REQUEST ===");
                log.info("URI: {}", request.getURI());
                log.info("Method: {}", request.getMethod());
                log.info("Headers: {}", request.getHeaders());
                log.info("Body: {}", new String(body, StandardCharsets.UTF_8));
                log.info("========================");
                
                ClientHttpResponse response = execution.execute(request, body);
                
                log.info("=== GROK API RESPONSE ===");
                log.info("Status: {}", response.getStatusCode());
                log.info("Headers: {}", response.getHeaders());
                log.info("=========================");
                
                return response;
            }
        };

        // Use builder pattern with custom RestClient that has the interceptor
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestInterceptor(loggingInterceptor);

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://api.x.ai")
                .apiKey(() -> apiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .maxTokens(2048)
                .build();

        return new OpenAiChatModel(api, options, toolCallingManager, retryTemplate, observationRegistry);
    }
}