-- V13: the invoice email becomes its own job, so an invoice stays issued when its email fails, and the
-- owner can mark a dead job as resolved when no retry will help (Phase 4, code review).

ALTER TABLE background_jobs DROP CONSTRAINT chk_background_jobs_type;
ALTER TABLE background_jobs ADD CONSTRAINT chk_background_jobs_type
    CHECK (type IN ('ORDER_CONFIRMATION_EMAIL', 'ORDER_INVOICE', 'ORDER_INVOICE_EMAIL'));

ALTER TABLE background_jobs DROP CONSTRAINT chk_background_jobs_status;
ALTER TABLE background_jobs ADD CONSTRAINT chk_background_jobs_status
    CHECK (status IN ('PENDING', 'DONE', 'DEAD', 'RESOLVED'));

ALTER TABLE background_jobs DROP CONSTRAINT chk_background_jobs_dead_error;
ALTER TABLE background_jobs ADD CONSTRAINT chk_background_jobs_dead_error
    CHECK (status NOT IN ('DEAD', 'RESOLVED') OR last_error IS NOT NULL);

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
    'STOCK_RECEIVED', 'STOCK_ADJUSTED', 'ORDER_STATUS_CHANGED',
    'EMPLOYEE_CREATED', 'EMPLOYEE_ROLE_CHANGED', 'EMPLOYEE_DEACTIVATED', 'EMPLOYEE_ACTIVATED',
    'EMPLOYEE_PASSWORD_RESET', 'EMPLOYEE_PASSWORD_CHANGED',
    'JOB_RETRIED', 'JOB_RESOLVED'));
