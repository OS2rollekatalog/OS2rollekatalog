-- Close open historic assignments whose user role, role group or IT system has been deleted.
-- valid_to is the earliest audit-logged deletion at or after valid_from, otherwise the migration time.
UPDATE historic_assignment ha
LEFT JOIN user_roles ur ON ur.id = ha.user_role_id
LEFT JOIN rolegroup rg ON rg.id = ha.role_group_id
LEFT JOIN it_systems its ON its.id = ha.it_system_id
SET ha.updated_at = NOW(),
    ha.valid_to = LEAST(
        COALESCE((SELECT MIN(al.timestamp) FROM audit_log al
                  WHERE al.entity_type = 'USERROLE' AND al.entity_id = CAST(ha.user_role_id AS CHAR)
                    AND al.event_type = 'DELETE' AND al.timestamp >= ha.valid_from), NOW()),
        COALESCE((SELECT MIN(al.timestamp) FROM audit_log al
                  WHERE al.entity_type = 'ROLEGROUP' AND al.entity_id = CAST(ha.role_group_id AS CHAR)
                    AND al.event_type = 'DELETE' AND al.timestamp >= ha.valid_from), NOW()),
        COALESCE((SELECT MIN(al.timestamp) FROM audit_log al
                  WHERE al.entity_type = 'ITSYSTEM' AND al.entity_id = CAST(ha.it_system_id AS CHAR)
                    AND al.event_type = 'DELETE' AND al.timestamp >= ha.valid_from), NOW()))
WHERE ha.valid_to IS NULL
  AND (ur.id IS NULL
       OR (ha.role_group_id IS NOT NULL AND rg.id IS NULL)
       OR (ha.it_system_id IS NOT NULL AND its.id IS NULL));
