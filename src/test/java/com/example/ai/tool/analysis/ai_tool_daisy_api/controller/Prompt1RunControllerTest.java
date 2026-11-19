package com.example.ai.tool.analysis.ai_tool_daisy_api.controller;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ClientRunRequest;
import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1RunDto;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1IntakeBuilder;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService.Client;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1RunService.ClientSubmit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Starting Prompt 1 for a client: required and optional fields, as the professional app and the intake app send them. */
@ExtendWith(MockitoExtension.class)
class Prompt1RunControllerTest {

    @Mock
    private Prompt1RunService runService;
    @Mock
    private Prompt1IntakeBuilder intakeBuilder;
    @InjectMocks
    private Prompt1RunController controller;

    private static Prompt1ClientRunRequest request(String clientId, String email, String clientName, String professionalName) {
        Prompt1ClientRunRequest request = new Prompt1ClientRunRequest();
        request.setClientId(clientId);
        request.setClientEmail(email);
        request.setClientName(clientName);
        request.setProfessionalName(professionalName);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Client started(Prompt1ClientRunRequest request, boolean existing) {
        when(runService.submitForClient(any(), any(), any()))
                .thenReturn(new ClientSubmit(Prompt1RunDto.builder().runId("r-1").status("QUEUED").build(), existing));
        ResponseEntity<Prompt1RunDto> response = controller.start(request);
        assertEquals(existing ? HttpStatus.OK : HttpStatus.ACCEPTED, response.getStatusCode());
        ArgumentCaptor<Client> client = ArgumentCaptor.forClass(Client.class);
        ArgumentCaptor<Supplier<Prompt1IntakeBuilder.ClientFiles>> intake = ArgumentCaptor.forClass(Supplier.class);
        verify(runService).submitForClient(client.capture(), any(), intake.capture());
        intake.getValue().get();  // the files are built with the request's ID, email and (optional) name
        return client.getValue();
    }

    @Test
    void theIntakeAppStartsWithIdAndEmailOnly() {
        Client client = started(request(" c-1 ", "client@example.org", null, null), false);

        assertEquals("c-1", client.clientId());
        assertNull(client.clientName());  // the builder takes the portal name
        assertEquals(Prompt1RunController.NO_PROFESSIONAL, client.professionalName());
        verify(intakeBuilder).build("c-1", "client@example.org", null);
    }

    @Test
    void theProfessionalAppSendsNamesThatAreKept() {
        Client client = started(request("c-1", "client@example.org", " Test Client ", "Prof"), true);

        assertEquals("Test Client", client.clientName());
        assertEquals("Prof", client.professionalName());
        verify(intakeBuilder).build("c-1", "client@example.org", "Test Client");
    }

    @Test
    void idAndEmailAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> controller.start(request(null, "client@example.org", null, null)));
        assertThrows(IllegalArgumentException.class, () -> controller.start(request("c-1", " ", null, null)));
        verifyNoInteractions(runService, intakeBuilder);
    }
}
