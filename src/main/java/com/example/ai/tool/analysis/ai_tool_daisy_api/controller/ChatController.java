package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.ChatService;
import lombok.AllArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * REST controller for handling chat-related endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/ai/tool/daisy/")
@AllArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Endpoint to upload and process a PDF file.
     *
     * @param file the uploaded PDF file as a {@link MultipartFile}.
     * @return a {@link ResponseEntity} containing the result of the processing or an error message.
     */
    @PostMapping("/read/pdf")
    public ResponseEntity<String> readPdf(@RequestParam("file") MultipartFile file) {
        log.info("Received file: {}", file.getOriginalFilename());
        try {
            CompletableFuture<String> responseFuture = chatService.generatePreIntakeAnalysis(file);
            String result = responseFuture.get();
            return ResponseEntity.ok(result);
        } catch (ExecutionException e) {
            log.error("Error during PDF analysis", e.getCause());
            return ResponseEntity.status(500).body("Internal server error: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Request interrupted", e);
            return ResponseEntity.status(503).body("Request interrupted");
        }
    }
}

