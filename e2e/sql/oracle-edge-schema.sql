-- Create Oracle schema objects for E2E testing edge cases
CREATE TABLE edge_test_limit_table (
    id VARCHAR2(50) PRIMARY KEY,
    "LIMIT" NUMBER,
    description VARCHAR2(100)
);

CREATE TABLE edge_test_composite_pk (
    tenant_id VARCHAR2(20),
    seq_id NUMBER,
    status VARCHAR2(10),
    PRIMARY KEY (tenant_id, seq_id)
);

CREATE TABLE edge_test_no_pk (
    event_time TIMESTAMP,
    event_type VARCHAR2(30),
    payload VARCHAR2(500)
);

CREATE TABLE edge_test_lob_table (
    id NUMBER PRIMARY KEY,
    notes CLOB,
    attachment BLOB,
    external_file BFILE
);

CREATE TABLE edge_test_excluded_parent (
    id NUMBER PRIMARY KEY,
    name VARCHAR2(50)
);

CREATE TABLE edge_test_child_table (
    id NUMBER PRIMARY KEY,
    parent_id NUMBER,
    val VARCHAR2(20),
    CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES edge_test_excluded_parent(id)
);

-- normal compatible view
CREATE VIEW edge_test_compat_view AS
SELECT id, description FROM edge_test_limit_table;

-- complex view requiring review
CREATE VIEW edge_test_complex_view AS
SELECT id, ROWNUM as rn_val FROM edge_test_lob_table;
