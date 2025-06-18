ALTER TABLE user_roles ADD ou_filter_enabled bit
GO

CREATE TABLE ous_user_roles (
   id bigint IDENTITY (1, 1) NOT NULL,
   ou_uuid NVARCHAR(36) NOT NULL,
   user_roles_id bigint NOT NULL,
   CONSTRAINT pk_ous_user_roles PRIMARY KEY (id)
)
GO

ALTER TABLE ous_user_roles ADD CONSTRAINT fk_oususerol_on_org_unit FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE
GO

ALTER TABLE ous_user_roles ADD CONSTRAINT fk_oususerol_on_user_role FOREIGN KEY (user_roles_id) REFERENCES user_roles (id) ON DELETE CASCADE
GO