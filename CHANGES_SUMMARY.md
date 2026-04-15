# Summary of Changes for Ethos Scan Adult Questionnaire

## Date: April 13, 2026

## Overview
Updated the questionnaire system to support the new "ethos-scan-adult-new" questionnaire with enhanced fields and fixed several bugs.

## Changes Made

### 1. Database Schema Changes

#### Option Entity (`Option.java`)
- **Changed**: Column name from `` `group` `` to `option_group` to avoid PostgreSQL reserved keyword issues
- **Reason**: PostgreSQL treats "group" as a reserved keyword, causing schema creation errors

#### Question Entity (`Question.java`)
- **Added**: `number` field (varchar(50)) - stores question numbering like "A1", "B2", etc.
- **Added**: `hint` field (LocalizedText) - provides additional context/hints for questions
- **Added**: `editEnabled` field (Boolean) - controls whether users can edit their responses

#### Section Entity (`Section.java`)
- **Added**: `description` field (LocalizedText) - provides section-level descriptions

### 2. DTO Changes

#### QuestionDto (`QuestionDto.java`)
- **Added**: `String number`
- **Added**: `LocalizedTextDto hint`
- **Added**: `Boolean editEnabled`

#### SectionDto (`SectionDto.java`)
- **Added**: `LocalizedTextDto description`

### 3. Service Layer Changes

#### QuestionnaireLoaderService (`QuestionnaireLoaderService.java`)
- **Updated** `parseSectionDto()`: Now parses the `description` field from JSON
- **Updated** `parseQuestionDto()`: Now parses `number`, `hint`, and `editEnabled` fields from JSON

#### QuestionnaireService (`QuestionnaireService.java`)
- **Fixed Bug**: In `convertOptionToDto()`, changed condition from `if(dto.getGroup() != null)` to `if(option.getGroup() != null)` - this was preventing the group field from being returned in API responses
- **Updated** `convertSectionToDto()`: Now includes `description` in the response
- **Updated** `convertQuestionToDto()`: Now includes `number`, `hint`, and `editEnabled` in the response
- **Updated** `convertSectionToEntity()`: Now saves `description` to database
- **Updated** `convertQuestionToEntity()`: Now saves `number`, `hint`, and `editEnabled` to database
- **Updated** `updateQuestionnaireFields()`: Now updates all new fields when updating existing questionnaires

### 4. JSON File Changes

#### ethos-scan-adult-new.json
- **Fixed Structure**: Removed nested questions structure in "ethos-plus-scan" section
- **Reason**: The current system doesn't support questions within questions (nested structure)
- **Solution**: Flattened all sub-questions (value1-aligned, value1-deviation, etc.) to be top-level questions in the section
- **Result**: The section now has 16 questions (4 core values × 4 questions each) instead of a nested structure

## API Response Changes

### Before
The `group` field was missing from question options in API responses due to the bug.

### After
The `group` field is now correctly returned:
```json
{
  "value": "collaboration",
  "group": "Connection",
  "label": {
    "en": "Collaboration",
    "nl": "Samenwerking"
  }
}
```

## Database Migration Notes

When the application starts with `spring.jpa.hibernate.ddl-auto=update`:
- New columns will be automatically added to existing tables:
  - `questions.number` (varchar(50))
  - `questions.hint_en` (text)
  - `questions.hint_nl` (text)
  - `questions.edit_enabled` (boolean)
  - `sections.description_en` (text)
  - `sections.description_nl` (text)
  - `question_options.option_group` (varchar(255)) - renamed from `group`

**Important**: If you have existing data with the old `group` column, you may need to manually migrate it:
```sql
-- Only needed if you have existing data
ALTER TABLE question_options RENAME COLUMN "group" TO option_group;
```

## Testing Recommendations

1. **Test New Questionnaire Loading**
   - Ensure `ethos-scan-adult-new.json` loads without errors
   - Verify all 3 sections load correctly
   - Confirm all 16 questions in "ethos-plus-scan" section are loaded

2. **Test API Responses**
   - Verify `group` field appears in question options
   - Verify `number` field appears in questions
   - Verify `hint` field appears in questions (where provided)
   - Verify `description` field appears in sections (where provided)

3. **Test Database**
   - Confirm new columns are created
   - Verify data saves correctly with new fields
   - Check that `option_group` column works correctly

## Breaking Changes

**None** - All changes are backwards compatible. Existing questionnaires will continue to work. New fields are optional.

## Files Modified

1. `src/main/java/com/example/ai/tool/analysis/phase_two_api/entity/Option.java`
2. `src/main/java/com/example/ai/tool/analysis/phase_two_api/entity/Question.java`
3. `src/main/java/com/example/ai/tool/analysis/phase_two_api/entity/Section.java`
4. `src/main/java/com/example/ai/tool/analysis/phase_two_api/pojo/QuestionDto.java`
5. `src/main/java/com/example/ai/tool/analysis/phase_two_api/pojo/SectionDto.java`
6. `src/main/java/com/example/ai/tool/analysis/phase_two_api/service/QuestionnaireLoaderService.java`
7. `src/main/java/com/example/ai/tool/analysis/phase_two_api/service/QuestionnaireService.java`
8. `src/main/resources/questionnaires/ethos-scan-adult-new.json`

## Build Status

✅ **Build Successful** - All changes compile without errors.

