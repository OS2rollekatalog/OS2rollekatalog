ALTER TABLE security_log 
	ADD COLUMN client_version VARCHAR(64) NULL,
	ADD COLUMN tls_version    VARCHAR(64) NULL,
	ADD COLUMN response_code  VARCHAR(64) NULL;