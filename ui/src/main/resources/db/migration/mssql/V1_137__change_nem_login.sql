DROP TABLE pending_nemlogin_updates;

--ALTER TABLE user_roles DROP COLUMN nemlogin_constraint_type;
--ALTER TABLE user_roles DROP COLUMN nemlogin_constraint_value;

INSERT INTO constraint_types (uuid, entity_id, name, ui_type, regex, description) VALUES ('cc7fdb54-2219-4eca-befc-100fe6451123', 'https://nemlogin.dk/constraints/pnr/1', 'p-nummer', 'REGEX', '^[0-9]{10}$', 'Rolletildelingen bliver afgrænset på pnr');
INSERT INTO constraint_types (uuid, entity_id, name, ui_type, regex, description) VALUES ('55726374-72fe-46e1-8531-ba2ed8e3f0d0', 'https://nemlogin.dk/constraints/senr/1', 'se-nummer', 'REGEX', '^[0-9]{8}$', 'Rolletildelingen bliver afgrænset på senr');

CREATE TABLE dirty_nemlogin_users (
    id                                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    tts                                     DATETIME2 NOT NULL DEFAULT GETDATE(),
    user_uuid                               NVARCHAR(36) NOT NULL,

    CONSTRAINT fk_dirty_nemlogin_users_user FOREIGN KEY (user_uuid) REFERENCES users(uuid)
);