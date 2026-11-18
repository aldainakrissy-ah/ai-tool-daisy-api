package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ClientRunRequest;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1RunDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1IntakeBuilder;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService.Client;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService.ClientSubmit;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Prompt 1 for a client on the turn-based route, started by the professional app with IDs instead of a PDF: the
 * client files are built from our own databases (the latest MyHealth Intake (Adult) and the portal's personal data),
 * the run goes to the background, and a VALID result is also saved where the existing result screen reads it
 * (prompt1_results, by the client's name). The answers hold no client text, only the run and its status. Errors:
 * {@link Prompt1ExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/ai-tool-daisy/analyze/prompt1/runs")
@AllArgsConstructor
@CrossOrigin(origins = "*")
public class Prompt1RunController {

    private final Prompt1RunService runService;
    private final Prompt1IntakeBuilder intakeBuilder;

    /**
     * 202 with the queued run; 200 with the client's QUEUED or RUNNING run when there is one (no second run);
     * 400 when a field is missing; 404/409 when the client data cannot be used; 503 when Prompt 1 is not ready.
     */
    @PostMapping
    public ResponseEntity<Prompt1RunDto> start(@RequestBody Prompt1ClientRunRequest request) {
        String clientId = required(request.getClientId(), "clientId");
        String email = required(request.getClientEmail(), "clientEmail");
        String clientName = required(request.getClientName(), "clientName");
        Client client = new Client(clientId, clientName, request.getProfessionalId(),
                required(request.getProfessionalName(), "professionalName"));
        ClientSubmit submitted = runService.submitForClient(client, request.getPreset(),
                () -> intakeBuilder.build(clientId, email, clientName));
        return submitted.existing() ? ResponseEntity.ok(submitted.run())
                : ResponseEntity.accepted().body(submitted.run());
    }

    /** One run: its status, current phase, release and error, without client text (the app reads the result in prompt1_results). */
    @GetMapping("/{runId}")
    public ResponseEntity<Prompt1RunDto> get(@PathVariable String runId) {
        return ResponseEntity.of(runService.status(runId));
    }

    /** A client's or a professional's runs, newest first, without results. */
    @GetMapping
    public ResponseEntity<List<Prompt1RunDto>> list(@RequestParam(required = false) String clientId,
                                                    @RequestParam(required = false) String professionalName,
                                                    @RequestParam(defaultValue = "20") int limit) {
        if (clientId != null && !clientId.isBlank()) {
            return ResponseEntity.ok(runService.listForClient(clientId.strip(), limit));
        }
        return ResponseEntity.ok(runService.listForProfessional(
                required(professionalName, "clientId or professionalName"), limit));
    }

    /** The value without surrounding spaces; a 400 (through the global handler) when it is missing. */
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }
}
