CREATE TABLE req_org_unit_user_role_cache (
  id bigint IDENTITY (1, 1) NOT NULL,
   ou_uuid NVARCHAR(36),
   user_role_id bigint,
   CONSTRAINT pk_org_unit_user_role_cache PRIMARY KEY (id)
)
GO

ALTER TABLE req_org_unit_user_role_cache ADD CONSTRAINT FK_ORG_UNIT_USER_ROLE_CACHE_ON_OU_UUID FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE
GO

ALTER TABLE req_org_unit_user_role_cache ADD CONSTRAINT FK_ORG_UNIT_USER_ROLE_CACHE_ON_USER_ROLE FOREIGN KEY (user_role_id) REFERENCES user_roles (id) ON DELETE CASCADE
GO

CREATE TABLE req_org_unit_role_group_cache (
  id bigint IDENTITY (1, 1) NOT NULL,
   ou_uuid NVARCHAR(36),
   role_group_id bigint,
   CONSTRAINT pk_org_unit_role_group_cache PRIMARY KEY (id)
)
GO

ALTER TABLE req_org_unit_role_group_cache ADD CONSTRAINT FK_ORG_UNIT_ROLE_GROUP_CACHE_ON_OU_UUID FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE
GO

ALTER TABLE req_org_unit_role_group_cache ADD CONSTRAINT FK_ORG_UNIT_ROLE_GROUP_CACHE_ON_ROLE_GROUP FOREIGN KEY (role_group_id) REFERENCES rolegroup (id) ON DELETE CASCADE
GO