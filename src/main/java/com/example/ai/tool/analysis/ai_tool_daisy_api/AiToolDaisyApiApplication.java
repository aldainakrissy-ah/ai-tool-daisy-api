package com.example.ai.tool.analysis.ai_tool_daisy_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class AiToolDaisyApiApplication {

	public static void main(String[] args) {

		SpringApplication.run(AiToolDaisyApiApplication.class, args);

	}

}
