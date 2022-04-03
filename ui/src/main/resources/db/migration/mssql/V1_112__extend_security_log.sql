ALTER TABLE security_log ADD client_version VARCHAR(64) NULL;
ALTER TABLE security_log ADD tls_version VARCHAR(64) NULL;
ALTER TABLE security_log ADD response_code VARCHAR(64) NULL;