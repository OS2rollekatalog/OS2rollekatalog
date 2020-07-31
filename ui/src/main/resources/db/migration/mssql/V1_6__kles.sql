CREATE TABLE kle (
    id                  BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    code                NVARCHAR(8) NOT NULL UNIQUE,
    name                NVARCHAR(255) NOT NULL,
    active              BIT NOT NULL,
    parent              NVARCHAR(8) NOT NULL
);
