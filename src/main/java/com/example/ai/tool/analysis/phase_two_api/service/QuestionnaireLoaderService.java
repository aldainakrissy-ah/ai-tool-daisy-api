package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.entity.Question;
import com.example.ai.tool.analysis.phase_two_api.entity.QuestionColumn;
import com.example.ai.tool.analysis.phase_two_api.pojo.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireLoaderService {
    
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final QuestionnaireService questionnaireService;
    
    public void loadQuestionnaireFromJson(String fileName) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:questionnaires/" + fileName);
        
        if (!resource.exists()) {
            throw new IOException("Questionnaire file not found: " + fileName);
        }
        
        JsonNode jsonNode = objectMapper.readTree(resource.getInputStream());
        QuestionnaireDto questionnaireDto = parseJsonToQuestionnaireDto(jsonNode);
        
        try {
            questionnaireService.createQuestionnaire(questionnaireDto);
            log.info("Successfully loaded questionnaire: {}", questionnaireDto.getQuestionnaireId());
        } catch (IllegalArgumentException e) {
            log.warn("Questionnaire already exists: {}", questionnaireDto.getQuestionnaireId());
        }
    }
    
    public void loadAllQuestionnaires() {
        String[] questionnaireFiles = {
            "anxiety-gad7.json",
            "mental-health-phq9.json",
            "food-diary.json",
            "my-health-intake-adult.json",
            "my-health-intake-child.json",
            "ethos-scan-adult.json",
            "ethos-scan-child.json",
            "ethos-scan-hybrid.json",
            "fatigue-fss.json",
            "heta-coverage.json",
            "pain-vas.json",
            "perceived-stress-pss10.json",
            "physical-activity-ipaqSf.json",
            "sleep-quality-psqi.json",
            "social-support.json"
        };
        
        for (String fileName : questionnaireFiles) {
            try {
                loadQuestionnaireFromJson(fileName);
            } catch (IOException e) {
                log.error("Failed to load questionnaire from file: {}", fileName, e);
            }
        }
    }
    
    public QuestionnaireDto parseJsonToQuestionnaireDto(JsonNode jsonNode) {
        QuestionnaireDto dto = new QuestionnaireDto();
        
        dto.setQuestionnaireId(jsonNode.get("id").asText());
        
        // Parse title directly as a translations map
        JsonNode titleNode = jsonNode.get("title");
        Map<String, String> titleTranslations;
        if (titleNode.isTextual()) {
            // If it's just a string, use it as the English title
            titleTranslations = Map.of("en", titleNode.asText());
        } else {
            titleTranslations = parseTranslations(titleNode);
        }
        dto.setTitle(titleTranslations); // Store as a Map directly
        
        // Parse description directly as a translations map
        JsonNode descriptionNode = jsonNode.get("description");
        if (descriptionNode != null) {
            Map<String, String> descriptionTranslations;
            if (descriptionNode.isTextual()) {
                // If it's just a string, use it as the English description
                descriptionTranslations = Map.of("en", descriptionNode.asText());
            } else {
                descriptionTranslations = parseTranslations(descriptionNode);
            }
            dto.setDescription(descriptionTranslations); // Store as a Map directly
        }
        
        // Parse sections
        JsonNode sectionsNode = jsonNode.get("sections");
        if (sectionsNode != null && sectionsNode.isArray()) {
            List<QuestionnaireSectionDto> sections = new ArrayList<>();
            int sectionOrder = 0;
            
            for (JsonNode sectionNode : sectionsNode) {
                QuestionnaireSectionDto sectionDto = parseSectionDto(sectionNode, sectionOrder++);
                sections.add(sectionDto);
            }
            
            dto.setSections(sections);
        }
        
        dto.setIsActive(true);
        dto.setVersion(1);
        
        return dto;
    }
    
    private QuestionnaireSectionDto parseSectionDto(JsonNode sectionNode, int order) {
        QuestionnaireSectionDto dto = new QuestionnaireSectionDto();
        
        dto.setSectionId(sectionNode.get("id").asText());
        dto.setSortOrder(order);
        
        // Parse title
        JsonNode titleNode = sectionNode.get("title");
        if (titleNode != null) {
            if (titleNode.isTextual()) {
                dto.setTitle(titleNode.asText());
            } else {
                Map<String, String> titleTranslations = parseTranslations(titleNode);
                dto.setTitle(titleTranslations.get("en"));
                dto.setTitleTranslations(titleTranslations);
            }
        }
        
        // Parse questions
        JsonNode questionsNode = sectionNode.get("questions");
        if (questionsNode != null && questionsNode.isArray()) {
            List<QuestionDto> questions = new ArrayList<>();
            int questionOrder = 0;
            
            for (JsonNode questionNode : questionsNode) {
                QuestionDto questionDto = parseQuestionDto(questionNode, questionOrder++);
                questions.add(questionDto);
            }
            
            dto.setQuestions(questions);
        }
        
        return dto;
    }
    
    private QuestionDto parseQuestionDto(JsonNode questionNode, int order) {
        QuestionDto dto = new QuestionDto();
        
        dto.setQuestionId(questionNode.get("id").asText());
        dto.setSortOrder(order);
        
        // Parse type
        String typeString = questionNode.get("type").asText();
        dto.setType(parseQuestionType(typeString));
        
        // Parse text
        JsonNode textNode = questionNode.get("text");
        if (textNode.isTextual()) {
            dto.setText(textNode.asText());
        } else {
            Map<String, String> textTranslations = parseTranslations(textNode);
            dto.setText(textTranslations.get("en"));
            dto.setTextTranslations(textTranslations);
        }
        
        // Parse options
        JsonNode optionsNode = questionNode.get("options");
        if (optionsNode != null && optionsNode.isArray()) {
            List<QuestionOptionDto> options = new ArrayList<>();
            for (JsonNode optionNode : optionsNode) {
                QuestionOptionDto optionDto = new QuestionOptionDto();
                optionDto.setValue(optionNode.get("value").asText());
                
                JsonNode labelNode = optionNode.get("label");
                if (labelNode.isTextual()) {
                    optionDto.setLabel(Map.of("en", labelNode.asText()));
                } else {
                    optionDto.setLabel(parseTranslations(labelNode));
                }
                
                options.add(optionDto);
            }
            dto.setOptions(options);
        }
        
        // Parse columns (for table questions)
        JsonNode columnsNode = questionNode.get("columns");
        if (columnsNode != null && columnsNode.isArray()) {
            List<QuestionColumnDto> columns = new ArrayList<>();
            int columnOrder = 0;
            
            for (JsonNode columnNode : columnsNode) {
                QuestionColumnDto columnDto = new QuestionColumnDto();
                columnDto.setColumnId(columnNode.get("id").asText());
                columnDto.setSortOrder(columnOrder++);
                columnDto.setType(parseColumnType(columnNode.get("type").asText()));
                
                JsonNode labelNode = columnNode.get("label");
                if (labelNode.isTextual()) {
                    columnDto.setLabel(labelNode.asText());
                } else {
                    Map<String, String> labelTranslations = parseTranslations(labelNode);
                    columnDto.setLabel(labelTranslations.get("en"));
                    columnDto.setLabelTranslations(labelTranslations);
                }
                
                columns.add(columnDto);
            }
            
            dto.setColumns(columns);
        }
        
        dto.setIsRequired(false); // Default to false, can be enhanced
        
        return dto;
    }
    
    private Map<String, String> parseTranslations(JsonNode node) {
        Map<String, String> translations = new HashMap<>();
        
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                translations.put(entry.getKey(), entry.getValue().asText());
            });
        }
        
        return translations;
    }
    
    private Question.QuestionType parseQuestionType(String typeString) {
        switch (typeString.toLowerCase()) {
            case "text":
                return Question.QuestionType.TEXT;
            case "textarea":
                return Question.QuestionType.TEXTAREA;
            case "yes-no":
                return Question.QuestionType.YES_NO;
            case "likert-scale-3":
                return Question.QuestionType.LIKERT_SCALE_3;
            case "likert-scale-4":
                return Question.QuestionType.LIKERT_SCALE_4;
            case "likert-scale-5":
                return Question.QuestionType.LIKERT_SCALE_5;
            case "multiple-choice":
                return Question.QuestionType.MULTIPLE_CHOICE;
            case "single-choice":
                return Question.QuestionType.SINGLE_CHOICE;
            case "number":
                return Question.QuestionType.NUMBER;
            case "date":
                return Question.QuestionType.DATE;
            case "time":
                return Question.QuestionType.TIME;
            case "table":
                return Question.QuestionType.TABLE;
            default:
                log.warn("Unknown question type: {}, defaulting to TEXT", typeString);
                return Question.QuestionType.TEXT;
        }
    }
    
    private QuestionColumn.ColumnType parseColumnType(String typeString) {
        switch (typeString.toLowerCase()) {
            case "text":
                return QuestionColumn.ColumnType.TEXT;
            case "number":
                return QuestionColumn.ColumnType.NUMBER;
            case "date":
                return QuestionColumn.ColumnType.DATE;
            case "time":
                return QuestionColumn.ColumnType.TIME;
            case "email":
                return QuestionColumn.ColumnType.EMAIL;
            case "textarea":
                return QuestionColumn.ColumnType.TEXTAREA;
            default:
                log.warn("Unknown column type: {}, defaulting to TEXT", typeString);
                return QuestionColumn.ColumnType.TEXT;
        }
    }
}