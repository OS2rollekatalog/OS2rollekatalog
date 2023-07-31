CREATE TABLE se_number (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	name						VARCHAR(255) NOT NULL,
	code						VARCHAR(255) NOT NULL
);

CREATE TABLE p_number (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	name						VARCHAR(255) NOT NULL,
	code						VARCHAR(255) NOT NULL
);