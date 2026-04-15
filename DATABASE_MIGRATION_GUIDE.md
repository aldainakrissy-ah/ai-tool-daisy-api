# Database Migration Guide

## Overview
This guide helps you migrate existing questionnaire data to the new schema with enhanced fields.

## Automatic Migration (Recommended)

If you're using `spring.jpa.hibernate.ddl-auto=update` in your `application.properties`, Hibernate will automatically:

✅ Add new columns to existing tables
✅ Preserve all existing data
✅ Set NULL for new fields in existing records

**No manual intervention required** unless you have the old `group` column issue.

## Schema Changes

### New Columns Added

#### `questions` table:
```sql
ALTER TABLE questions ADD COLUMN number VARCHAR(50);
ALTER TABLE questions ADD COLUMN hint_en TEXT;
ALTER TABLE questions ADD COLUMN hint_nl TEXT;
ALTER TABLE questions ADD COLUMN edit_enabled BOOLEAN;
```

#### `sections` table:
```sql
ALTER TABLE sections ADD COLUMN description_en TEXT;
ALTER TABLE sections ADD COLUMN description_nl TEXT;
```

#### `question_options` table:
```sql
-- Only if the old column exists with backticks
ALTER TABLE question_options RENAME COLUMN "group" TO option_group;

-- If the column doesn't exist yet
ALTER TABLE question_options ADD COLUMN option_group VARCHAR(255);
```

## Manual Migration Steps (If Needed)

### Step 1: Backup Your Database
```bash
# PostgreSQL backup
pg_dump -h localhost -U your_user -d your_database > backup_$(date +%Y%m%d).sql
```

### Step 2: Check for Old `group` Column
```sql
-- Check if the column exists
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'question_options' 
AND column_name IN ('group', 'option_group');
```

### Step 3: Rename Column (If Needed)
```sql
-- Only if 'group' column exists
ALTER TABLE question_options RENAME COLUMN "group" TO option_group;
```

### Step 4: Verify Schema
```sql
-- Check questions table
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'questions'
AND column_name IN ('number', 'hint_en', 'hint_nl', 'edit_enabled')
ORDER BY ordinal_position;

-- Check sections table
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'sections'
AND column_name IN ('description_en', 'description_nl')
ORDER BY ordinal_position;

-- Check question_options table
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'question_options'
AND column_name = 'option_group';
```

## Data Migration

### Update Existing Questionnaires

If you have existing questionnaires that you want to update with new fields:

```sql
-- Example: Add numbers to existing questions
UPDATE questions 
SET number = '1' 
WHERE id = 'statement-1';

UPDATE questions 
SET number = 'A1' 
WHERE id = 'value1-aligned';

-- Example: Add hints to text questions
UPDATE questions 
SET hint_en = '(brief explanation)', 
    hint_nl = '(korte toelichting)' 
WHERE type = 'text' 
AND id LIKE 'value%-obstacles';

-- Example: Enable editing for specific questions
UPDATE questions 
SET edit_enabled = true 
WHERE id = 'core-values-selection';

-- Example: Add section descriptions
UPDATE sections 
SET description_en = 'Read each statement carefully and indicate to what extent you agree:',
    description_nl = 'Lees elke stelling zorgvuldig en geef aan in hoeverre je het ermee eens bent:'
WHERE id = 'statements';
```

## Rollback Plan

If you need to rollback the changes:

```sql
-- Remove new columns from questions table
ALTER TABLE questions DROP COLUMN IF EXISTS number;
ALTER TABLE questions DROP COLUMN IF EXISTS hint_en;
ALTER TABLE questions DROP COLUMN IF EXISTS hint_nl;
ALTER TABLE questions DROP COLUMN IF EXISTS edit_enabled;

-- Remove new columns from sections table
ALTER TABLE sections DROP COLUMN IF EXISTS description_en;
ALTER TABLE sections DROP COLUMN IF EXISTS description_nl;

-- Rename option_group back to group (if needed)
ALTER TABLE question_options RENAME COLUMN option_group TO "group";
```

Then restore from backup:
```bash
psql -h localhost -U your_user -d your_database < backup_YYYYMMDD.sql
```

## Testing After Migration

### 1. Verify Data Integrity
```sql
-- Check that no data was lost
SELECT COUNT(*) FROM questionnaires;
SELECT COUNT(*) FROM sections;
SELECT COUNT(*) FROM questions;

-- Check new fields
SELECT id, number, edit_enabled FROM questions WHERE number IS NOT NULL;
SELECT id, option_group FROM question_options WHERE option_group IS NOT NULL;
```

### 2. Test API Endpoints
```bash
# Get all questionnaires
curl http://localhost:8080/api/questionnaires

# Get specific questionnaire
curl http://localhost:8080/api/questionnaires/ethos-scan-adult-new

# Verify group field is returned
curl http://localhost:8080/api/questionnaires/ethos-scan-adult-new | jq '.sections[0].questions[0].options[0].group'
```

### 3. Test Questionnaire Loading
```bash
# Check application logs for successful loading
# Should see: "Successfully loaded questionnaire: ethos-scan-adult-new"
```

## Common Issues and Solutions

### Issue 1: "group" Column Error
**Error**: `ERROR: syntax error at or near "group"`

**Solution**: 
```sql
ALTER TABLE question_options RENAME COLUMN "group" TO option_group;
```

### Issue 2: Existing Data Not Updated
**Problem**: New fields show NULL for existing questions

**Solution**: This is expected. New fields are optional. Update manually if needed:
```sql
UPDATE questions SET number = 'desired_number' WHERE id = 'question_id';
```

### Issue 3: Application Won't Start
**Error**: Connection issues or schema validation errors

**Solution**:
1. Check database connectivity
2. Verify user permissions
3. Check Hibernate DDL mode in application.properties
4. Review application logs for specific errors

## Environment-Specific Notes

### Development Environment
- Use `spring.jpa.hibernate.ddl-auto=update`
- Automatic migration will work

### Production Environment
- Consider using `spring.jpa.hibernate.ddl-auto=validate`
- Run migration scripts manually before deployment
- Test on staging environment first

## Post-Migration Checklist

- [ ] Database backup created
- [ ] Schema changes applied successfully
- [ ] New columns visible in database
- [ ] Existing data preserved
- [ ] New questionnaire loads without errors
- [ ] API returns `group` field in options
- [ ] API returns new fields (`number`, `hint`, `editEnabled`, `description`)
- [ ] Application logs show no errors
- [ ] Response times are normal
- [ ] All existing questionnaires still work

## Support

If you encounter issues:
1. Check application logs: `logs/application.log`
2. Check database logs
3. Verify schema with queries above
4. Restore from backup if needed
5. Review `CHANGES_SUMMARY.md` for details on what changed

