-- introduce a new deleted field
ALTER TABLE users ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER active;

-- update deleted field with reverse value of old active field
UPDATE users SET deleted = (active IS false);

-- get rid of old active field
ALTER TABLE users DROP COLUMN active;

-- introduce a disabled field
ALTER TABLE users ADD COLUMN disabled TINYINT(1) NOT NULL DEFAULT 0 AFTER `deleted`;
