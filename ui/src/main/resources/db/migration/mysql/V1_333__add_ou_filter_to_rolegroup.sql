ALTER TABLE rolegroup ADD COLUMN ou_filter_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE ous_rolegroup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT NOT NULL,
    ou_uuid VARCHAR(255) NOT NULL,
    rolegroup_id BIGINT NOT NULL,
    CONSTRAINT fk_ousrolegroup_on_org_unit FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_ousrolegroup_on_user_role FOREIGN KEY (rolegroup_id) REFERENCES rolegroup (id) ON DELETE CASCADE
);