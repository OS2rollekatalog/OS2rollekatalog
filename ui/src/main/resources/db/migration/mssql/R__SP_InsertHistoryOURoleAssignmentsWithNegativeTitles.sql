-- Stored procedure for populating history_ou_role_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryOURoleAssignmentsWithNegativeTitles;

CREATE OR ALTER PROC SP_InsertHistoryOURoleAssignmentsWithNegativeTitles
AS
BEGIN
    INSERT INTO history_role_assignment_negative_titles (
                                              dato
                                            ,ou_uuid
                                            ,role_id
                                            ,role_name
                                            ,role_it_system_id
                                            ,role_it_system_name
                                            ,role_role_group
                                            ,role_role_group_id
                                            ,assigned_through_type
                                            ,assigned_through_uuid
                                            ,assigned_through_name
                                            ,assigned_by_user_id
                                            ,assigned_by_name
                                            ,assigned_when
                                            ,inherit
                                            ,start_date
                                            ,stop_date
                                            ,title_uuids
    )
    -- user roles from direct assignments
    SELECT
                CURRENT_TIMESTAMP
         ,MAX(o.uuid)
         ,MAX(ur.id)
         ,MAX(ur.name)
         ,MAX(it.id)
         ,MAX(it.name)
         , NULL
         , NULL
         ,'DIRECT'
         ,NULL
         ,NULL
         ,MAX(our.assigned_by_user_id)
         ,MAX(our.assigned_by_name)
         ,MAX(our.assigned_timestamp)
         ,0
         , MAX(our.start_date)
         ,MAX(our.stop_date)
         ,STRING_AGG(oureu.title_uuid, ',')
    FROM ou_roles our
             JOIN ous o ON o.uuid = our.ou_uuid
             JOIN user_roles ur ON ur.id = our.role_id
             JOIN ou_roles_titles oureu ON oureu.ou_roles_id = our.id
             JOIN it_systems it ON it.id = ur.it_system_id
    WHERE
        our.inherit = 0
      AND our.inactive = 0
      AND our.contains_excepted_users = 0
      AND our.contains_titles = 2
      AND o.active = 1
    GROUP BY
        our.id
    UNION ALL

    -- user roles through rolegroups from direct assignments
    SELECT
                CURRENT_TIMESTAMP
         ,MAX(o.uuid)
         ,MAX(ur.id)
         ,MAX(ur.name)
         ,MAX(it.id)
         ,MAX(it.name)
         ,MAX(rg.name)
         ,MAX(rg.id)
         ,'DIRECT'
         ,NULL
         ,NULL
         ,MAX(ourg.assigned_by_user_id)
         ,MAX(ourg.assigned_by_name)
         ,MAX(ourg.assigned_timestamp)
         ,0
         ,MAX(ourg.start_date)
         ,MAX(ourg.stop_date)
         ,STRING_AGG(oureu.title_uuid, ',')
    FROM ou_rolegroups ourg
             JOIN ous o ON o.uuid = ourg.ou_uuid
             JOIN rolegroup rg ON ourg.rolegroup_id = rg.id
             JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
             JOIN ou_rolegroups_titles oureu ON oureu.ou_rolegroups_id = rgr.id
             JOIN user_roles ur ON ur.id = rgr.role_id
             JOIN it_systems it ON it.id = ur.it_system_id
    WHERE
        ourg.inherit = 0
      AND ourg.inactive = 0
      AND ourg.contains_excepted_users = 0
      AND ourg.contains_titles = 2
      AND o.active = 1
    GROUP BY ourg.id, ur.id;

    -- user roles from orgunits (inherited)
    WITH cte
             AS
             (
                 SELECT
                     our.id
                      ,o.uuid AS ou_uuid
                      ,o.uuid AS orig_ou_uuid
                      ,o.parent_uuid
                      ,o.name AS orig_ou_name
                 FROM ous o
                          JOIN ou_roles our ON our.ou_uuid = o.uuid AND our.inherit = 1
                 WHERE
                     o.active = 1

                 UNION ALL

                 SELECT
                     cte.id
                      ,o.uuid AS ou_uuid
                      ,cte.orig_ou_uuid
                      ,o.parent_uuid
                      ,cte.orig_ou_name
                 FROM ous o
                          JOIN cte ON cte.ou_uuid = o.parent_uuid
                 WHERE
                     o.active = 1
             )
    INSERT INTO history_role_assignment_negative_titles (
                                              dato
                                            ,ou_uuid
                                            ,role_id
                                            ,role_name
                                            ,role_it_system_id
                                            ,role_it_system_name
                                            ,assigned_through_type
                                            ,assigned_through_uuid
                                            ,assigned_through_name
                                            ,assigned_by_user_id
                                            ,assigned_by_name
                                            ,assigned_when
                                            ,inherit
                                            ,start_date
                                            ,stop_date
                                            ,title_uuids
    )
    SELECT
        CURRENT_TIMESTAMP
         ,MAX(cte.ou_uuid)
         ,MAX(ur.id)
         ,MAX(ur.name)
         ,MAX(it.id)
         ,MAX(it.name)
         ,'ORGUNIT'
         ,MAX(orig_ou_uuid)
         ,MAX(orig_ou_name)
         ,MAX(our.assigned_by_user_id)
         ,MAX(our.assigned_by_name)
         ,MAX(our.assigned_timestamp)
         ,max(iif(orig_ou_uuid=cte.ou_uuid, 1, 0))
         ,MAX(our.start_date)
         ,MAX(our.stop_date)
         ,STRING_AGG(oureu.title_uuid, ',')
    FROM cte
             JOIN ou_roles our ON our.id = cte.id
             JOIN ou_roles_titles oureu ON oureu.ou_roles_id = our.id
             JOIN ous ou ON ou.uuid = our.ou_uuid
             JOIN user_roles ur ON ur.id = our.role_id
             JOIN it_systems it ON it.id = ur.it_system_id
    WHERE our.inactive = 0
      AND our.contains_titles = 2
      AND our.contains_excepted_users = 0
    GROUP BY our.id
    ;

    -- user roles through rolegroups from orgunits (inherited)
    WITH cte
             AS
             (
                 SELECT
                     ourg.id
                      ,o.uuid AS ou_uuid
                      ,o.uuid AS orig_ou_uuid
                      ,o.parent_uuid
                      ,o.name AS orig_ou_name
                 FROM ous o
                          JOIN ou_rolegroups ourg ON ourg.ou_uuid = o.uuid AND ourg.inherit = 1
                 WHERE
                     o.active = 1 AND ourg.inactive = 0

                 UNION ALL

                 SELECT
                     cte.id
                      ,o.uuid AS ou_uuid
                      ,cte.orig_ou_uuid
                      ,o.parent_uuid
                      ,cte.orig_ou_name
                 FROM ous o
                          JOIN cte ON cte.ou_uuid = o.parent_uuid
                 WHERE
                     o.active = 1
             )
    INSERT INTO history_role_assignment_negative_titles (
                                              dato
                                            ,ou_uuid
                                            ,role_id
                                            ,role_name
                                            ,role_it_system_id
                                            ,role_it_system_name
                                            ,role_role_group
                                            ,role_role_group_id
                                            ,assigned_through_type
                                            ,assigned_through_uuid
                                            ,assigned_through_name
                                            ,assigned_by_user_id
                                            ,assigned_by_name
                                            ,assigned_when
                                            ,inherit
                                            ,start_date
                                            ,stop_date
                                            ,title_uuids
    )
    SELECT
                CURRENT_TIMESTAMP
         ,MAX(cte.ou_uuid)
         ,MAX(ur.id)
         ,MAX(ur.name)
         ,MAX(it.id)
         ,MAX(it.name)
         ,MAX(rg.name)
         ,MAX(rg.id)
         ,'ORGUNIT'
         ,MAX(orig_ou_uuid)
         ,MAX(orig_ou_name)
         ,MAX(ourg.assigned_by_user_id)
         ,MAX(ourg.assigned_by_name)
         ,MAX(ourg.assigned_timestamp)
         ,max(iif(orig_ou_uuid=cte.ou_uuid, 1, 0))
         ,MAX(ourg.start_date)
         ,MAX(ourg.stop_date)
         ,STRING_AGG(oureu.title_uuid, ',')
    FROM cte
             JOIN ou_rolegroups ourg ON ourg.id = cte.id
             JOIN ou_roles_titles oureu ON oureu.ou_roles_id = ourg.id
             JOIN rolegroup rg ON rg.id = ourg.rolegroup_id
             JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
             JOIN user_roles ur ON ur.id = rgr.role_id
             JOIN it_systems it ON it.id = ur.it_system_id
    WHERE ourg.inactive = 0
      AND ourg.contains_titles = 2
      AND ourg.contains_excepted_users = 0
    GROUP BY ourg.id, ur.id
    ;
END
GO