-- V6: audit log (Phase 3). Who changed what, when, and the value before and after.
-- Rows are written in the same transaction as the change and can never be changed or removed.

CREATE TABLE audit_log (
    id          BIGSERIAL    PRIMARY KEY,
    actor_id    BIGINT       NOT NULL REFERENCES employees (id),
    actor_email VARCHAR(100) NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(64)  NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_log_action CHECK (action IN ('STOCK_RECEIVED', 'STOCK_ADJUSTED')),
    CONSTRAINT chk_audit_log_entity_type CHECK (entity_type IN ('VARIANT')),
    CONSTRAINT chk_audit_log_old_value CHECK (old_value IS NULL OR jsonb_typeof(old_value) = 'object'),
    CONSTRAINT chk_audit_log_new_value CHECK (new_value IS NULL OR jsonb_typeof(new_value) = 'object')
);

CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC, id DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id, id);
CREATE INDEX idx_audit_log_actor ON audit_log (actor_id, id);

CREATE FUNCTION audit_log_is_append_only() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only, % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_is_append_only();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_is_append_only();
