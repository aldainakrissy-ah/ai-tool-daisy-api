package com.example.ai.tool.analysis.ai_tool_daisy_api.constant;

public final class QuestionnaireInstructions {

    public static final String PROMPT1_INSTRUCTION = "Run DAISY Prompt 1 on the attached intake PDF.\n" +
            "\n" +
            "TASK:\n" +
            "• Extract intake and demographic data.\n" +
            "• Produce ONE TPD hypothesis.\n" +
            "• Map up to THREE primary fields (FIELD-LIMIT = 3).\n" +
            "• Report ONLY concretely triggered clusters.\n" +
            "• Use validated HETA mappings (HETA names only).\n" +
            "• Apply teleonic rules: TQRP → TCG → LST → IFMP → ZZCP.\n" +
            "• Use ONLY Daisy canon sources available in the vector store.\n" +
            "• Follow the Prompt1Result JSON schema format for A1, A2, A3, B1, B2, B3.\n" +
            "• Output JSON ONLY.\n" +
            "\n" +
            "A1 REQUIREMENTS (Integrative Endoteleon Determination):\n" +
            "• Select exactly 1 primary IE and 2 secondary IE’s.\n" +
            "• IE’s must appear in Appendix 22 (no aliases, no invented IE’s).\n" +
            "• Provide a short narrative and source list.\n" +
            "\n" +
            "A2 REQUIREMENTS (Teleonic Domain Cluster Projection):\n" +
            "• Derive clusters STRICTLY from A1.\n" +
            "• No external domains, no reinterpretation.\n" +
            "• Provide cluster name + description (optional: linked_ie).\n" +
            "\n" +
            "A3 REQUIREMENTS (HETA Coverage Report):\n" +
            "• Use validated HETA list only.\n" +
            "• Output two arrays: A3.direct (High Tier), A3.indirect (Medium Tier).\n" +
            "• No inferred HETA’s, no spray names.\n" +
            "\n" +
            "B1 REQUIREMENTS (Ethos Variant):\n" +
            "• Select 1 Ethos variant and give rationale.\n" +
            "\n" +
            "B2 REQUIREMENTS (Additional Questionnaires):\n" +
            "• Select ONLY questionnaires from the canon list.\n" +
            "• Match ONLY against A3.indirect.\n" +
            "• Provide id, name, rationale.\n" +
            "\n" +
            "B3 REQUIREMENTS (Modular HETA Questionnaires):\n" +
            "• Select ONLY canon HETA modules.\n" +
            "• Match ONLY against A3.direct.\n" +
            "• Provide heta_name, module_id, rationale.\n" +
            "\n" +
            "RESTRICTIONS:\n" +
            "• Do NOT quote or reproduce raw text from the PDF.\n" +
            "• Do NOT output anything outside JSON.\n" +
            "• Do NOT use non-canon files or create missing elements.\n" +
            "• Do NOT ask for confirmation or follow-up questions.\n" +
            "\n" +
            "FINAL OUTPUT:\n" +
            "Return ONLY a valid Prompt1Result JSON object:\n" +
            "A1, A2, A3, B1, B2, B3.\n";


    public static final String PROMPT1_DAISY = """
Run DAISY Prompt 1 on the attached intake PDF.

TASK
• Extract intake and demographic data.
• Identify the client and the responsible professional.
• Produce ONE TPD hypothesis.
• Map up to THREE primary fields (IE’s).
• Report ONLY concretely triggered clusters using validated HETA mappings (HETA names only).
• Apply teleonic rules: TQRP → TCG → LST → IFMP → ZZCP.
• Use only DAISY/Daisy canon knowledge, no external sources.
• Output MUST be a single JSON object matching the Prompt1Result structure below.

OUTPUT FORMAT (Prompt1Result)
Return EXACTLY one JSON object with the following fields:

- "client-name": string  
- "professional-name": string  

- "A1": object  
  {
    "primary_IE": string,
    "secondary_IEs": [string, ...],
    "uncertainty": string,
    "narrative": string
  }

- "A2": array of objects  
  [
    {
      "teleonic_cluster": string,
      "derived_from_IE": string,
      "projection": string
    }
  ]

- "A3": object  
  {
    "direct": [
      { "id": string, "label": string, "json_ref": string }
    ],
    "indirect": [
      { "id": string, "label": string, "json_ref": string }
    ],
    "narrative": string
  }

- "A4a": object
- "A4b": object
- "A4c": object
- "A4d": object
- "A5": object
- "A6": object

- "B1": array of objects

- "B2": array of objects  
  [
    { "id": string, "label": string, "json_ref": string }
  ]

- "B3": array of objects  
  [
    { "id": string, "label": string, "json_ref": string }
  ]

RESTRICTIONS
• Do NOT quote or reproduce raw text from the PDF.
• Do NOT invent non-canon IE’s, HETA’s, or questionnaire IDs.
• Do NOT output anything outside the single JSON object described above.
• If a section has no data, include {} or [] according to the schema.
""";
}
