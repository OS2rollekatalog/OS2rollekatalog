
DECLARE @it_system_id INT;
SELECT @it_system_id = id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id, role_type)
VALUES (
    'Anmod/Godkend Bemyndiget',
    'http://rollekatalog.dk/requestauthorized',
    'Denne rolle giver adgang til frit at oprette og godkende rolleanmodninger',
    @it_system_id,
    'BOTH'
);

DECLARE @system_role_id INT;
SELECT @system_role_id = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/requestauthorized';

DECLARE @rc_it_systems_id INT;
DECLARE @rc_system_roles_authorized_id INT;
DECLARE @rc_orgenhed_id INT;
DECLARE @rc_itsystem_id INT;

SELECT @rc_it_systems_id = id FROM it_systems WHERE identifier = 'RoleCatalogue';
SELECT @rc_system_roles_authorized_id = id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/requestauthorized';
SELECT @rc_orgenhed_id = id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/orgunit/1';
SELECT @rc_itsystem_id = id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/itsystem/1';

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory)
VALUES (@rc_system_roles_authorized_id, @rc_orgenhed_id, 0);

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory)
VALUES (@rc_system_roles_authorized_id, @rc_itsystem_id, 0);
