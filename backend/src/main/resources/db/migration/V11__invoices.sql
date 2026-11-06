-- V11: invoices (Phase 4). An invoice is issued by a background job when an order is confirmed.
-- Numbers are per year and must have no gaps, so they come from a counter row taken under a row lock
-- instead of a sequence: a sequence keeps the numbers of rolled back transactions.

ALTER TABLE background_jobs DROP CONSTRAINT chk_background_jobs_type;
ALTER TABLE background_jobs ADD CONSTRAINT chk_background_jobs_type
    CHECK (type IN ('ORDER_CONFIRMATION_EMAIL', 'ORDER_INVOICE'));

CREATE TABLE invoice_counters (
    year        INTEGER PRIMARY KEY,
    last_number INTEGER NOT NULL,
    CONSTRAINT chk_invoice_counters_year CHECK (year BETWEEN 2000 AND 9999),
    CONSTRAINT chk_invoice_counters_last_number CHECK (last_number >= 0)
);

CREATE TABLE invoices (
    id              BIGSERIAL      PRIMARY KEY,
    order_id        BIGINT         NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    year            INTEGER        NOT NULL,
    sequence_number INTEGER        NOT NULL,
    number          VARCHAR(20)    NOT NULL,
    issued_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    total           NUMERIC(12, 2) NOT NULL,
    vat_rate        NUMERIC(5, 2)  NOT NULL,
    vat_amount      NUMERIC(12, 2) NOT NULL,
    pdf             BYTEA          NOT NULL,
    CONSTRAINT uq_invoices_order UNIQUE (order_id),
    CONSTRAINT uq_invoices_year_sequence UNIQUE (year, sequence_number),
    CONSTRAINT uq_invoices_number UNIQUE (number),
    CONSTRAINT chk_invoices_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT chk_invoices_number
        CHECK (number = 'RN-' || year::text || '-' || lpad(sequence_number::text, 6, '0')),
    CONSTRAINT chk_invoices_total CHECK (total >= 0),
    CONSTRAINT chk_invoices_vat_rate CHECK (vat_rate >= 0 AND vat_rate < 100),
    CONSTRAINT chk_invoices_vat_amount CHECK (vat_amount >= 0 AND vat_amount <= total),
    CONSTRAINT chk_invoices_pdf_size CHECK (octet_length(pdf) BETWEEN 1 AND 2097152)
);

CREATE FUNCTION invoices_are_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'invoices cannot be changed, % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_invoices_no_update_delete
    BEFORE UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION invoices_are_immutable();

CREATE TRIGGER trg_invoices_no_truncate
    BEFORE TRUNCATE ON invoices
    FOR EACH STATEMENT EXECUTE FUNCTION invoices_are_immutable();
