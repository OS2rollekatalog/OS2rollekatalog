CREATE TABLE user_kles (
	id								BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	user_uuid						VARCHAR(36) NOT NULL,
	code							VARCHAR(8),
	assignment_type					VARCHAR(16),

	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);