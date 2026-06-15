-- Covering index til AD-sync's findActiveAssignedAsProjection-query.
--
-- Query'en filtrerer på user_role_id + start/stop_date og projecterer kun
-- (user_role_id, user_uuid, id) — denne index dækker hele forespørgslen så
-- DB kan svare uden heap-lookup mod selve current_assignment-tabellen.
-- Forventet ~70% reduktion i SQL-tid på Næstved-skala (190k+ rækker).
--
-- ALGORITHM=INPLACE og LOCK=NONE undgår at blokere skrivninger under
-- index-bygning lokalt. På Galera-clusters bruger DDL stadig TOI som default,
-- så cluster-skrivninger pauses i de sekunder DDL'en tager — derfor kan
-- index'et alternativt køres manuelt på hver node via RSU før deploy.
--
-- IF NOT EXISTS gør migrationen idempotent: hvis index'et allerede er
-- applieret manuelt (fx via RSU på hver Galera-node), springer migrationen
-- over uden at fejle.
ALTER TABLE current_assignment
    ADD INDEX IF NOT EXISTS idx_assignment_covering (
        assignment_user_role_id,
        start_date,
        stop_date,
        assignment_user_uuid,
        id
    ),
    ALGORITHM=INPLACE,
    LOCK=NONE;
