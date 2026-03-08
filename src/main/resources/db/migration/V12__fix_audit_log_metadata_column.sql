-- Change metadata column from JSON to TEXT to support plain string values
ALTER TABLE audit_log MODIFY COLUMN metadata TEXT;
