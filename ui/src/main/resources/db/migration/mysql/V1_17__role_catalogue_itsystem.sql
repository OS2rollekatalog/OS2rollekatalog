INSERT INTO it_systems (name, identifier, system_type) VALUES ('Rollekatalog', 'RoleCatalogue', 'SAML');

SELECT id INTO @it_system_id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Administrator',
  'http://rollekatalog.dk/administrator',
  'Denne rolle giver adgang til alt funktionaliteten i Rollekataloget',
  @it_system_id
);

SELECT id INTO @system_role_admin_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/administrator';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Rolletildeler',
  'http://rollekatalog.dk/assigner',
  'Denne rolle giver adgang til at tildele og fjerne jobfunktionsroller og rollebuketter til brugere og enheder',
  @it_system_id
);

SELECT id INTO @system_role_assigner_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/assigner';

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES ('Administrator', 'administrator', 'Denne rolle giver fuld adgang til Rollekataloget', @it_system_id);
INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES ('Rolletildeler', 'tildeler', 'Denne rolle giver adgang til at tildele roller til brugere og enheder', @it_system_id);

SELECT id INTO @user_role_admin_id FROM user_roles WHERE name = 'Administrator' AND it_system_id = @it_system_id;
SELECT id INTO @user_role_assigner_id FROM user_roles WHERE name = 'Rolletildeler' AND it_system_id = @it_system_id;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@user_role_admin_id, @system_role_admin_id);
INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@user_role_assigner_id, @system_role_assigner_id);
