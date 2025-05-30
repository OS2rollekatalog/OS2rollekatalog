
DECLARE @ItSystem BIGINT;
SELECT @ItSystem = id FROM it_systems WHERE identifier = 'RoleCatalogue';

-- system role

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Global Rolletildeler',
  'http://rollekatalog.dk/global_assigner',
  'Denne rolle giver adgang til at tildele og fjerne jobfunktionsroller og rollebuketter til brugere og enheder, samt adgang til auditloggen.',
  @ItSystem
);

DECLARE @system_role_global_assigner_id BIGINT;
SELECT @system_role_global_assigner_id = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/global_assigner';

-- user role

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES ('Global Rolletildeler', 'global_tildeler',
                                                                             'Denne rolle giver adgang til at tildele og fjerne jobfunktionsroller og rollebuketter til brugere og enheder, samt adgang til auditloggen.', @ItSystem);


DECLARE @user_role_global_assigner_id BIGINT;
SELECT @user_role_global_assigner_id = id FROM user_roles WHERE name = 'Global Rolletildeler' AND it_system_id = @ItSystem;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@user_role_global_assigner_id, @system_role_global_assigner_id);
