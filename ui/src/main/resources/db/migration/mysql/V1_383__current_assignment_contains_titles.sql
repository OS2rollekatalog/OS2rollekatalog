-- Whether the title stamped on a calculated assignment is the reason for the role or just the one the
-- holder has. Never hashed: that would close and reopen every title filtered assignment on upgrade.
ALTER TABLE current_assignment ADD COLUMN contains_titles VARCHAR(255) NOT NULL DEFAULT 'NO';

-- assignment_id is unique within its own source table only, hence the narrowing to ou_roles rows.
-- ou_roles is a legacy ordinal column, so the value is translated on the way in.
UPDATE current_assignment ca
    JOIN ou_roles our ON our.id = ca.assignment_id
SET ca.contains_titles = CASE our.contains_titles WHEN 1 THEN 'POSITIVE' WHEN 2 THEN 'NEGATIVE' ELSE 'NO' END
WHERE ca.assignment_title_uuid IS NOT NULL
  AND ca.assignment_ou_uuid IS NOT NULL
  AND ca.assignment_role_group_id IS NULL;

-- Relabel the open history so existing reports read correctly without waiting for a recalculation.
-- STRAIGHT_JOIN keeps the filtered current_assignment side driving, so this probes the hash index.
UPDATE current_assignment ca
    STRAIGHT_JOIN historic_assignment ha ON ha.record_hash = ca.record_hash AND ha.valid_to IS NULL
SET ha.assigned_through_type = 'ORGUNIT'
WHERE ca.contains_titles = 'NEGATIVE'
  AND ha.assigned_through_type = 'TITLE';
