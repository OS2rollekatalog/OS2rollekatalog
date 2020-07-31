CREATE TABLE user_kles (
    id                  BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    user_uuid           NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE,
    code                NVARCHAR(8),
    assignment_type     NVARCHAR(16)
);
