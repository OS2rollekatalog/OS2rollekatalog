-- Stored procedure for populating history_ou_role_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryOURoleAssignmentsWithExceptions;
--

CREATE OR ALTER PROC SP_InsertHistoryOURoleAssignmentsWithTitles
AS
BEGIN
    INSERT INTO history_role_assignment_titles (
        dato
        ,ou_uuid
        ,title_uuids
        ,role_id
        ,role_name
        ,role_it_system_id
        ,role_it_system_name
        ,role_role_group
        ,role_role_group_id
        ,assigned_by_user_id
        ,assigned_by_name
        ,assigned_when)
    SELECT
        CURRENT_TIMESTAMP
        ,MAX(o.uuid)
        ,STRING_AGG(oureu.title_uuid, ',')
        ,MAX(ur.id)
        ,MAX(ur.name)
        ,MAX(it.id)
        ,MAX(it.name)
        ,NULL
        ,NULL
        ,MAX(our.assigned_by_user_id)
        ,MAX(our.assigned_by_name)
        ,MAX(our.assigned_timestamp)
    FROM ou_roles our
        JOIN ous o ON o.uuid = our.ou_uuid
        JOIN user_roles ur ON ur.id = our.role_id
        JOIN it_systems it ON it.id = ur.it_system_id
        JOIN ou_roles_titles oureu ON oureu.ou_roles_id = our.id
    WHERE
        our.inherit = 0
        AND o.active = 1
        AND our.inactive = 0
        AND our.contains_titles = 1
    GROUP BY
        our.id

    UNION ALL

    -- user roles through rolegroups from direct assignments
    SELECT
        CURRENT_TIMESTAMP
        ,MAX(o.uuid)
        ,STRING_AGG(ourgeu.title_uuid, ',')
        ,ur.id
        ,MAX(ur.name)
        ,MAX(it.id)
        ,MAX(it.name)
        ,MAX(rg.name)
        ,MAX(rg.id)
        ,MAX(ourg.assigned_by_user_id)
        ,MAX(ourg.assigned_by_name)
        ,MAX(ourg.assigned_timestamp)
    FROM ou_rolegroups ourg
        JOIN ous o ON o.uuid = ourg.ou_uuid
        JOIN rolegroup rg ON ourg.rolegroup_id = rg.id
        JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
        JOIN user_roles ur ON ur.id = rgr.role_id
        JOIN it_systems it ON it.id = ur.it_system_id
        JOIN ou_rolegroups_titles ourgeu ON ourgeu.ou_rolegroups_id = ourg.rolegroup_id
    WHERE
        ourg.inherit = 0
        AND o.active = 1
        AND ourg.inactive = 0
        AND ourg.contains_titles = 1
    GROUP BY ourg.id, ur.id;
END
GO
