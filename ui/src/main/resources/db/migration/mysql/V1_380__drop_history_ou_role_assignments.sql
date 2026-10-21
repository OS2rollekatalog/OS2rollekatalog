-- The Excel report now reads historic_ou_assignment, so the nightly OU-role snapshot is retired.
-- The R__ files that created these procedures are now empty, which stops Flyway recreating them
-- but does not drop the ones already present in existing databases, so each is dropped here.

DROP PROCEDURE IF EXISTS SP_InsertHistoryOUNegativeRoleAssignmentsOUInherit;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUNegativeRoleAssignmentsOUInheritRecursive;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUNegativeRoleAssignmentsOURoleGroupInherit;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUNegRoleAssignmentsOURoleGroupInheritRecursive;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUPositiveRoleAssignmentsOUInherit;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUPositiveRoleAssignmentsOUInheritRecursive;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUPositiveRoleAssignmentsOURoleGroupInherit;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOUPosRoleAssignmentsOURoleGroupInheritRecursive;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignments;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOUInherit;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOUInheritRecursive;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOURoleGroupInherit;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOURoleGroupInheritRecursive;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsWithExceptions;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsWithNegativeTitles;
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsWithTitles;

-- Release the foreign key first so each DROP TABLE is an independent, shorter operation.
-- Galera applies DDL under Total Order Isolation, which blocks cluster writes for its duration.
ALTER TABLE IF EXISTS history_ou_role_assignment_exclusions DROP FOREIGN KEY IF EXISTS fk_excl_assignment;
DROP TABLE IF EXISTS history_ou_role_assignment_exclusions;
DROP TABLE IF EXISTS history_ou_role_assignments;

-- Superseded by history_ou_role_assignments in V1_208; only the procedures dropped above wrote to them.
DROP TABLE IF EXISTS history_role_assignment_titles;
DROP TABLE IF EXISTS history_role_assignment_excepted_users;
DROP TABLE IF EXISTS history_role_assignment_negative_titles;
