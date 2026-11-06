-- V12: the owner retries a dead background job from the admin (Phase 4), and every retry is audited.

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
    'STOCK_RECEIVED', 'STOCK_ADJUSTED', 'ORDER_STATUS_CHANGED',
    'EMPLOYEE_CREATED', 'EMPLOYEE_ROLE_CHANGED', 'EMPLOYEE_DEACTIVATED', 'EMPLOYEE_ACTIVATED',
    'EMPLOYEE_PASSWORD_RESET', 'EMPLOYEE_PASSWORD_CHANGED',
    'JOB_RETRIED'));

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_entity_type;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_entity_type
    CHECK (entity_type IN ('VARIANT', 'ORDER', 'EMPLOYEE', 'JOB'));

CREATE INDEX idx_background_jobs_status_id ON background_jobs (status, id DESC);
