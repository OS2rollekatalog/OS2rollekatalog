CREATE TABLE pending_kombit_updates (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    user_role_uuid          NVARCHAR(36),
    user_role_id            BIT NOT NULL,
    event_type              NVARCHAR(8) NOT NULL
);

ALTER TABLE user_roles ADD uuid NVARCHAR(36);
ALTER TABLE user_roles ADD delegated_from_cvr NVARCHAR(8);
