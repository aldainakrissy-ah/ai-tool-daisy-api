package com.example.ai.tool.analysis.phase_three_api.controller;

import com.example.ai.tool.analysis.phase_three_api.service.ClientDataDumpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/client-dump")
@Tag(name = "Client Data Dump", description = "APIs for fetching a complete data dump for a specific client")
public class ClientDataDumpController {

    @Autowired
    private ClientDataDumpService clientDataDumpService;

    @GetMapping
    @Operation(summary = "Get a full data dump for a client by email",
            description = "Retrieves a comprehensive dump of all data related to a client from all relevant tables, identified by their email address.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved the client data dump"),
                    @ApiResponse(responseCode = "404", description = "Client not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            })
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getClientDataDump(@RequestParam String email) {
        Map<String, List<Map<String, Object>>> dataDump = clientDataDumpService.getClientDataDump(email);
        if (dataDump.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dataDump);
    }
}
