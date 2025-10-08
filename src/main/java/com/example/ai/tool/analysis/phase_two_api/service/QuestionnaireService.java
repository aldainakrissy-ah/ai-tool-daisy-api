package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.entity.*;
import com.example.ai.tool.analysis.phase_two_api.pojo.*;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireService {
    
    private final QuestionnaireRepository questionnaireRepository;
    private final ObjectMapper objectMapper;
    
    @Transactional(readOnly = true)
    public List<QuestionnaireDto> getAllActiveQuestionnaires() {
        List<Questionnaire> questionnaires = questionnaireRepository.findActiveQuestionnairesOrderByCreatedDate();
        return questionnaires.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Optional<QuestionnaireDto> getQuestionnaireById(String questionnaireId) {
        return questionnaireRepository.findByQuestionnaireIdWithDetails(questionnaireId)
                .map(this::convertToDto);
    }
    
    @Transactional
    public QuestionnaireDto createQuestionnaire(QuestionnaireDto questionnaireDto) {
        if (questionnaireRepository.existsByQuestionnaireId(questionnaireDto.getQuestionnaireId())) {
            throw new IllegalArgumentException("Questionnaire with ID " + questionnaireDto.getQuestionnaireId() + " already exists");
        }
        
        Questionnaire questionnaire = convertToEntity(questionnaireDto);
        Questionnaire savedQuestionnaire = questionnaireRepository.save(questionnaire);
        return convertToDto(savedQuestionnaire);
    }
    
    @Transactional
    public QuestionnaireDto updateQuestionnaire(String questionnaireId, QuestionnaireDto questionnaireDto) {
        Questionnaire existingQuestionnaire = questionnaireRepository.findByQuestionnaireId(questionnaireId)
                .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found: " + questionnaireId));
        
        updateQuestionnaireFields(existingQuestionnaire, questionnaireDto);
        Questionnaire savedQuestionnaire = questionnaireRepository.save(existingQuestionnaire);
        return convertToDto(savedQuestionnaire);
    }
    
    @Transactional
    public void deleteQuestionnaire(String questionnaireId) {
        Questionnaire questionnaire = questionnaireRepository.findByQuestionnaireId(questionnaireId)
                .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found: " + questionnaireId));
        
        questionnaire.setIsActive(false);
        questionnaireRepository.save(questionnaire);
    }
    
    private QuestionnaireDto convertToDto(Questionnaire questionnaire) {
        QuestionnaireDto dto = new QuestionnaireDto();
        dto.setId(questionnaire.getId());
        dto.setQuestionnaireId(questionnaire.getQuestionnaireId());
        dto.setTitle(parseJsonToMap(questionnaire.getTitle()));
        dto.setDescription(parseJsonToMap(questionnaire.getDescription()));
        dto.setCreatedAt(questionnaire.getCreatedAt());
        dto.setUpdatedAt(questionnaire.getUpdatedAt());
        dto.setIsActive(questionnaire.getIsActive());
        dto.setVersion(questionnaire.getVersion());
        
        if (questionnaire.getSections() != null) {
            dto.setSections(questionnaire.getSections().stream()
                    .map(this::convertSectionToDto)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private QuestionnaireSectionDto convertSectionToDto(QuestionnaireSection section) {
        QuestionnaireSectionDto dto = new QuestionnaireSectionDto();
        dto.setId(section.getId());
        dto.setSectionId(section.getSectionId());
        dto.setTitle(section.getTitle());
        dto.setSortOrder(section.getSortOrder());
        dto.setTitleTranslations(parseJsonToMap(section.getTitleTranslations()));
        
        if (section.getQuestions() != null) {
            dto.setQuestions(section.getQuestions().stream()
                    .map(this::convertQuestionToDto)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private QuestionDto convertQuestionToDto(Question question) {
        QuestionDto dto = new QuestionDto();
        dto.setId(question.getId());
        dto.setQuestionId(question.getQuestionId());
        dto.setText(question.getText());
        dto.setType(question.getType());
        dto.setSortOrder(question.getSortOrder());
        dto.setIsRequired(question.getIsRequired());
        dto.setTextTranslations(parseJsonToMap(question.getTextTranslations()));
        dto.setValidationRules(parseJsonToObject(question.getValidationRules()));
        
        // Convert options
        dto.setOptions(parseJsonToOptionsList(question.getOptions()));
        
        // Convert columns
        if (question.getColumns() != null) {
            dto.setColumns(question.getColumns().stream()
                    .map(this::convertColumnToDto)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private QuestionColumnDto convertColumnToDto(QuestionColumn column) {
        QuestionColumnDto dto = new QuestionColumnDto();
        dto.setId(column.getId());
        dto.setColumnId(column.getColumnId());
        dto.setLabel(column.getLabel());
        dto.setType(column.getType());
        dto.setSortOrder(column.getSortOrder());
        dto.setLabelTranslations(parseJsonToMap(column.getLabelTranslations()));
        return dto;
    }
    
    private Questionnaire convertToEntity(QuestionnaireDto dto) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setQuestionnaireId(dto.getQuestionnaireId());
        questionnaire.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        questionnaire.setVersion(dto.getVersion() != null ? dto.getVersion() : 1);
        
        // Convert Maps to JSON strings for the title and description fields directly
        try {
            questionnaire.setTitle(objectMapper.writeValueAsString(dto.getTitle()));
            questionnaire.setDescription(objectMapper.writeValueAsString(dto.getDescription()));
        } catch (JsonProcessingException e) {
            log.error("Error converting title/description to JSON", e);
            throw new RuntimeException("Error converting data", e);
        }
        
        // Convert sections
        if (dto.getSections() != null) {
            List<QuestionnaireSection> sections = dto.getSections().stream()
                    .map(sectionDto -> convertSectionToEntity(sectionDto, questionnaire))
                    .collect(Collectors.toList());
            questionnaire.setSections(sections);
        }
        
        return questionnaire;
    }
    
    private QuestionnaireSection convertSectionToEntity(QuestionnaireSectionDto dto, Questionnaire questionnaire) {
        QuestionnaireSection section = new QuestionnaireSection();
        section.setSectionId(dto.getSectionId());
        section.setTitle(dto.getTitle());
        section.setSortOrder(dto.getSortOrder());
        section.setQuestionnaire(questionnaire);
        
        try {
            section.setTitleTranslations(objectMapper.writeValueAsString(dto.getTitleTranslations()));
        } catch (JsonProcessingException e) {
            log.error("Error converting section title translations to JSON", e);
            throw new RuntimeException("Error converting data", e);
        }
        
        if (dto.getQuestions() != null) {
            List<Question> questions = dto.getQuestions().stream()
                    .map(questionDto -> convertQuestionToEntity(questionDto, section))
                    .collect(Collectors.toList());
            section.setQuestions(questions);
        }
        
        return section;
    }
    
    private Question convertQuestionToEntity(QuestionDto dto, QuestionnaireSection section) {
        Question question = new Question();
        question.setQuestionId(dto.getQuestionId());
        question.setText(dto.getText());
        question.setType(dto.getType());
        question.setSortOrder(dto.getSortOrder());
        question.setIsRequired(dto.getIsRequired() != null ? dto.getIsRequired() : false);
        question.setSection(section);
        
        try {
            question.setTextTranslations(objectMapper.writeValueAsString(dto.getTextTranslations()));
            question.setValidationRules(objectMapper.writeValueAsString(dto.getValidationRules()));
            question.setOptions(objectMapper.writeValueAsString(dto.getOptions()));
        } catch (JsonProcessingException e) {
            log.error("Error converting question data to JSON", e);
            throw new RuntimeException("Error converting data", e);
        }
        
        if (dto.getColumns() != null) {
            List<QuestionColumn> columns = dto.getColumns().stream()
                    .map(columnDto -> convertColumnToEntity(columnDto, question))
                    .collect(Collectors.toList());
            question.setColumns(columns);
        }
        
        return question;
    }
    
    private QuestionColumn convertColumnToEntity(QuestionColumnDto dto, Question question) {
        QuestionColumn column = new QuestionColumn();
        column.setColumnId(dto.getColumnId());
        column.setLabel(dto.getLabel());
        column.setType(dto.getType());
        column.setSortOrder(dto.getSortOrder());
        column.setQuestion(question);
        
        try {
            column.setLabelTranslations(objectMapper.writeValueAsString(dto.getLabelTranslations()));
        } catch (JsonProcessingException e) {
            log.error("Error converting column label translations to JSON", e);
            throw new RuntimeException("Error converting data", e);
        }
        
        return column;
    }
    
    private void updateQuestionnaireFields(Questionnaire existing, QuestionnaireDto dto) {
        // Update title and description directly with JSON strings
        try {
            existing.setTitle(objectMapper.writeValueAsString(dto.getTitle()));
            existing.setDescription(objectMapper.writeValueAsString(dto.getDescription()));
        } catch (JsonProcessingException e) {
            log.error("Error converting title/description to JSON", e);
            throw new RuntimeException("Error converting data", e);
        }
        
        if (dto.getVersion() != null) {
            existing.setVersion(dto.getVersion());
        }
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }
    }
    
    // Utility methods for JSON conversion
    private Map<String, String> parseJsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON to Map: {}", json, e);
            return new HashMap<>();
        }
    }
    
    private Map<String, Object> parseJsonToObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON to Object: {}", json, e);
            return new HashMap<>();
        }
    }
    
    private List<QuestionOptionDto> parseJsonToOptionsList(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<QuestionOptionDto>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON to Options List: {}", json, e);
            return new ArrayList<>();
        }
    }
}