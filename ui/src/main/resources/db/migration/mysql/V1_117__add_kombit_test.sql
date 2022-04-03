INSERT INTO it_systems (name, identifier, system_type)
SELECT * FROM (SELECT 'KOMBIT Ekstern Test', 'KOMBITTEST', 'KOMBIT') AS tmp
WHERE NOT EXISTS (
    SELECT name FROM it_systems WHERE identifier = 'KOMBITTEST'
) LIMIT 1;