ALTER TABLE positions ADD COLUMN do_not_inherit BOOLEAN NOT NULL DEFAULT false;

UPDATE positions p
INNER JOIN users u ON p.user_uuid = u.uuid
SET p.do_not_inherit = u.do_not_inherit;

ALTER TABLE users DROP COLUMN do_not_inherit;