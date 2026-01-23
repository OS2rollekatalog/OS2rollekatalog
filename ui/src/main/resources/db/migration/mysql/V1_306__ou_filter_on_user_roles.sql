ALTER TABLE user_roles ADD ou_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE ous_user_roles (
   id BIGINT PRIMARY KEY AUTO_INCREMENT NOT NULL,
   ou_uuid VARCHAR(255) NOT NULL,
   user_roles_id BIGINT NOT NULL,
   CONSTRAINT fk_oususerol_on_org_unit FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE,
   CONSTRAINT fk_oususerol_on_user_role FOREIGN KEY (user_roles_id) REFERENCES user_roles (id) ON DELETE CASCADE
);