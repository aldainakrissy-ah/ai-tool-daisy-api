# JSON Schema Configuration Guide for OpenAI Response API

## Overview

This guide explains how to configure JSON Schema response formatting for the Daisy AI Analysis API using OpenAI's Response API.

## Important Note

**The OpenAI Response API does NOT support runtime `response_format` configuration through the Java SDK API calls.** The `response_format` must be configured at the **Prompt level** in the OpenAI Dashboard.

### 1. Access OpenAI Dashboard

1. Navigate to [OpenAI Platform Prompts](https://platform.openai.com/prompts)
2. Log in with your OpenAI account

### 2. Select Your Prompt

- Find and select your prompt:
  - **Prompt ID**: `pmpt_698f139913a4819386d1a71530ea241a0c676b2ba894ec52`
  - **Current Version**: `9`

### 3. Edit Prompt Settings

1. Click the **"Edit"** button on your prompt
2. Scroll to the **"Response Format"** section

### 4. Configure JSON Schema

Select **"JSON schema"** mode and configure:

- **Schema Name**: `daisy_doc13_generic_p1_p2_p3_A3`
- **Strict Mode**: ✅ Enabled (set to `true`)

Then paste the complete schema:

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": [
    "prompt_id",
    "client-name",
    "professional-name",
    "A1",
    "A2",
  - **Version**: `9`
    "A4a",
    "A4b",
        "A4c",
        "A4d",
        "A5",
        "A6",
        "B1",
        "B2",
        "B3"
      ],
      "properties": {
Select **"Structured Outputs"** or **"JSON Schema"** mode and enter:
        "A1": { "$ref": "#/$defs/a1_schema" },
        "A2": { "$ref": "#/$defs/a2_schema" },
  "type": "json_schema",
  "json_schema": {
    "name": "daisy_doc13_maxhard",
    "strict": true,
    "schema": {
      "type": "object",
      "additionalProperties": false,
      "required": [
        "prompt_id",
        "client-name",
        "professional-name",
        "A1",
        "A2",
        "A3",
        "A4a",
        "A4b",
        "A5": { "$ref": "#/$defs/a5_module" },
        "A6": { "$ref": "#/$defs/a6_module" },
        "B1": { "$ref": "#/$defs/b1_array" },
        "B2": { "$ref": "#/$defs/b2_array" },
        "B3": { "$ref": "#/$defs/b3_array" }
      },
      "$defs": {
        "clean_string": {
          "type": "string",
          "minLength": 0,
          "maxLength": 2000,
          "pattern": "^[^<>\\[\\]{}]*$"
        },
        "clean_string_nonempty": {
          "type": "string",
          "minLength": 1,
          "maxLength": 2000,
          "pattern": "^[^<>\\[\\]{}]*$"
        }
        // ... (additional schema definitions from QuestionnaireInstructions.java)
      }
    }
  }
}
```

**Note**: For the complete schema, refer to `QuestionnaireInstructions.DAISY_USER_MESSAGE` constant in your codebase starting at line 220.

### 5. Save the Prompt

Click **"Save"** or **"Update"** to apply the changes.

## How It Works

### API Call Flow

```
Java Application 
    ↓
OpenAI Response API (ResponseCreateParams)
    ↓
Prompt Configuration (with JSON Schema) [Configured in Dashboard]
    ↓
Structured JSON Response
    ↓
