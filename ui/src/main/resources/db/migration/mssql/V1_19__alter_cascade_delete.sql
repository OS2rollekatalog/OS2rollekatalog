-- tilføj cascade delete til system_role_assignments tabellen
DECLARE @fk_name VARCHAR(50);

SELECT @fk_name = fk.name from sys.foreign_keys fk
  INNER JOIN sys.objects po ON po.type = 'U' AND po.name = 'system_role_assignments' AND po.object_id = fk.parent_object_id
  INNER JOIN sys.objects o ON o.type = 'U' AND o.name = 'system_roles' AND o.object_id = fk.referenced_object_id;

DECLARE @stmt VARCHAR(200);

SET @stmt = 'ALTER TABLE system_role_assignments DROP CONSTRAINT ' + @fk_name;

EXEC sp_sqlexec @stmt;

ALTER TABLE system_role_assignments WITH CHECK ADD CONSTRAINT FK_SRA_SystemRoles
      FOREIGN KEY(system_role_id) REFERENCES system_roles(id) ON DELETE CASCADE;

-- tilføj cascade delete til system_role_assignment_contraint_values tabellen

SELECT @fk_name = fk.name from sys.foreign_keys fk
  INNER JOIN sys.objects po ON po.type = 'U' AND po.name = 'system_role_assignment_constraint_values' AND po.object_id = fk.parent_object_id
  INNER JOIN sys.objects o ON o.type = 'U' AND o.name = 'system_role_assignments' AND o.object_id = fk.referenced_object_id;

SET @stmt = 'ALTER TABLE system_role_assignment_constraint_values DROP CONSTRAINT ' + @fk_name;

EXEC sp_sqlexec @stmt;

ALTER TABLE system_role_assignment_constraint_values WITH CHECK ADD CONSTRAINT FK_SRACV_SystemRoleAssignments
      FOREIGN KEY(system_role_assignment_id) REFERENCES system_role_assignments(id) ON DELETE CASCADE;

      
-- tilføj cascade delete til ou_roles tabellen

SELECT @fk_name = fk.name from sys.foreign_keys fk
  INNER JOIN sys.objects po ON po.type = 'U' AND po.name = 'ou_roles' AND po.object_id = fk.parent_object_id
  INNER JOIN sys.objects o ON o.type = 'U' AND o.name = 'user_roles' AND o.object_id = fk.referenced_object_id;

SET @stmt = 'ALTER TABLE ou_roles DROP CONSTRAINT ' + @fk_name;

EXEC sp_sqlexec @stmt;

ALTER TABLE ou_roles WITH CHECK ADD CONSTRAINT FK_RoleOURoles
      FOREIGN KEY(role_id) REFERENCES user_roles(id) ON DELETE CASCADE;

      
-- tilføj cascade delete til ou_roles tabellen

SELECT @fk_name = fk.name from sys.foreign_keys fk
  INNER JOIN sys.objects po ON po.type = 'U' AND po.name = 'user_roles_mapping' AND po.object_id = fk.parent_object_id
  INNER JOIN sys.objects o ON o.type = 'U' AND o.name = 'user_roles' AND o.object_id = fk.referenced_object_id;

SET @stmt = 'ALTER TABLE user_roles_mapping DROP CONSTRAINT ' + @fk_name;

EXEC sp_sqlexec @stmt;

ALTER TABLE user_roles_mapping WITH CHECK ADD CONSTRAINT FK_URM_UserRoles
      FOREIGN KEY(role_id) REFERENCES user_roles(id) ON DELETE CASCADE;
