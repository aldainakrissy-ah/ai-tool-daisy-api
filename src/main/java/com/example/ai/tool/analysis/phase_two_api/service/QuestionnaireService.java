package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.entity.*;
import com.example.ai.tool.analysis.phase_two_api.pojo.*;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireRepository;
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

    @Transactional(readOnly = true)
    public List<QuestionnaireDto> getAllActiveQuestionnaires() {
        List<Questionnaire> questionnaires = questionnaireRepository.findAll();
        return questionnaires.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<QuestionnaireDto> getQuestionnaireById(String id) {
        return questionnaireRepository.findByIdWithDetails(id)
                .map(this::convertToDto);
    }

    @Transactional
    public QuestionnaireDto createQuestionnaire(QuestionnaireDto questionnaireDto) {
        if (questionnaireRepository.existsById(questionnaireDto.getId())) {
            throw new IllegalArgumentException("Questionnaire with ID " + questionnaireDto.getId() + " already exists");
        }

        Questionnaire questionnaire = convertToEntity(questionnaireDto);
        Questionnaire savedQuestionnaire = questionnaireRepository.save(questionnaire);
        return convertToDto(savedQuestionnaire);
    }

    @Transactional
    public QuestionnaireDto updateQuestionnaire(String id, QuestionnaireDto questionnaireDto) {
        Questionnaire existingQuestionnaire = questionnaireRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found: " + id));

        updateQuestionnaireFields(existingQuestionnaire, questionnaireDto);
        Questionnaire savedQuestionnaire = questionnaireRepository.save(existingQuestionnaire);
        return convertToDto(savedQuestionnaire);
    }

    @Transactional
    public void deleteQuestionnaire(String id) {
        questionnaireRepository.deleteById(id);
    }

    private QuestionnaireDto convertToDto(Questionnaire questionnaire) {
        QuestionnaireDto dto = new QuestionnaireDto();
        dto.setId(questionnaire.getId());
        dto.setTitle(toLocalizedTextDto(questionnaire.getTitle()));
        dto.setDescription(toLocalizedTextDto(questionnaire.getDescription()));
        dto.setInstructions(toLocalizedTextDto(questionnaire.getInstructions()));
        dto.setIsActive(questionnaire.getIsActive());
        dto.setVersion(questionnaire.getVersion());
        if (questionnaire.getSections() != null) {
            dto.setSections(questionnaire.getSections().stream()
                    .map(this::convertSectionToDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private SectionDto convertSectionToDto(Section section) {
        SectionDto dto = new SectionDto();
        dto.setId(section.getId());
        dto.setTitle(toLocalizedTextDto(section.getTitle()));
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
        dto.setType(question.getType());
        dto.setText(toLocalizedTextDto(question.getText()));
        if (question.getOptions() != null) {
            dto.setOptions(question.getOptions().stream()
                    .map(this::convertOptionToDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    private OptionDto convertOptionToDto(Option option) {
        OptionDto dto = new OptionDto();
        dto.setValue(option.getValue());
        dto.setLabel(toLocalizedTextDto(option.getLabel()));
        return dto;
    }

    private LocalizedTextDto toLocalizedTextDto(LocalizedText text) {
        if (text == null)
            return null;
        LocalizedTextDto dto = new LocalizedTextDto();
        dto.setEn(text.getEn());
        dto.setNl(text.getNl());
        return dto;
    }

    private Questionnaire convertToEntity(QuestionnaireDto dto) {
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setId(dto.getId());
        questionnaire.setTitle(toLocalizedText(dto.getTitle()));
        questionnaire.setDescription(toLocalizedText(dto.getDescription()));
        questionnaire.setInstructions(toLocalizedText(dto.getInstructions()));
        questionnaire.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        questionnaire.setVersion(dto.getVersion() != null ? dto.getVersion() : 1);

        // Convert sections and maintain bidirectional relationships
        if (dto.getSections() != null) {
            Set<Section> sections = dto.getSections().stream()
                    .map(sectionDto -> convertSectionToEntity(sectionDto, questionnaire))
                    .collect(Collectors.toSet());
            questionnaire.setSections(sections);
        }
        return questionnaire;
    }

    private Section convertSectionToEntity(SectionDto dto, Questionnaire questionnaire) {
        Section section = new Section();
        // Ensure ID is set - generate a UUID if not provided
        section.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString());
        section.setTitle(toLocalizedText(dto.getTitle()));

        if (dto.getQuestions() != null) {
            Set<Question> questions = dto.getQuestions().stream()
                    .map(questionDto -> {
                        Question question = convertQuestionToEntity(questionDto);
                        question.setId(
                                questionDto.getId() != null ? questionDto.getId() : UUID.randomUUID().toString());
                        question.setSection(section); // Set up bidirectional relationship
                        return question;
                    })
                    .collect(Collectors.toSet());
            section.setQuestions(questions);
        } else {
            section.setQuestions(new HashSet<>());
        }

        return section;
    }

    private Question convertQuestionToEntity(QuestionDto dto) {
        Question question = new Question();
        question.setId(dto.getId());
        question.setType(dto.getType());
        question.setText(toLocalizedText(dto.getText()));
        if (dto.getOptions() != null) {
            question.setOptions(dto.getOptions().stream()
                    .map(this::convertOptionToEntity)
                    .collect(Collectors.toSet()));
        }
        return question;
    }

    private Option convertOptionToEntity(OptionDto dto) {
        Option option = new Option();
        option.setValue(dto.getValue());
        option.setLabel(toLocalizedText(dto.getLabel()));
        return option;
    }

    private LocalizedText toLocalizedText(LocalizedTextDto dto) {
        if (dto == null)
            return null;
        LocalizedText text = new LocalizedText();
        text.setEn(dto.getEn());
        text.setNl(dto.getNl());
        return text;
    }

    private void updateQuestionnaireFields(Questionnaire existing, QuestionnaireDto dto) {
        existing.setTitle(toLocalizedText(dto.getTitle()));
        existing.setDescription(toLocalizedText(dto.getDescription()));
        existing.setInstructions(toLocalizedText(dto.getInstructions()));

        if (dto.getVersion() != null) {
            existing.setVersion(dto.getVersion());
        }
        if (dto.getIsActive() != null) {
            existing.setIsActive(dto.getIsActive());
        }

        // Update sections if provided
        if (dto.getSections() != null) {
            // Create a map of existing sections by ID for easy lookup
            Map<String, Section> existingSectionsMap = existing.getSections() != null
                    ? existing.getSections().stream().collect(Collectors.toMap(Section::getId, s -> s, (s1, s2) -> s1))
                    : new HashMap<>();

            Set<Section> updatedSections = dto.getSections().stream()
                    .map(sectionDto -> {
                        // Check if section with this ID already exists
                        Section existingSection = existingSectionsMap.get(sectionDto.getId());
                        if (existingSection != null) {
                            // Update existing section
                            existingSection.setTitle(toLocalizedText(sectionDto.getTitle()));
                            if (sectionDto.getQuestions() != null) {
                                Set<Question> updatedQuestions = sectionDto.getQuestions().stream()
                                        .map(questionDto -> {
                                            Question question = convertQuestionToEntity(questionDto);
                                            question.setSection(existingSection);
                                            return question;
                                        })
                                        .collect(Collectors.toSet());
                                existingSection.setQuestions(updatedQuestions);
                            }
                            return existingSection;
                        } else {
                            // Create new section
                            return convertSectionToEntity(sectionDto, existing);
                        }
                    })
                    .collect(Collectors.toSet());
            existing.setSections(updatedSections);
        }
    }
}
