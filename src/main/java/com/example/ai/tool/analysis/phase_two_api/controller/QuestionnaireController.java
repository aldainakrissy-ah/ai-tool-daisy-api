package com.example.ai.tool.analysis.phase_two_api.controller;

import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionnaireDto;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireService;
import com.example.ai.tool.analysis.phase_two_api.service.QuestionnaireLoaderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/questionnaires")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class QuestionnaireController {
    
    private final QuestionnaireService questionnaireService;
    private final QuestionnaireLoaderService questionnaireLoaderService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @GetMapping
    public ResponseEntity<List<QuestionnaireDto>> getAllActiveQuestionnaires() {
        try {
            List<QuestionnaireDto> questionnaires = questionnaireService.getAllActiveQuestionnaires();
            return ResponseEntity.ok(questionnaires);
        } catch (Exception e) {
            log.error("Error retrieving questionnaires", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<QuestionnaireDto> getQuestionnaire(@PathVariable String id) {
        try {
            return questionnaireService.getQuestionnaireById(id)
                    .map(questionnaire -> ResponseEntity.ok(questionnaire))
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error retrieving questionnaire: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/create")
    public ResponseEntity<?> createQuestionnaire(@RequestBody QuestionnaireDto questionnaireDto) {
        try {
            QuestionnaireDto createdQuestionnaire = questionnaireService.createQuestionnaire(questionnaireDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdQuestionnaire);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating questionnaire", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateQuestionnaire(
            @PathVariable String id,
            @RequestBody QuestionnaireDto questionnaireDto) {
        try {
            QuestionnaireDto updatedQuestionnaire = questionnaireService.updateQuestionnaire(id, questionnaireDto);
            return ResponseEntity.ok(updatedQuestionnaire);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating questionnaire: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{id}/upload")
    public ResponseEntity<?> updateQuestionnaireWithUpload(
            @PathVariable String id,
            @RequestBody JsonNode jsonNode) {
        try {
            // Parse the JSON directly
            QuestionnaireDto questionnaireDto = questionnaireLoaderService.parseJsonToQuestionnaireDto(jsonNode);
            
            // Update the questionnaire
            QuestionnaireDto updatedQuestionnaire = questionnaireService.updateQuestionnaire(id, questionnaireDto);
            return ResponseEntity.ok(updatedQuestionnaire);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating questionnaire with upload: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing questionnaire");
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuestionnaire(@PathVariable String id) {
        try {
            questionnaireService.deleteQuestionnaire(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting questionnaire: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/load-json")
    public ResponseEntity<?> loadQuestionnaireFromJson(@RequestParam String fileName) {
        try {
            questionnaireLoaderService.loadQuestionnaireFromJson(fileName);
            return ResponseEntity.ok("Questionnaire loaded successfully");
        } catch (IOException e) {
            log.error("Error loading questionnaire: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error loading questionnaire: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
