-- new role: KLE assigner

SELECT id INTO @it_system_id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id, role_type) VALUES (
  'KLE Administrator',
  'http://rollekatalog.dk/kleadmin',
  'Denne rolle giver adgang til at administrere KLE opmærkninger i Rollekataloget',
  @it_system_id,
  'BOTH'
);

SELECT id INTO @system_role_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/kleadmin';

INSERT INTO user_roles (name, identifier, description, it_system_id) VALUES (
  'KLE Administrator',
  'kleadmin',
  'Denne rolle giver adgang til at administrere KLE opmærkninger i Rollekataloget',
  @it_system_id
);

SELECT id INTO @user_role_id FROM user_roles WHERE name = 'KLE Administrator' AND it_system_id = @it_system_id;

INSERT INTO system_role_assignments (user_role_id, system_role_id) VALUES (@user_role_id, @system_role_id);

-- add constraint to above role, as well as read only role

SELECT id INTO @rc_it_systems_id FROM it_systems WHERE identifier = 'RoleCatalogue';
SELECT id INTO @rc_system_roles_kleadmin_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/kleadmin';
SELECT id INTO @rc_system_roles_readonly_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/readaccess';
SELECT id INTO @rc_orgenhed_id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/orgunit/1';
SELECT id INTO @rc_itystem_id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/itsystem/1';

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_kleadmin_id, @rc_orgenhed_id, false);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_readonly_id, @rc_orgenhed_id, false);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_readonly_id, @rc_itystem_id, false);
