# Ethos Scan Adult New - Questionnaire Structure Guide

## Overview
The "ethos-scan-adult-new" questionnaire is designed to assess core values and identify discrepancies between values and behavior.

## Structure

### Section 1: Core Values Selection (`core-values`)
**Purpose**: Allow users to select their top 4 core values from a categorized list.

**Question Type**: `multi-choice-selection`

**Validation**:
- Minimum selections: 4
- Maximum selections: 4
- Required: Yes

**Core Value Groups**:
1. **Connection**: Collaboration, Empathy, Loyalty, Solidarity
2. **Integrity**: Honesty, Transparency, Reliability, Equality
3. **Growth and Development**: Curiosity, Personal Growth, Creativity, Adventure
4. **Autonomy**: Freedom, Self-Determination, Entrepreneurship
5. **Results**: Purposefulness, Efficiency, Quality, Impact
6. **People & Care**: Caring, Well-being, Respect, Safety
7. **Innovation**: Renewal, Courage, Visionary Thinking
8. **Sustainability**: Sustainability, Responsibility

**Features**:
- `editEnabled: true` - Users can modify their selections
- Each option has a `group` field for categorization

### Section 2: Statements (`statements`)
**Purpose**: Assess alignment between values and behavior through 6 Likert-scale statements.

**Question Type**: `radio`

**Questions**:
1. "I have a clear understanding of the core values that guide my choices."
2. "Sometimes I feel guilty or ashamed because my choices don't align with what I truly value."
3. "My daily life (work, family, leisure) is largely in line with my deepest beliefs."
4. "I sometimes hesitate to make choices for fear of what others think, even if they don't align with my values."
5. "When I look back on my recent decisions, I often wish I had acted differently."
6. "Overall, I feel in harmony with my own values."

**Scale**: 1-5 (Strongly disagree to Strongly agree)

### Section 3: Ethos Plus Scan (`ethos-plus-scan`)
**Purpose**: Deep reflection on behavior alignment with each of the 4 selected core values.

**Structure**: 4 sets of 4 questions (16 total questions)

**For Each Core Value (Value 1-4)**:

#### A. Alignment Assessment (Radio)
- Question ID: `value{N}-aligned`
- Text: "To what extent have you acted in accordance with [Selected Core Value {N}] in the past week?"
- Scale: 1 (Not at all) to 5 (Completely)

#### B. Deviation Assessment (Radio)
- Question ID: `value{N}-deviation`
- Text: "To what extent have you acted in deviation from [Selected Core Value {N}] in the past week?"
- Scale: 1 (Not at all) to 5 (Completely)

#### C. Obstacle Identification (Text)
- Question ID: `value{N}-obstacles`
- Text: "What factors or situations have prevented you from acting according to [Selected Core Value {N}]?"
- Hint: "(brief explanation)"

#### D. Success Example (Text)
- Question ID: `value{N}-success`
- Text: "Describe an example of a moment when you acted in accordance with [Selected Core Value {N}]."
- Hint: "(brief explanation)"

## Implementation Notes

### Dynamic Content
The text in Section 3 contains placeholders like `[Selected Core Value 1]` which should be replaced at runtime with the actual values selected in Section 1.

### Question Numbering
- Section 2: Questions numbered 1-6
- Section 3: Questions numbered A1-A4, B1-B4, C1-C4, D1-D4

### Fields Used

#### Standard Fields
- `id`: Unique identifier
- `type`: Question type (radio, text, multi-choice-selection)
- `text`: Question text (localized)
- `options`: Answer options (for radio/multiple choice)
- `required`: Whether the question must be answered

#### Enhanced Fields (New)
- `number`: Display numbering for questions
- `hint`: Additional guidance text (localized)
- `editEnabled`: Whether answers can be modified
- `group`: Category grouping for options
- `description`: Section-level description (localized)

### Supported Languages
- English (`en`)
- Dutch (`nl`)

## API Response Example

```json
{
  "id": "ethos-scan-adult-new",
  "title": {
    "en": "1B Values and Norms Scan & Values-Behavior Discrepancy Scan",
    "nl": "1B Waarden- en Normen scan & Scan Waarden-Gedrag Discrepantie"
  },
  "sections": [
    {
      "id": "core-values",
      "title": {
        "en": "Core Values Selection",
        "nl": "Kernwaarden Selectie"
      },
      "questions": [
        {
          "id": "core-values-selection",
          "type": "multi-choice-selection",
          "text": { ... },
          "options": [
            {
              "value": "collaboration",
              "group": "Connection",
              "label": {
                "en": "Collaboration",
                "nl": "Samenwerking"
              }
            }
          ],
          "validation": {
            "required": true,
            "minSelections": 4,
            "maxSelections": 4
          },
          "editEnabled": true
        }
      ]
    }
  ]
}
```

## Usage Flow

1. **User completes Section 1**: Selects 4 core values
2. **User completes Section 2**: Rates 6 value-behavior alignment statements
3. **User completes Section 3**: For each selected value:
   - Rates alignment behavior (A1-D1)
   - Rates deviation behavior (A2-D2)
   - Describes obstacles (A3-D3)
   - Provides success examples (A4-D4)

## Analysis Possibilities

The questionnaire enables analysis of:
- Core value identification
- Value clarity (Statement 1)
- Value-behavior discrepancies (Statements 2, 5)
- External vs. internal motivation (Statement 4)
- Overall value alignment (Statements 3, 6)
- Specific obstacles to value-aligned behavior
- Examples of value-aligned success

## Frontend Considerations

1. **Dynamic Question Text**: Replace placeholders with selected values in Section 3
2. **Conditional Display**: Only show Section 3 questions for selected values
3. **Grouping**: Display options in Section 1 grouped by category
4. **Validation**: Enforce exactly 4 selections in Section 1
5. **Edit Mode**: Support re-editing responses where `editEnabled: true`

