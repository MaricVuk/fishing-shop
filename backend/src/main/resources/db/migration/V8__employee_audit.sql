-- V8: the owner manages employees (Phase 3), and each of those changes is audited.

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action CHECK (action IN (
    'STOCK_RECEIVED', 'STOCK_ADJUSTED', 'ORDER_STATUS_CHANGED',
    'EMPLOYEE_CREATED', 'EMPLOYEE_ROLE_CHANGED', 'EMPLOYEE_DEACTIVATED', 'EMPLOYEE_ACTIVATED',
    'EMPLOYEE_PASSWORD_RESET', 'EMPLOYEE_PASSWORD_CHANGED'));

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_entity_type;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_entity_type CHECK (entity_type IN ('VARIANT', 'ORDER', 'EMPLOYEE'));

CREATE INDEX idx_audit_log_action ON audit_log (action, id DESC);
CREATE INDEX idx_employees_role_active ON employees (role, active);
