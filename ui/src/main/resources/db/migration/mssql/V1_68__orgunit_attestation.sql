ALTER TABLE ous ADD last_attested_by NVARCHAR(255);
ALTER TABLE ous ADD last_attested DATETIME2 NULL;
ALTER TABLE ous ADD next_attestation DATETIME2 NULL;
