package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ClientDataException;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1NotReadyException;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

/**
 * The Prompt 1 run errors, in one place for the Prompt 1 run endpoints: not ready or a full run queue is a 503 with
 * what to do, client data that cannot start a run is its own 404 or 409. Limited to these controllers (other
 * controllers keep the global handling) and ordered first, so the global {@code RuntimeException} handler does not
 * turn these into 500s.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice(assignableTypes = {Prompt1ExperimentController.class, Prompt1RunController.class})
public class Prompt1ExceptionHandler {

    @ExceptionHandler({Prompt1NotReadyException.class, Prompt1RunService.QueueFullException.class})
    public ResponseEntity<Map<String, String>> notReady(RuntimeException e) {
        log.warn("{}", e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(Prompt1ClientDataException.class)
    public ResponseEntity<Map<String, String>> clientData(Prompt1ClientDataException e) {
        log.warn("Prompt 1 client run refused ({}): {}", e.getStatus().value(), e.getMessage());
        return body(e.getStatus(), e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", status.getReasonPhrase(), "message", message));
    }
}
