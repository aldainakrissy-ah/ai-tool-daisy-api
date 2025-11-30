package com.example.ai.tool.analysis.phase_three_api.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientGidWithClientInfo {
    private Long id;
    private String professionalId;
    private Long clientId;
    private String email;
    private String firstName;
    private String familyName;
    private String clientGidId;
}
