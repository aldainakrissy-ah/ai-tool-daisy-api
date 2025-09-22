package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.ChatService;
import lombok.AllArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * REST controller for handling chat-related endpoints.
 */
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
    public ResponseEntity<String> readPdf(@RequestParam("file") MultipartFile file) throws IOException {
            String result = String.valueOf(chatService.generatePreIntakeAnalysis(file));
            return ResponseEntity.ok(result);

    }
}

