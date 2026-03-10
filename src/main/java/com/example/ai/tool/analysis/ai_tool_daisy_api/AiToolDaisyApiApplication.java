package com.example.ai.tool.analysis.ai_tool_daisy_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
@SpringBootApplication(scanBasePackages = "com.example.ai.tool.analysis")
@EnableAsync
@ComponentScan(basePackages = {
    "com.example.ai.tool.analysis.ai_tool_daisy_api",
    "com.example.ai.tool.analysis.phase_two_api",
    "com.example.ai.tool.analysis.phase_three_api"
})
public class AiToolDaisyApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiToolDaisyApiApplication.class, args);
    }
}