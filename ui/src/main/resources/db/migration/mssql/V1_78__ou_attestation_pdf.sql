CREATE TABLE ou_attestation_pdfs (
	id 					BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	pdf					VARBINARY(max) NOT NULL
);

ALTER TABLE ous ADD attestation_pdf BIGINT REFERENCES ou_attestation_pdfs(id);
DROP TABLE attestations;