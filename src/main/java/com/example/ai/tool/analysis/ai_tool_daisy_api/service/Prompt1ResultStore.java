package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.entity.Prompt1ResultEntity;
import com.example.ai.tool.analysis.ai_tool_daisy_api.repository.Prompt1ResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The one place that saves an analysis result for the apps ({@code prompt1_results}), for the old PDF route and the
 * turn-based route alike. The apps find a result by the client's name ({@code patient_id}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Prompt1ResultStore {

    private final Prompt1ResultRepository repository;

    public void save(String professionalName, String clientName, String promptType, String resultJson) {
        Prompt1ResultEntity entity = new Prompt1ResultEntity();
        entity.setProfessionalId(professionalName);
        entity.setPatientId(clientName);
        entity.setPromptType(promptType);
        entity.setResultJson(resultJson);
        repository.save(entity);
        log.debug("Saved a {} result for the apps", promptType);
    }
}
