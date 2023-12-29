
ALTER TABLE it_systems ADD COLUMN attestation_exempt BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE history_it_systems ADD COLUMN attestation_exempt BOOLEAN NOT NULL DEFAULT 0;
