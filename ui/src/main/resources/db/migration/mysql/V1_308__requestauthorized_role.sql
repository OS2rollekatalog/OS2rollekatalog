
SELECT id INTO @it_system_id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id, role_type) VALUES (
  'Anmod/Godkend Bemyndiget',
  'http://rollekatalog.dk/requestauthorized',
  'Denne rolle giver adgang til frit at oprette og godkende rolleanmodninger',
  @it_system_id,
  'BOTH'
);

SELECT id INTO @system_role_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/requestauthorized';
SELECT id INTO @rc_it_systems_id FROM it_systems WHERE identifier = 'RoleCatalogue';
SELECT id INTO @rc_system_roles_authroized_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/requestauthorized';
SELECT id INTO @rc_orgenhed_id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/orgunit/1';
SELECT id INTO @rc_itystem_id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/itsystem/1';

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory)
VALUES (@rc_system_roles_authroized_id, @rc_orgenhed_id, false);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory)
VALUES (@rc_system_roles_authroized_id, @rc_itystem_id, false);

