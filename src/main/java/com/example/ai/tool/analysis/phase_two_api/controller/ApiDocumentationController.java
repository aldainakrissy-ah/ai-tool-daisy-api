package com.example.ai.tool.analysis.phase_two_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ApiDocumentationController {
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Questionnaire Engine API");
        health.put("version", "1.0.0");
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/endpoints")
    public ResponseEntity<Map<String, Object>> getApiEndpoints() {
        Map<String, Object> endpoints = new HashMap<>();
        
        // Questionnaire endpoints
        Map<String, String> questionnaireEndpoints = new HashMap<>();
        questionnaireEndpoints.put("GET /api/v1/questionnaires", "Get all active questionnaires");
        questionnaireEndpoints.put("GET /api/v1/questionnaires/{id}", "Get questionnaire by ID");
        questionnaireEndpoints.put("POST /api/v1/questionnaires", "Create new questionnaire");
        questionnaireEndpoints.put("PUT /api/v1/questionnaires/{id}", "Update questionnaire");
        questionnaireEndpoints.put("DELETE /api/v1/questionnaires/{id}", "Delete questionnaire");
        questionnaireEndpoints.put("POST /api/v1/questionnaires/load/{fileName}", "Load questionnaire from JSON file");
        questionnaireEndpoints.put("POST /api/v1/questionnaires/load-all", "Load all questionnaires from JSON files");
        
        // Response endpoints
        Map<String, String> responseEndpoints = new HashMap<>();
        responseEndpoints.put("POST /api/v1/questionnaire-responses/start", "Start questionnaire session");
        responseEndpoints.put("POST /api/v1/questionnaire-responses/{sessionId}/responses", "Save question responses");
        responseEndpoints.put("POST /api/v1/questionnaire-responses/{sessionId}/complete", "Complete questionnaire");
        responseEndpoints.put("POST /api/v1/questionnaire-responses/{sessionId}/abandon", "Abandon questionnaire");
        responseEndpoints.put("GET /api/v1/questionnaire-responses/{sessionId}", "Get questionnaire response by session");
        responseEndpoints.put("GET /api/v1/questionnaire-responses/user/{userId}", "Get all user responses");
        responseEndpoints.put("GET /api/v1/questionnaire-responses/user/{userId}/questionnaire/{questionnaireId}", "Get user responses for specific questionnaire");
        
        endpoints.put("questionnaires", questionnaireEndpoints);
        endpoints.put("responses", responseEndpoints);
        endpoints.put("health", Map.of("GET /api/v1/health", "API health check"));
        
        return ResponseEntity.ok(endpoints);
    }
}
