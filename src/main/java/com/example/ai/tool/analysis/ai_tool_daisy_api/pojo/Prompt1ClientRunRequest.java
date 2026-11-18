package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Starts Prompt 1 for a client from the professional app: IDs and names only, the client files are built from our
 * own databases. {@code clientName} is the name the result screen looks the result up by (first and family name).
 */
@Data
@NoArgsConstructor
public class Prompt1ClientRunRequest {
    /** The portal client ID; the intake answers are saved under it. */
    private String clientId;
    /** The portal email; the personal data is looked up by it. */
    private String clientEmail;
    private String clientName;
    private String professionalId;
    private String professionalName;
    /** Optional model-and-effort plan: quality, standard or economy. */
    private String preset;
}
