INSERT INTO it_systems (name, identifier, system_type) VALUES ('Rollekatalog', 'RoleCatalogue', 'SAML');

DECLARE @ItSystem BIGINT;
SELECT @ItSystem = id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Administrator',
  'http://rollekatalog.dk/administrator',
  'Denne rolle giver adgang til alt funktionaliteten i Rollekataloget',
  @ItSystem
);

DECLARE @SystemRoleAdminId BIGINT;
SELECT @SystemRoleAdminId = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/administrator';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Rolletildeler',
  'http://rollekatalog.dk/assigner',
  'Denne rolle giver adgang til at tildele og fjerne jobfunktionsroller og rollebuketter til brugere og enheder',
  @ItSystem
);

DECLARE @SystemRoleAssignerId BIGINT;
SELECT @SystemRoleAssignerId = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/assigner';

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES (
  'Administrator',
  'administrator',
  'Denne rolle give fuld adgang til Rollekataloget',
  @ItSystem
);

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES (
  'Rolletildeler',
  'tildeler',
  'Denne rolle giver adgang til at tildele roller til brugere og enheder',
  @ItSystem
);

DECLARE @UserRoleAdministratorId BIGINT;
SELECT @UserRoleAdministratorId = id FROM user_roles WHERE name = 'Administrator' AND it_system_id = @ItSystem;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@UserRoleAdministratorId, @SystemRoleAdminId);

DECLARE @UserRoleAssignerId BIGINT;
SELECT @UserRoleAssignerId = id FROM user_roles WHERE name = 'Rolletildeler' AND it_system_id = @ItSystem;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@UserRoleAssignerId, @SystemRoleAssignerId);
