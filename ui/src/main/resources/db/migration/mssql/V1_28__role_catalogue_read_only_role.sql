DECLARE @ItSystem BIGINT;
SELECT @ItSystem = id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Læseadgang',
  'http://rollekatalog.dk/readaccess',
  'Denne rolle giver læseadgang til alle data i Rollekataloget',
  @ItSystem
);

DECLARE @SystemRoleId BIGINT;
SELECT @SystemRoleId = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/readaccess';

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES (
	'Læseadgang',
	'readonly',
	'Denne rolle giver læseadgang til alle data i Rollekataloget',
	@ItSystem
);

DECLARE @UserRoleId BIGINT;
SELECT @UserRoleId = id FROM user_roles WHERE name = 'Læseadgang' AND it_system_id = @ItSystem;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@UserRoleId, @SystemRoleId);
