DECLARE @fk_name VARCHAR(50);

SELECT @fk_name = fk.name from sys.foreign_keys fk
  INNER JOIN sys.objects po ON po.type = 'U' AND po.name = 'rolegroup_roles' AND po.object_id = fk.parent_object_id
  INNER JOIN sys.objects o ON o.type = 'U' AND o.name = 'user_roles' AND o.object_id = fk.referenced_object_id;

DECLARE @stmt VARCHAR(200);

SET @stmt = 'ALTER TABLE rolegroup_roles DROP CONSTRAINT ' + @fk_name;

EXEC sp_sqlexec @stmt;

ALTER TABLE rolegroup_roles WITH CHECK ADD CONSTRAINT FK_RGR_UserRoles FOREIGN KEY(role_id) REFERENCES user_roles(id) ON DELETE CASCADE;
