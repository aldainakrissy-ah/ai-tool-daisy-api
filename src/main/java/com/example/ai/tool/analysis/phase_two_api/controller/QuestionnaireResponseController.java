package com.example.ai.tool.analysis.phase_two_api.controller;

import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireResponseDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.SaveQuestionnaireResponseRequest;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/questionnaire-responses")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class QuestionnaireResponseController {

    private final QuestionnaireResponseService responseService;

    @PostMapping("/start")
    public ResponseEntity<?> startQuestionnaire(
            @RequestParam String questionnaireId,
            @RequestParam String userId,
            @RequestParam(required = false, defaultValue = "en") String languageCode) {
        try {
            QuestionnaireResponseDto response = responseService.startQuestionnaire(questionnaireId, userId,
                    languageCode);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error starting questionnaire", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{sessionId}/responses")
    public ResponseEntity<?> saveQuestionResponses(
            @PathVariable String sessionId,
            @RequestBody SaveQuestionnaireResponseRequest request) {
        try {
            QuestionnaireResponseDto updatedResponse = responseService.saveQuestionResponse(
                    sessionId,
                    request.getClientId(),
                    request.getQuestionnaireId(),
                    request.getResponses());
            return ResponseEntity.ok(updatedResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error saving question responses for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<?> completeQuestionnaire(@PathVariable String sessionId) {
        try {
            QuestionnaireResponseDto completedResponse = responseService.completeQuestionnaire(sessionId);
            return ResponseEntity.ok(completedResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error completing questionnaire for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{sessionId}/abandon")
    public ResponseEntity<?> abandonQuestionnaire(@PathVariable String sessionId) {
        try {
            responseService.abandonQuestionnaire(sessionId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error abandoning questionnaire for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getQuestionnaireResponse(@PathVariable String sessionId) {
        try {
            return responseService.getQuestionnaireResponse(sessionId)
                    .map(response -> ResponseEntity.ok(response))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving questionnaire response for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserResponses(@PathVariable String userId) {
        try {
            List<QuestionnaireResponseDto> responses = responseService.getUserResponses(userId);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error retrieving user responses for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user/{userId}/questionnaire/{questionnaireId}")
    public ResponseEntity<?> getUserResponsesByQuestionnaire(
            @PathVariable String userId,
            @PathVariable String questionnaireId) {
        try {
            List<QuestionnaireResponseDto> responses = responseService.getUserResponsesByQuestionnaire(questionnaireId,
                    userId);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error retrieving user responses for user: {} and questionnaire: {}", userId, questionnaireId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
