DECLARE @ItSystem BIGINT;
SELECT @ItSystem = id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Attesterings Administrator',
  'http://rollekatalog.dk/attestationadmin',
  'Denne rolle giver adgang til administrator adgang til attesterings delen af rollekataloget',
  @ItSystem
);

