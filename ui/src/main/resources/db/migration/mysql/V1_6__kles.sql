CREATE TABLE kle (
    id                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    code                VARCHAR(8) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    active              BOOLEAN NOT NULL,
    parent              VARCHAR(8) NOT NULL,

    UNIQUE(code)
);
