-- Dækker:
--   1) closeOpenByRecordHash + existsByRecordHashAndValidToIsNull (filtrerer record_hash + valid_to IS NULL)
--   2) NOT EXISTS-subquery i OrgUnitUserRoleAssignmentDao/OrgUnitRoleGroupAssignmentDao.findIdsMissingOpenHistoricRow
--      brugt af SeedHistoricOuAssignmentsTask (ou_uuid + role_id/role_role_group_id + valid_to IS NULL)
--
-- ALGORITHM=INPLACE og LOCK=NONE undgår at blokere skrivninger lokalt under index-bygning.
-- På Galera bruger DDL stadig TOI som default, så cluster-skrivninger pauses i de sekunder
-- DDL'en tager — alternativt kan index'erne køres manuelt på hver node via RSU før deploy.
--
-- IF NOT EXISTS gør migrationen idempotent: hvis index'erne allerede er applieret manuelt
-- (fx via RSU på hver Galera-node), springer migrationen over uden at fejle.
ALTER TABLE historic_ou_assignment
    ADD INDEX IF NOT EXISTS idx_historic_ou_assignment_hash_open (record_hash, valid_to),
    ADD INDEX IF NOT EXISTS idx_historic_ou_assignment_lookup (ou_uuid, role_id, role_role_group_id, valid_to),
    ALGORITHM=INPLACE, LOCK=NONE;
