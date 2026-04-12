package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.pojo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireLoaderService {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final QuestionnaireService questionnaireService;

    @Value("${questionnaire.auto-load-on-startup:true}")
    private boolean autoLoadOnStartup;

    /**
     * Loads all questionnaire JSON files from the questionnaires directory
     * 
     * @throws IOException if there's an error reading the files
     */
    public void loadAllQuestionnaires() throws IOException {
        if (!autoLoadOnStartup) {
            log.info("Questionnaire auto-loading disabled. Skipping.");
            return;
        }

        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
        Resource[] resources = resolver.getResources("classpath:questionnaires/*.json");

        if (resources.length == 0) {
            log.warn("No questionnaire JSON files found in questionnaires directory");
            return;
        }

        log.info("Found {} questionnaire files to load", resources.length);
        int loaded = 0;

        for (Resource resource : resources) {
            try {
                String filename = resource.getFilename();
                log.info("Loading questionnaire from: {}", filename);
                loadQuestionnaireFromJson(filename);
                loaded++;
            } catch (Exception e) {
                log.error("Failed to load questionnaire: {}", resource.getFilename(), e);
            }
        }

        log.info("Successfully loaded {}/{} questionnaires", loaded, resources.length);
    }

    public void loadQuestionnaireFromJson(String fileName) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:questionnaires/" + fileName);
        if (!resource.exists()) {
            throw new IOException("Questionnaire file not found: " + fileName);
        }
        JsonNode jsonNode = objectMapper.readTree(resource.getInputStream());
        QuestionnaireDto questionnaireDto = parseJsonToQuestionnaireDto(jsonNode);
        try {
            questionnaireService.createQuestionnaire(questionnaireDto);
            log.info("Successfully loaded questionnaire: {}", questionnaireDto.getId());
        } catch (IllegalArgumentException e) {
            log.warn("Questionnaire already exists: {}", questionnaireDto.getId());
        }
    }

    public QuestionnaireDto parseJsonToQuestionnaireDto(JsonNode jsonNode) {
        QuestionnaireDto dto = new QuestionnaireDto();
        dto.setId(jsonNode.get("id").asText());
        dto.setTitle(parseLocalizedTextDto(jsonNode.get("title")));
        dto.setDescription(parseLocalizedTextDto(jsonNode.get("description")));
        JsonNode sectionsNode = jsonNode.get("sections");
        if (sectionsNode != null && sectionsNode.isArray()) {
            List<SectionDto> sections = new ArrayList<>();
            for (JsonNode sectionNode : sectionsNode) {
                sections.add(parseSectionDto(sectionNode));
            }
            dto.setSections(sections);
        }
        dto.setIsActive(true);
        dto.setVersion(1);
        return dto;
    }

    private SectionDto parseSectionDto(JsonNode sectionNode) {
        SectionDto dto = new SectionDto();
        dto.setId(sectionNode.get("id").asText());

        // Parse title if it exists
        JsonNode titleNode = sectionNode.get("title");
        if (titleNode != null) {
            dto.setTitle(parseLocalizedTextDto(titleNode));
        }

        // Parse questions array
        JsonNode questionsNode = sectionNode.get("questions");
        if (questionsNode != null && questionsNode.isArray()) {
            List<QuestionDto> questions = new ArrayList<>();
            for (JsonNode questionNode : questionsNode) {
                QuestionDto questionDto = parseQuestionDto(questionNode);
                questions.add(questionDto);
            }
            dto.setQuestions(questions);

            // Log the number of questions parsed for debugging
            log.debug("Parsed {} questions for section {}", questions.size(), dto.getId());
        } else {
            log.debug("No questions found for section {}", dto.getId());
            dto.setQuestions(new ArrayList<>());
        }

        return dto;
    }

    private QuestionDto parseQuestionDto(JsonNode questionNode) {
        QuestionDto dto = new QuestionDto();

        JsonNode idNode = questionNode.get("id");
        if (idNode == null) {
            throw new IllegalArgumentException("Question must have an id");
        }
        dto.setId(idNode.asText());

        JsonNode typeNode = questionNode.get("type");
        if (typeNode == null) {
            throw new IllegalArgumentException("Question must have a type");
        }
        dto.setType(typeNode.asText());

        JsonNode textNode = questionNode.get("text");
        if (textNode == null) {
            throw new IllegalArgumentException("Question must have text");
        }
        dto.setText(parseLocalizedTextDto(textNode));

        // Parse required field with default value false if not present
        dto.setRequired(false); // Default value
        JsonNode requiredNode = questionNode.get("required");
        if (requiredNode != null && !requiredNode.isNull()) {
            dto.setRequired(requiredNode.asBoolean());
        }

        JsonNode optionsNode = questionNode.get("options");
        if (optionsNode != null && optionsNode.isArray()) {
            List<OptionDto> options = new ArrayList<>();
            for (JsonNode optionNode : optionsNode) {
                OptionDto optionDto = new OptionDto();
                JsonNode valueNode = optionNode.get("value");
                if (valueNode != null) {
                    optionDto.setValue(valueNode.asText());
                }
                JsonNode labelNode = optionNode.get("label");
                if (labelNode != null) {
                    optionDto.setLabel(parseLocalizedTextDto(labelNode));
                }
                JsonNode groupNode = optionNode.get("group");
                if (groupNode != null && !groupNode.isNull()) {
                    optionDto.setGroup(groupNode.asText());
                }
                options.add(optionDto);
            }
            dto.setOptions(options);
        }
        return dto;
    }

    private LocalizedTextDto parseLocalizedTextDto(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        LocalizedTextDto dto = new LocalizedTextDto();

        JsonNode enNode = node.get("en");
        if (enNode != null && !enNode.isNull()) {
            dto.setEn(enNode.asText());
        }

        JsonNode nlNode = node.get("nl");
        if (nlNode != null && !nlNode.isNull()) {
            dto.setNl(nlNode.asText());
        }

        return dto;
    }
}