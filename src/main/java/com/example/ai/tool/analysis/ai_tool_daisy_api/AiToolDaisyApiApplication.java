package com.example.ai.tool.analysis.ai_tool_daisy_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.example.ai.tool.analysis.ai_tool_daisy_api", 
    "com.example.ai.tool.analysis.phase_two_api"
})
@EntityScan(basePackages = {
    "com.example.ai.tool.analysis.ai_tool_daisy_api.entity",
    "com.example.ai.tool.analysis.phase_two_api.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.example.ai.tool.analysis.ai_tool_daisy_api.repository",
    "com.example.ai.tool.analysis.phase_two_api.repository"
})
public class AiToolDaisyApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiToolDaisyApiApplication.class, args);
    }
}