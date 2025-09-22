package com.example.ai.tool.analysis.ai_tool_daisy_api.configuration;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    private final String apiKey;


    public OpenAIConfig(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }


    // This class can be used to configure OpenAI-related beans or settings
    // For example, you might want to define a bean for the OpenAI client
    // or set up properties related to OpenAI API usage.
    @Bean
    public OpenAIClient openAIClient() {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
