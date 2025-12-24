package com.example.ai.tool.analysis.phase_two_api.service;

import com.example.ai.tool.analysis.phase_two_api.entity.*;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireResponseDto;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionRepository;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireRepository;
import com.example.ai.tool.analysis.phase_two_api.repository.QuestionnaireResponseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireResponseService {

    private final QuestionnaireResponseRepository responseRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuestionnaireResponseDto startQuestionnaire(String questionnaireId, String userId, String languageCode) {
        Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found: " + questionnaireId));

        QuestionnaireResponse response = new QuestionnaireResponse();
        response.setQuestionnaire(questionnaire);
        response.setUserId(userId);
        response.setSessionId(UUID.randomUUID().toString());
        response.setLanguageCode(languageCode != null ? languageCode : "en");
        response.setStatus(QuestionnaireResponse.ResponseStatus.IN_PROGRESS);
        response.setStartedAt(LocalDateTime.now());

        QuestionnaireResponse savedResponse = responseRepository.save(response);
        return convertToDto(savedResponse);
    }

    @Transactional
    public QuestionnaireResponseDto saveQuestionResponse(String sessionId, String clientId,
            String questionnaireId, List<QuestionResponseDto> responses) {

        Optional<QuestionnaireResponse> existingResponse = responseRepository.findBySessionId(sessionId);
        QuestionnaireResponse questionnaireResponse;

        if (existingResponse.isPresent()) {
            // Update existing session
            questionnaireResponse = existingResponse.get();
            log.info("Found existing questionnaire session: {}", sessionId);

            // Update client ID if provided (using clientId for both client and user)
            if (clientId != null && !clientId.trim().isEmpty()) {
                questionnaireResponse.setClientId(clientId);
                questionnaireResponse.setUserId(clientId); // Use same value for userId
            }

            // Clear existing responses for this session
            if (questionnaireResponse.getQuestionResponses() != null) {
                questionnaireResponse.getQuestionResponses().clear();
            } else {
                questionnaireResponse.setQuestionResponses(new ArrayList<>());
            }
        } else {
            // Create new session if not found
            log.info("Session {} not found. Creating new questionnaire response.", sessionId);

            questionnaireResponse = new QuestionnaireResponse();
            questionnaireResponse.setSessionId(sessionId);
            questionnaireResponse.setClientId(clientId);
            questionnaireResponse.setUserId(clientId); // Use same value for userId
            questionnaireResponse.setLanguageCode("en"); // default language
            questionnaireResponse.setStatus(QuestionnaireResponse.ResponseStatus.IN_PROGRESS);
            questionnaireResponse.setStartedAt(LocalDateTime.now());

            // Set questionnaire if provided
            if (questionnaireId != null && !questionnaireId.trim().isEmpty()) {
                Questionnaire questionnaire = questionnaireRepository.findById(questionnaireId)
                        .orElseThrow(() -> new IllegalArgumentException("Questionnaire not found: " + questionnaireId));
                questionnaireResponse.setQuestionnaire(questionnaire);
                log.info("Associated new session {} with questionnaire: {}", sessionId, questionnaireId);
            } else {
                log.warn(
                        "No questionnaire ID provided for new session: {}. Consider providing questionnaireId in the request.",
                        sessionId);
            }
        }

        // Ensure questionResponses list is initialized
        if (questionnaireResponse.getQuestionResponses() == null) {
            questionnaireResponse.setQuestionResponses(new ArrayList<>());
        }

        // Add new responses
        for (QuestionResponseDto responseDto : responses) {
            Question question = questionRepository.findById(responseDto.getQuestionId())
                    .orElseThrow(
                            () -> new IllegalArgumentException("Question not found: " + responseDto.getQuestionId()));

            QuestionResponse questionResponse = new QuestionResponse();
            questionResponse.setQuestionnaireResponse(questionnaireResponse);
            questionResponse.setQuestion(question);
            questionResponse.setAnswerText(responseDto.getAnswerText());
            questionResponse.setAnswerNumber(responseDto.getAnswerNumber());
            questionResponse.setAnswerBoolean(responseDto.getAnswerBoolean());

            if (responseDto.getAnswerJson() != null) {
                try {
                    questionResponse.setAnswerJson(objectMapper.writeValueAsString(responseDto.getAnswerJson()));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize answer JSON for question {}: {}", responseDto.getQuestionId(),
                            responseDto.getAnswerJson(), e);
                }
            }

            questionnaireResponse.getQuestionResponses().add(questionResponse);
        }

        QuestionnaireResponse savedResponse = responseRepository.save(questionnaireResponse);
        return convertToDto(savedResponse);
    }

    @Transactional
    public QuestionnaireResponseDto completeQuestionnaire(String sessionId) {
        QuestionnaireResponse response = responseRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Questionnaire session not found: " + sessionId));

        response.setStatus(QuestionnaireResponse.ResponseStatus.COMPLETED);
        response.setCompletedAt(LocalDateTime.now());

        QuestionnaireResponse savedResponse = responseRepository.save(response);
        return convertToDto(savedResponse);
    }

    @Transactional(readOnly = true)
    public Optional<QuestionnaireResponseDto> getQuestionnaireResponse(String sessionId) {
        return responseRepository.findBySessionIdWithResponses(sessionId)
                .map(this::convertToDto);
    }

    @Transactional(readOnly = true)
    public List<QuestionnaireResponseDto> getUserResponses(String userId) {
        List<QuestionnaireResponse> responses = responseRepository.findByUserId(userId);
        return responses.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuestionnaireResponseDto> getUserResponsesByQuestionnaire(String questionnaireId, String userId) {
        List<QuestionnaireResponse> responses = responseRepository.findByQuestionnaireIdAndUserId(questionnaireId,
                userId);
        return responses.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void abandonQuestionnaire(String sessionId) {
        QuestionnaireResponse response = responseRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Questionnaire session not found: " + sessionId));

        response.setStatus(QuestionnaireResponse.ResponseStatus.ABANDONED);
        responseRepository.save(response);
    }

    private QuestionnaireResponseDto convertToDto(QuestionnaireResponse response) {
        QuestionnaireResponseDto dto = new QuestionnaireResponseDto();
        dto.setId(response.getId());
        dto.setQuestionnaireId(response.getQuestionnaire() != null ? response.getQuestionnaire().getId() : null);
        dto.setUserId(response.getUserId());
        dto.setClientId(response.getClientId());
        dto.setSessionId(response.getSessionId());
        dto.setStatus(response.getStatus());
        dto.setStartedAt(response.getStartedAt());
        dto.setCompletedAt(response.getCompletedAt());
        dto.setLanguageCode(response.getLanguageCode());

        if (response.getQuestionResponses() != null) {
            dto.setQuestionResponses(response.getQuestionResponses().stream()
                    .map(this::convertQuestionResponseToDto)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private QuestionResponseDto convertQuestionResponseToDto(QuestionResponse response) {
        QuestionResponseDto dto = new QuestionResponseDto();
        dto.setId(response.getId());
        dto.setQuestionId(response.getQuestion().getId());
        dto.setAnswerText(response.getAnswerText());
        dto.setAnswerNumber(response.getAnswerNumber());
        dto.setAnswerBoolean(response.getAnswerBoolean());

        if (response.getAnswerJson() != null) {
            try {
                dto.setAnswerJson(objectMapper.readValue(response.getAnswerJson(), Object.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize answer JSON for question {}: {}", response.getQuestion().getId(),
                        response.getAnswerJson(), e);
                dto.setAnswerJson(response.getAnswerJson());
            }
        }

        return dto;
    }
}
