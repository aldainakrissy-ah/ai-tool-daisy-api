package com.example.ai.tool.analysis.phase_three_api.controller;

import com.example.ai.tool.analysis.phase_three_api.entity.Client;
import com.example.ai.tool.analysis.phase_three_api.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/clients")
@Tag(name = "Client Data", description = "APIs for fetching client data from the second database across all schemas")
public class ClientController {

    @Autowired
    private ClientService clientService;

    @GetMapping
    @Operation(summary = "Get all clients from all schemas",
               description = "Retrieves a comprehensive list of all clients by searching through all available schemas in the second database for the 'clients' table and consolidating the results.",
               responses = {
                   @ApiResponse(responseCode = "200", description = "Successfully retrieved the list of clients"),
                   @ApiResponse(responseCode = "500", description = "Internal server error")
               })
    public ResponseEntity<List<Client>> getAllClients() {
        List<Client> clients = clientService.findAllClientsFromAllSchemas();
        return ResponseEntity.ok(clients);
    }
}
