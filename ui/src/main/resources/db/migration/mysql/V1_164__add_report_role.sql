SELECT id INTO @it_system_id FROM it_systems WHERE identifier = 'RoleCatalogue';

INSERT INTO system_roles (name, identifier, description, it_system_id) VALUES (
  'Rapport Adgang',
  'http://rollekatalog.dk/reportaccess',
  'Denne rolle giver adgang til se og udskrive rapporter',
  @it_system_id
);
