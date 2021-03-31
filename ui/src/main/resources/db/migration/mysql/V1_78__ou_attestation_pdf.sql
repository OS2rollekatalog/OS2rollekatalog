CREATE TABLE ou_attestation_pdfs (
	id 					BIGINT PRIMARY KEY AUTO_INCREMENT,
	pdf					MEDIUMBLOB NOT NULL
);

ALTER TABLE ous ADD COLUMN attestation_pdf BIGINT REFERENCES ou_attestation_pdfs(id);
DROP TABLE attestations;