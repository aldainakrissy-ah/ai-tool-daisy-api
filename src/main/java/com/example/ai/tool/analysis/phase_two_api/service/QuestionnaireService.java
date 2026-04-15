package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.entity.*;
import com.example.ai.tool.analysis.phase_two_api.pojo.*;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
        dto.setDescription(toLocalizedTextDto(section.getDescription()));
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
        if (question.getColumns() != null) {
            dto.setColumns(question.getColumns().stream()
                    .map(this::convertColumnToDto)
                    .collect(Collectors.toList()));
        }
        dto.setValidation(convertValidationToDto(question.getValidation()));
        dto.setMaxNames(question.getMaxNames());
        dto.setRequired(question.getRequired());
        dto.setSortOrder(question.getSortOrder());
        return dto;
    }

    private OptionDto convertOptionToDto(Option option) {
        OptionDto dto = new OptionDto();
        dto.setValue(option.getValue());
        dto.setLabel(toLocalizedTextDto(option.getLabel()));
        if(option.getGroup() != null) {
            dto.setGroup(option.getGroup());
        }
        return dto;
    }

    private QuestionColumnDto convertColumnToDto(QuestionColumn column) {
        QuestionColumnDto dto = new QuestionColumnDto();
        dto.setId(column.getId());
        dto.setLabel(toLocalizedTextDto(column.getLabel()));
        dto.setType(column.getType().name().toLowerCase());
        dto.setSortOrder(column.getSortOrder());
        return dto;
    }

    private ValidationRulesDto convertValidationToDto(ValidationRules validation) {
        if (validation == null)
            return null;
        ValidationRulesDto dto = new ValidationRulesDto();
        dto.setMin(validation.getMin());
        dto.setMax(validation.getMax());
        dto.setRequired(validation.getRequired());
        dto.setPattern(validation.getPattern());
        dto.setMinLength(validation.getMinLength());
        dto.setMaxLength(validation.getMaxLength());
        dto.setMinDate(validation.getMinDate());
        dto.setMaxDate(validation.getMaxDate());
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
            AtomicInteger sectionOrder = new AtomicInteger(0);
            List<Section> sections = dto.getSections().stream()
                    .map(sectionDto -> {
                        Section section = convertSectionToEntity(sectionDto, questionnaire);
                        section.setSortOrder(sectionOrder.getAndIncrement());
                        return section;
                    })
                    .collect(Collectors.toList());
            questionnaire.setSections(sections);
        }
        return questionnaire;
    }

    private Section convertSectionToEntity(SectionDto dto, Questionnaire questionnaire) {
        Section section = new Section();
        // Ensure ID is set - generate a UUID if not provided
        section.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString());
        section.setTitle(toLocalizedText(dto.getTitle()));
        section.setDescription(toLocalizedText(dto.getDescription()));

        if (dto.getQuestions() != null) {
            AtomicInteger questionOrder = new AtomicInteger(0);
            List<Question> questions = dto.getQuestions().stream()
                    .map(questionDto -> {
                        Question question = convertQuestionToEntity(questionDto);
                        question.setId(
                                questionDto.getId() != null ? questionDto.getId() : UUID.randomUUID().toString());
                        question.setSection(section); // Set up bidirectional relationship
                        question.setSortOrder(questionOrder.getAndIncrement());
                        return question;
                    })
                    .collect(Collectors.toList());
            section.setQuestions(questions);
        } else {
            section.setQuestions(new ArrayList<>());
        }

        return section;
    }

    private Question convertQuestionToEntity(QuestionDto dto) {
        Question question = new Question();
        question.setId(dto.getId());
        question.setType(dto.getType());
        question.setText(toLocalizedText(dto.getText()));

        // Handle options
        if (dto.getOptions() != null) {
            AtomicInteger optionOrder = new AtomicInteger(0);
            List<Option> options = dto.getOptions().stream()
                    .map(optionDto -> {
                        Option option = convertOptionToEntity(optionDto);
                        option.setSortOrder(optionOrder.getAndIncrement());
                        return option;
                    })
                    .collect(Collectors.toList());
            question.setOptions(options);
        }

        // Handle columns
        if (dto.getColumns() != null) {
            AtomicInteger columnOrder = new AtomicInteger(0);
            List<QuestionColumn> columns = dto.getColumns().stream()
                    .map(columnDto -> {
                        QuestionColumn column = convertColumnToEntity(columnDto);
                        column.setSortOrder(columnOrder.getAndIncrement());
                        column.setQuestion(question); // Set up bidirectional relationship
                        return column;
                    })
                    .collect(Collectors.toList());
            question.setColumns(columns);
        }

        // Handle other fields
        if (dto.getValidation() != null) {
            question.setValidation(convertValidationToEntity(dto.getValidation()));
        }
        question.setMaxNames(dto.getMaxNames());
        question.setRequired(dto.getRequired());
        question.setSortOrder(dto.getSortOrder());

        return question;
    }

    private QuestionColumn convertColumnToEntity(QuestionColumnDto dto) {
        QuestionColumn column = new QuestionColumn();
        column.setId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString());
        column.setLabel(toLocalizedText(dto.getLabel()));
        column.setType(QuestionColumn.ColumnType.valueOf(dto.getType().toUpperCase()));
        return column;
    }

    private ValidationRules convertValidationToEntity(ValidationRulesDto dto) {
        if (dto == null)
            return null;
        ValidationRules validation = new ValidationRules();
        validation.setMin(dto.getMin());
        validation.setMax(dto.getMax());
        validation.setRequired(dto.getRequired());
        validation.setPattern(dto.getPattern());
        validation.setMinLength(dto.getMinLength());
        validation.setMaxLength(dto.getMaxLength());
        validation.setMinDate(dto.getMinDate());
        validation.setMaxDate(dto.getMaxDate());
        return validation;
    }

    private Option convertOptionToEntity(OptionDto dto) {
        Option option = new Option();
        option.setValue(dto.getValue());
        option.setLabel(toLocalizedText(dto.getLabel()));
        if(dto.getGroup() != null) {
            option.setGroup(dto.getGroup());
        }
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
            Map<String, Section> existingSectionsMap = existing.getSections().stream()
                    .collect(Collectors.toMap(Section::getId, s -> s));

            // Create new sections list
            AtomicInteger sectionOrder = new AtomicInteger(0);
            List<Section> updatedSections = new ArrayList<>();

            dto.getSections().forEach(sectionDto -> {
                final Section section = existingSectionsMap.get(sectionDto.getId());
                final Section updatedSection = section != null ? section : new Section();

                // Set or update section ID
                if (updatedSection.getId() == null) {
                    updatedSection
                            .setId(sectionDto.getId() != null ? sectionDto.getId() : UUID.randomUUID().toString());
                }

                // Update section fields
                updatedSection.setTitle(toLocalizedText(sectionDto.getTitle()));
                updatedSection.setSortOrder(sectionOrder.getAndIncrement());

                // Handle questions
                List<Question> currentQuestions = new ArrayList<>(updatedSection.getQuestions());
                updatedSection.getQuestions().clear(); // Clear existing questions safely

                if (sectionDto.getQuestions() != null) {
                    AtomicInteger questionOrder = new AtomicInteger(0);
                    sectionDto.getQuestions().forEach(questionDto -> {
                        final Question question;
                        if (questionDto.getId() != null) {
                            // Try to find existing question
                            question = currentQuestions.stream()
                                    .filter(q -> q.getId().equals(questionDto.getId()))
                                    .findFirst()
                                    .orElseGet(() -> {
                                        Question q = new Question();
                                        q.setId(UUID.randomUUID().toString());
                                        return q;
                                    });
                        } else {
                            question = new Question();
                            question.setId(UUID.randomUUID().toString());
                        }

                        // Update question fields
                        question.setType(questionDto.getType());
                        question.setText(toLocalizedText(questionDto.getText()));
                        question.setSortOrder(questionOrder.getAndIncrement());
                        question.setRequired(questionDto.getRequired());
                        question.setMaxNames(questionDto.getMaxNames());
                        question.setSection(updatedSection); // Maintain bidirectional relationship

                        // Handle options
                        if (questionDto.getOptions() != null) {
                            AtomicInteger optionOrder = new AtomicInteger(0);
                            List<Option> updatedOptions = questionDto.getOptions().stream()
                                    .map(optionDto -> {
                                        Option option = new Option();
                                        option.setValue(optionDto.getValue());
                                        option.setLabel(toLocalizedText(optionDto.getLabel()));
                                        if(optionDto.getGroup() != null) {
                                            option.setGroup(optionDto.getGroup());
                                        }
                                        option.setSortOrder(optionOrder.getAndIncrement());
                                        return option;
                                    })
                                    .collect(Collectors.toList());

                            question.getOptions().clear();
                            question.getOptions().addAll(updatedOptions);
                        }

                        // Handle columns
                        if (questionDto.getColumns() != null) {
                            AtomicInteger columnOrder = new AtomicInteger(0);
                            List<QuestionColumn> updatedColumns = questionDto.getColumns().stream()
                                    .map(columnDto -> {
                                        QuestionColumn column = convertColumnToEntity(columnDto);
                                        column.setSortOrder(columnOrder.getAndIncrement());
                                        column.setQuestion(question); // Maintain bidirectional relationship
                                        return column;
                                    })
                                    .collect(Collectors.toList());

                            if (question.getColumns() == null) {
                                question.setColumns(new ArrayList<>());
                            }
                            question.getColumns().clear();
                            question.getColumns().addAll(updatedColumns);
                        }

                        // Handle validation
                        if (questionDto.getValidation() != null) {
                            question.setValidation(convertValidationToEntity(questionDto.getValidation()));
                        }

                        updatedSection.getQuestions().add(question);
                    });
                }

                updatedSections.add(updatedSection);
            });

            // Clear and update the sections collection
            existing.getSections().clear();
            existing.getSections().addAll(updatedSections);
        }
    }
}
