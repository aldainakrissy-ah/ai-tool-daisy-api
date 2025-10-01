package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1Result;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.ChatService;
import lombok.AllArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;



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
    public ResponseEntity<Prompt1Result> readPdf(@RequestParam("file") MultipartFile file) {
        log.info("Received file: {}", file.getOriginalFilename());
        Prompt1Result responseFuture = chatService.generatePreIntakeAnalysis(file);
        return ResponseEntity.ok(responseFuture);
    }

//    @GetMapping("/results")
//    public ResponseEntity<List<Prompt1ResultEntity>> getPrompt1ResultByPatientId(@RequestParam("patientId") String patientId) {
//        List<Prompt1ResultEntity> result = chatService.getPrompt1ResultByPatientId(patientId);
//        if (result != null) {
//            return ResponseEntity.ok(result);
//        } else {
//            return ResponseEntity.notFound().build();
//        }
//    }
}

