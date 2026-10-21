CREATE TABLE user_user_role_reason (
    user_user_role_id  BIGINT NOT NULL PRIMARY KEY,
    reason             VARCHAR(768) NOT NULL,
    CONSTRAINT fk_user_user_role_reason FOREIGN KEY (user_user_role_id) REFERENCES user_roles_mapping (id) ON DELETE CASCADE
);

CREATE TABLE user_role_group_reason (
    user_role_group_id BIGINT NOT NULL PRIMARY KEY,
    reason             VARCHAR(768) NOT NULL,
    CONSTRAINT fk_user_role_group_reason FOREIGN KEY (user_role_group_id) REFERENCES user_rolegroups (id) ON DELETE CASCADE
);

INSERT INTO user_user_role_reason (user_user_role_id, reason)
    SELECT id, request_reason FROM user_roles_mapping WHERE TRIM(request_reason) <> '';

INSERT INTO user_role_group_reason (user_role_group_id, reason)
    SELECT id, request_reason FROM user_rolegroups WHERE TRIM(request_reason) <> '';

ALTER TABLE user_roles_mapping
    DROP COLUMN IF EXISTS request_reason, ALGORITHM=INSTANT;
ALTER TABLE user_rolegroups
    DROP COLUMN IF EXISTS request_reason, ALGORITHM=INSTANT;
ALTER TABLE current_assignment
    DROP COLUMN IF EXISTS request_reason, ALGORITHM=INSTANT;
