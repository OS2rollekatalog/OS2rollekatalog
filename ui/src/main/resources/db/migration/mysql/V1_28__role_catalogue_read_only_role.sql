SELECT id INTO @it_system_id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Læseadgang',
  'http://rollekatalog.dk/readaccess',
  'Denne rolle giver læseadgang til alle data i Rollekataloget',
  @it_system_id
);

SELECT id INTO @system_role_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/readaccess';

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES (
	'Læseadgang',
	'readonly',
	'Denne rolle giver læseadgang til alle data i Rollekataloget',
	@it_system_id
);

SELECT id INTO @user_role_id FROM user_roles WHERE name = 'Læseadgang' AND it_system_id = @it_system_id;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@user_role_id, @system_role_id);
