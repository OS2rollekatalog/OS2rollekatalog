SELECT id INTO @it_system_id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Attesterings Administrator',
  'http://rollekatalog.dk/attestationadmin',
  'Denne rolle giver adgang til administrator adgang til attesterings delen af rollekataloget',
  @it_system_id
);
