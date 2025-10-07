package com.example.ai.tool.analysis.phase_two_api.configuration;

import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireLoaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireConfiguration {
    
    private final QuestionnaireLoaderService questionnaireLoaderService;
    
    @Bean
    public CommandLineRunner loadQuestionnaires() {
        return args -> {
            log.info("Loading questionnaires from JSON files...");
            try {
                questionnaireLoaderService.loadAllQuestionnaires();
                log.info("Successfully loaded all questionnaires");
            } catch (Exception e) {
                log.error("Failed to load questionnaires on startup", e);
            }
        };
    }
}
