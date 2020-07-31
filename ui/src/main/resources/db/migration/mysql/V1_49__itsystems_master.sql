CREATE TABLE it_systems_master (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    master_id                   VARCHAR(36) NOT NULL,
	name						VARCHAR(64) NOT NULL,
    last_modified               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE it_systems ADD COLUMN subscribed_to VARCHAR(36);