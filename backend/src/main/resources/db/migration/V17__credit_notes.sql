-- A confirmed order cancelled after its invoice was issued gets a credit note (storno računa) that reverses
-- the invoice. Credit notes have their own series ST-{year}-{000001} without gaps, and cannot be changed.
CREATE TABLE credit_note_counters (
    year        INTEGER PRIMARY KEY,
    last_number INTEGER NOT NULL,
    CONSTRAINT chk_credit_note_counters_year CHECK (year BETWEEN 2000 AND 9999),
    CONSTRAINT chk_credit_note_counters_last_number CHECK (last_number >= 0)
);

CREATE TABLE credit_notes (
    id              BIGSERIAL      PRIMARY KEY,
    invoice_id      BIGINT         NOT NULL REFERENCES invoices (id) ON DELETE RESTRICT,
    year            INTEGER        NOT NULL,
    sequence_number INTEGER        NOT NULL,
    number          VARCHAR(20)    NOT NULL,
    issued_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    total           NUMERIC(12, 2) NOT NULL,
    vat_rate        NUMERIC(5, 2)  NOT NULL,
    vat_amount      NUMERIC(12, 2) NOT NULL,
    pdf             BYTEA          NOT NULL,
    CONSTRAINT uq_credit_notes_invoice UNIQUE (invoice_id),
    CONSTRAINT uq_credit_notes_year_sequence UNIQUE (year, sequence_number),
    CONSTRAINT uq_credit_notes_number UNIQUE (number),
    CONSTRAINT chk_credit_notes_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT chk_credit_notes_number
        CHECK (number = 'ST-' || year::text || '-' || lpad(sequence_number::text, 6, '0')),
    CONSTRAINT chk_credit_notes_total CHECK (total <= 0),
    CONSTRAINT chk_credit_notes_vat_rate CHECK (vat_rate >= 0 AND vat_rate < 100),
    CONSTRAINT chk_credit_notes_vat_amount CHECK (vat_amount <= 0 AND vat_amount >= total),
    CONSTRAINT chk_credit_notes_pdf_size CHECK (octet_length(pdf) BETWEEN 1 AND 2097152)
);

CREATE FUNCTION credit_notes_are_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'credit notes cannot be changed, % is not allowed', TG_OP;
END;
$$;

CREATE TRIGGER trg_credit_notes_no_update_delete
    BEFORE UPDATE OR DELETE ON credit_notes
    FOR EACH ROW EXECUTE FUNCTION credit_notes_are_immutable();

CREATE TRIGGER trg_credit_notes_no_truncate
    BEFORE TRUNCATE ON credit_notes
    FOR EACH STATEMENT EXECUTE FUNCTION credit_notes_are_immutable();

ALTER TABLE background_jobs DROP CONSTRAINT chk_background_jobs_type;
ALTER TABLE background_jobs ADD CONSTRAINT chk_background_jobs_type
    CHECK (type IN ('ORDER_CONFIRMATION_EMAIL', 'ORDER_INVOICE', 'ORDER_INVOICE_EMAIL',
                    'ORDER_CREDIT_NOTE', 'ORDER_CREDIT_NOTE_EMAIL')) NOT VALID;
ALTER TABLE background_jobs VALIDATE CONSTRAINT chk_background_jobs_type;
