INSERT INTO constraint_types(uuid, entity_id, name, ui_type) VALUES('d9bd772b-32b0-4a56-b616-34fa3b465c5d', 'http://digital-identity.dk/constraints/itsystem/1', 'It-system', 'REGEX');
INSERT INTO constraint_types(uuid, entity_id, name, ui_type) VALUES('49be31cf-a1c5-4be1-bb96-73e693cce3ef', 'http://digital-identity.dk/constraints/orgunit/1', 'Enhed', 'REGEX');

DECLARE @rc_it_systems_id BIGINT;
DECLARE @rc_system_roles_assigner_id BIGINT;
DECLARE @rc_orgenhed_id BIGINT;
DECLARE @rc_itystem_id BIGINT;

SELECT @rc_it_systems_id = id FROM it_systems WHERE identifier = 'RoleCatalogue';
SELECT @rc_system_roles_assigner_id = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/assigner';
SELECT @rc_orgenhed_id = id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/orgunit/1';
SELECT @rc_itystem_id = id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/itsystem/1';

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_assigner_id, @rc_orgenhed_id, 0);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_assigner_id, @rc_itystem_id, 0);
