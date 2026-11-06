-- V5: employees and their login sessions (Phase 3).

CREATE TABLE employees (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_employees_role CHECK (role IN ('OWNER', 'SELLER', 'WAREHOUSE')),
    CONSTRAINT chk_employees_email CHECK (email = btrim(email) AND email LIKE '%_@_%'),
    CONSTRAINT chk_employees_full_name CHECK (btrim(full_name) <> ''),
    CONSTRAINT chk_employees_password_hash CHECK (password_hash LIKE '$2%')
);

-- Login is by email, so two accounts must not differ only in letter case.
CREATE UNIQUE INDEX uq_employees_email_lower ON employees (lower(email));

-- Spring Session JDBC tables, taken unchanged from spring-session-jdbc 4.1 (schema-postgresql.sql).
-- PRINCIPAL_NAME holds the employee email, which is why employees.email is limited to 100.
CREATE TABLE spring_session (
    primary_id            CHAR(36)     NOT NULL,
    session_id            CHAR(36)     NOT NULL,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
