-- remove bad rows from table
DELETE FROM current_assignment WHERE assignment_user_role_id IS NULL AND assignment_role_group_id IS NULL;

SET SESSION foreign_key_checks = 0;
SET SESSION lock_wait_timeout = 10;   -- fail fast instead of stalling the cluster

ALTER TABLE current_assignment
    DROP FOREIGN KEY fk_current_assignment_user_uuid,
    DROP FOREIGN KEY fk_current_assignment_user_role_id,
    DROP FOREIGN KEY fk_current_assignment_it_system_id,
    DROP FOREIGN KEY fk_current_assignment_role_group_id,
    ALGORITHM=NOCOPY, LOCK=NONE;

ALTER TABLE current_assignment
    ADD CONSTRAINT fk_current_assignment_user_uuid FOREIGN KEY (assignment_user_uuid)
        REFERENCES users (uuid) ON DELETE CASCADE,
    ADD CONSTRAINT fk_current_assignment_user_role_id FOREIGN KEY (assignment_user_role_id)
        REFERENCES user_roles (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_current_assignment_it_system_id FOREIGN KEY (assignment_it_system_id)
        REFERENCES it_systems (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_current_assignment_role_group_id FOREIGN KEY (assignment_role_group_id)
        REFERENCES rolegroup (id) ON DELETE CASCADE,
    ALGORITHM=NOCOPY, LOCK=NONE;

SET SESSION foreign_key_checks = 1;
