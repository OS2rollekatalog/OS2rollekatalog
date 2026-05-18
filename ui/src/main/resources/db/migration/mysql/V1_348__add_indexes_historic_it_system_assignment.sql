-- Dækker:
--   1) closeOpenByRecordHash + existsByRecordHashAndValidToIsNull (filtrerer record_hash + valid_to IS NULL)
--   2) NOT EXISTS-subquery i SystemRoleAssignmentDao.findIdsMissingOpenHistoricRow brugt af
--      SeedHistoricItSystemAssignmentsTask (user_role_id, system_role_id, valid_to IS NULL)
--
-- ALGORITHM=INPLACE og LOCK=NONE undgår at blokere skrivninger lokalt under index-bygning.
-- På Galera bruger DDL stadig TOI som default, så cluster-skrivninger pauses i de sekunder
-- DDL'en tager — alternativt kan index'erne køres manuelt på hver node via RSU før deploy.
--
-- IF NOT EXISTS gør migrationen idempotent: hvis index'erne allerede er applieret manuelt
-- (fx via RSU på hver Galera-node), springer migrationen over uden at fejle.
ALTER TABLE historic_it_system_assignment
    ADD INDEX IF NOT EXISTS idx_historic_it_system_assignment_hash_open (record_hash, valid_to),
    ADD INDEX IF NOT EXISTS idx_historic_it_system_assignment_role_pair (user_role_id, system_role_id, valid_to),
    ALGORITHM=INPLACE, LOCK=NONE;
