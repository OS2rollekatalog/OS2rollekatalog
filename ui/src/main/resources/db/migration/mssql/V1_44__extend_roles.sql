-- new role: KLE assigner

DECLARE @ItSystem BIGINT;
SELECT @ItSystem = id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id, role_type) VALUES (
  'KLE Administrator',
  'http://rollekatalog.dk/kleadmin',
  'Denne rolle giver adgang til at administrere KLE opmærkninger i Rollekataloget',
  @ItSystem,
  'BOTH'
);

DECLARE @SystemRoleId BIGINT;
SELECT @SystemRoleId = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/kleadmin';

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES (
	'KLE Administrator',
	'kleadmin',
	'Denne rolle giver adgang til at administrere KLE opmærkninger i Rollekataloget',
	@ItSystem
);

DECLARE @UserRoleId BIGINT;
SELECT @UserRoleId = id FROM user_roles WHERE name = 'KLE Administrator' AND it_system_id = @ItSystem;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@UserRoleId, @SystemRoleId);

-- add constraint to above role, as well as read only role

DECLARE @rc_it_systems_id BIGINT;
DECLARE @rc_system_roles_kleadmin_id BIGINT;
DECLARE @rc_system_roles_readonly_id BIGINT;
DECLARE @rc_orgenhed_id BIGINT;
DECLARE @rc_itystem_id BIGINT;

SELECT @rc_it_systems_id = id FROM it_systems WHERE identifier = 'RoleCatalogue';
SELECT @rc_system_roles_kleadmin_id = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/kleadmin';
SELECT @rc_system_roles_readonly_id = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/readaccess';
SELECT @rc_orgenhed_id = id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/orgunit/1';
SELECT @rc_itystem_id = id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/itsystem/1';

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_kleadmin_id, @rc_orgenhed_id, 0);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_readonly_id, @rc_orgenhed_id, 0);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_readonly_id, @rc_itystem_id, 0);