AiAnalysisResult Object
```

### Code Implementation

The `buildResponseParams` method in `QuestionnaireAnalysisService.java`:

```java
private ResponseCreateParams buildResponseParams(String content, String promptType) {
    ResponsePrompt prompt = ResponsePrompt.builder()
            .id(PROMPT_ID)          // References your configured prompt
            .version(PROMPT_VERSION) // Uses version 9
            .build();

    FileSearchTool fileSearchTool = FileSearchTool.builder()
            .addVectorStoreId(VECTOR_STORE_ID)
            .build();

    return ResponseCreateParams.builder()
            .temperature(0.0)
            .topP(1.0)
            .prompt(prompt)  // Prompt contains the JSON schema configuration
            .tools(Collections.singletonList(Tool.ofFileSearch(fileSearchTool)))
            .store(true)
            .maxOutputTokens(6000)
            .include(Collections.singletonList(ResponseIncludable.FILE_SEARCH_CALL_RESULTS))
            .input(promptType + ":\n\n" + content)
            .build();
}
```

## Benefits of JSON Schema Validation

1. **Guaranteed Structure**: OpenAI will always return responses matching your schema
2. **Type Safety**: Fields are validated for correct types (string, number, array, etc.)
3. **Required Fields**: Ensures all required fields are present
4. **Pattern Validation**: String patterns are enforced (e.g., clean_string pattern)
5. **Array Constraints**: Min/max items are enforced
6. **Error Reduction**: Malformed responses are prevented at the API level

## Testing the Configuration

### 1. Send a Test Request

Use your existing API endpoint:

```bash
POST http://localhost:8081/api/v1/ai-tool-daisy/analyze
Content-Type: multipart/form-data

file: [your-pdf-file.pdf]
promptType: Prompt_1
```

### 2. Verify Response Structure

The response should strictly match the JSON schema:

```json
{
  "prompt_id": "Prompt_1",
  "client-name": "John Doe",
  "professional-name": "Dr. Smith",
  "A1": {
    "primary_ie": "...",
    "secondary_ie": [],
    "teleonic_pattern": "...",
    "secondary_dynamics": [],
    "uncertainty_level": 0.2,
    "uncertainty_label": "Laag"
  },
  // ... other fields
}
```

### 3. Error Handling

If OpenAI cannot generate a response matching the schema:
- The API will return an error
- Check your schema configuration
- Ensure the schema isn't too restrictive for the AI model

## Troubleshooting

### Issue: Schema Validation Errors

**Symptom**: OpenAI returns errors about invalid schema

**Solutions**:
1. Validate your JSON schema using [JSON Schema Validator](https://www.jsonschemavalidator.net/)
2. Ensure all `$ref` references are correctly defined in `$defs`
3. Check that enum values match exactly (case-sensitive)

### Issue: Responses Don't Match Schema

**Symptom**: Responses are not structured as expected

**Solutions**:
1. Verify the schema is saved in the OpenAI Dashboard
2. Confirm you're using the correct prompt ID and version
3. Check that "Structured Outputs" or "JSON Schema" is enabled

### Issue: API Timeout

**Symptom**: Requests take too long or timeout

**Solutions**:
1. Schema validation adds processing time
2. Consider increasing `maxOutputTokens` if responses are incomplete
3. Simplify the schema if it's too complex

## Alternative: Chat Completions API

If you need more flexibility with runtime schema configuration, consider using the **Chat Completions API** instead of Response API:

```java
ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
    .model("gpt-4o-2024-08-06")  // Supports structured outputs
    .responseFormat(ResponseFormat.jsonSchema(
        ResponseFormat.JsonSchema.builder()
            .name("daisy_doc13_maxhard")
            .strict(true)
            .schema(schema)  // Pass schema as Map<String, Object>
            .build()
    ))
    .messages(...)
    .build();
```

However, this would require refactoring your code to use Chat Completions instead of Response API.

## Schema Reference

The complete JSON schema is defined in:
- **File**: `QuestionnaireInstructions.java`
- **Constant**: `DAISY_USER_MESSAGE`
- **Lines**: 220-768

## Additional Resources

- [OpenAI Structured Outputs Guide](https://platform.openai.com/docs/guides/structured-outputs)
- [JSON Schema Documentation](https://json-schema.org/)
- [OpenAI Response API Reference](https://platform.openai.com/docs/api-reference/responses)
- [OpenAI Java SDK Documentation](https://github.com/openai/openai-java)

## Support

For questions or issues:
1. Check OpenAI Dashboard configuration
2. Review API logs in `QuestionnaireAnalysisService`
3. Verify schema matches your POJO structure (`AiAnalysisResult.java`)
4. Test with simplified schema first, then add complexity

---

**Last Updated**: February 20, 2026

