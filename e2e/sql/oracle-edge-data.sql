-- Insert test data for Oracle edge cases
INSERT INTO edge_test_limit_table (id, "LIMIT", description) VALUES ('key1', 10, 'description 1');
INSERT INTO edge_test_limit_table (id, "LIMIT", description) VALUES ('key2', 20, 'description 2');

INSERT INTO edge_test_composite_pk (tenant_id, seq_id, status) VALUES ('tenantA', 1, 'ACTIVE');
INSERT INTO edge_test_composite_pk (tenant_id, seq_id, status) VALUES ('tenantA', 2, 'INACTIVE');
INSERT INTO edge_test_composite_pk (tenant_id, seq_id, status) VALUES ('tenantB', 1, 'PENDING');

INSERT INTO edge_test_no_pk (event_time, event_type, payload) VALUES (SYSTIMESTAMP, 'LOGIN', 'User tenantA logged in');
INSERT INTO edge_test_no_pk (event_time, event_type, payload) VALUES (SYSTIMESTAMP, 'LOGOUT', 'User tenantA logged out');

INSERT INTO edge_test_lob_table (id, notes, attachment, external_file) VALUES (1, 'Here are some clob notes', utl_raw.cast_to_raw('Blob attachment content'), NULL);

INSERT INTO edge_test_excluded_parent (id, name) VALUES (100, 'Excluded Parent 1');
INSERT INTO edge_test_child_table (id, parent_id, val) VALUES (1, 100, 'value 1');

COMMIT;
