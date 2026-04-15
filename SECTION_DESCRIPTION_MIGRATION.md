# Section Description Field Migration Guide

## Overview
This migration adds support for section-level descriptions in questionnaires. The description field allows each section to have localized instructional text.

## Changes Made

### 1. Database Schema Changes
Two new columns have been added to the `sections` table:
- `description_en` (TEXT) - English description
- `description_nl` (TEXT) - Dutch description

**Migration SQL (for reference only - Hibernate will auto-update):**
```sql
ALTER TABLE sections 
ADD COLUMN description_en TEXT,
ADD COLUMN description_nl TEXT;
```

### 2. Backend Code Changes

#### SectionDto.java
Added description field:
```java
private LocalizedTextDto description;
```

#### Section.java (Entity)
Added embedded description field with database mappings:
```java
@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "en", column = @Column(name = "description_en", columnDefinition = "text")),
    @AttributeOverride(name = "nl", column = @Column(name = "description_nl", columnDefinition = "text"))
})
private LocalizedText description;
```

#### QuestionnaireLoaderService.java
Updated `parseSectionDto()` to parse description from JSON:
```java
JsonNode descriptionNode = sectionNode.get("description");
if (descriptionNode != null) {
    dto.setDescription(parseLocalizedTextDto(descriptionNode));
}
```

#### QuestionnaireService.java
Updated both conversion methods:
- `convertSectionToEntity()` - Now sets description when converting DTO to entity
- `convertSectionToDto()` - Now includes description when converting entity to DTO

### 3. JSON Questionnaire Updates

#### ethos-scan-adult.json
Already contains descriptions for:
- **statements** section - Instructions for reading statements
- **ethos-plus-scan** section - Objective and instructions for reflection questions

#### ethos-scan-hybrid.json
Added descriptions to:
- **ethos-plus-scan-client** section
- **ethos-plus-scan-parent** section

## Deployment Notes

### Automatic Schema Update
Since the application uses `spring.jpa.hibernate.ddl-auto=update`, the database schema will be automatically updated when the application starts. No manual migration is needed.

### Questionnaire Reloading
After deployment, existing questionnaires in the database should be reloaded to include the new descriptions:
1. Delete existing questionnaires from the database, or
2. Restart the application with `LOAD_QUESTIONNAIRES_ON_STARTUP=true`

## Testing

### Verify Database Schema
```sql
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'sections' 
  AND column_name IN ('description_en', 'description_nl');
```

### Verify JSON Parsing
Check application logs for successful questionnaire loading:
```
Successfully loaded questionnaire: ethos-scan-adult
Successfully loaded questionnaire: ethos-scan-hybrid
```

### API Testing
Retrieve a questionnaire and verify section descriptions are included:
```bash
curl -X GET http://localhost:8080/api/v1/questionnaires/ethos-scan-adult
```

Expected response should include description fields in sections:
```json
{
  "sections": [
    {
      "id": "statements",
      "title": {
        "en": "Statements",
        "nl": "Stellingen"
      },
      "description": {
        "en": "Read each statement carefully and indicate to what extent you agree:",
        "nl": "Lees elke stelling zorgvuldig en geef aan in hoeverre je het ermee eens bent:"
      },
      "questions": [...]
    }
  ]
}
```

## Rollback Plan

If rollback is needed:

1. **Code Rollback**: Revert the changes in git
2. **Database Rollback**: The columns can remain in the database (they will simply be unused) or can be dropped:
```sql
ALTER TABLE sections 
DROP COLUMN IF EXISTS description_en,
DROP COLUMN IF EXISTS description_nl;
```

## Related Files

- `/src/main/java/com/example/ai/tool/analysis/phase_two_api/pojo/SectionDto.java`
- `/src/main/java/com/example/ai/tool/analysis/phase_two_api/entity/Section.java`
- `/src/main/java/com/example/ai/tool/analysis/phase_two_api/service/QuestionnaireLoaderService.java`
- `/src/main/java/com/example/ai/tool/analysis/phase_two_api/service/QuestionnaireService.java`
- `/src/main/resources/questionnaires/ethos-scan-adult.json`
- `/src/main/resources/questionnaires/ethos-scan-hybrid.json`

## Date
Created: April 13, 2026

