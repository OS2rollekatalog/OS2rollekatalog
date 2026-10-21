-- Assignment date on current/historic assignments.
--
-- valid_from is the row's version date, not the assignment date, but was shown as "assigned when" -
-- so everything looked assigned on the upgrade date. assigned_when carries the date from the source.
--
-- ALGORITHM=INSTANT holds the DDL on metadata - it runs as TOI and pauses the whole Galera cluster,
-- and the flag fails rather than falling back to a table copy.
-- Existing rows are filled by AssignedWhenRepairJob in the background, not here: the backfill is one
-- write per assignment plus one per open historic row, which is minutes of migration on a large base.

ALTER TABLE current_assignment
    ADD COLUMN IF NOT EXISTS assigned_when DATETIME NULL, ALGORITHM=INSTANT;
ALTER TABLE historic_assignment
    ADD COLUMN IF NOT EXISTS assigned_when DATETIME NULL, ALGORITHM=INSTANT;
ALTER TABLE current_excepted_assignment
    ADD COLUMN IF NOT EXISTS assigned_when DATETIME NULL, ALGORITHM=INSTANT;
ALTER TABLE historic_excepted_assignment
    ADD COLUMN IF NOT EXISTS assigned_when DATETIME NULL, ALGORITHM=INSTANT;
