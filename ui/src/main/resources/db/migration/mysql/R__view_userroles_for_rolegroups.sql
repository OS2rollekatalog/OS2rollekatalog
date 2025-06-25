CREATE OR REPLACE VIEW vw_all_userroles_with_rolegroups AS
SELECT
    ur.id,
    ur.name,
    ur.description,
    its.name AS it_system_name,
    rg.id AS rolegroup_id,
    CASE WHEN rgr.role_id IS NOT NULL THEN 1 ELSE 0 END AS selected,
    its.readonly AS read_only
FROM user_roles ur
JOIN it_systems its ON ur.it_system_id = its.id
CROSS JOIN rolegroup rg
LEFT JOIN rolegroup_roles rgr
    ON rgr.role_id = ur.id AND rgr.rolegroup_id = rg.id
WHERE
    its.deleted = 0
    AND ur.allow_postponing = 0
;