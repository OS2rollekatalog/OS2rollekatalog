-- UserRole.SystemRoles
ALTER TABLE system_role_assignments ADD assigned_by_user_id NVARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE system_role_assignments ADD assigned_by_name NVARCHAR(255) NOT NULL DEFAULT 'Systembruger';
ALTER TABLE system_role_assignments ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT '1979-05-21';

-- RoleGroup.UserRoles
SELECT * INTO #tmp_rolegroup_roles FROM rolegroup_roles;

TRUNCATE TABLE rolegroup_roles;

ALTER TABLE rolegroup_roles ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE rolegroup_roles ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE rolegroup_roles ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE rolegroup_roles ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO rolegroup_roles (role_id, rolegroup_id, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT role_id, rolegroup_id, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_rolegroup_roles;

-- User.UserRoles
SELECT * INTO #tmp_user_roles_mapping FROM user_roles_mapping;

TRUNCATE TABLE user_roles_mapping;

ALTER TABLE user_roles_mapping ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE user_roles_mapping ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE user_roles_mapping ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE user_roles_mapping ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO user_roles_mapping (role_id, user_uuid, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT role_id, user_uuid, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_user_roles_mapping;

-- User.RoleGroups
  
SELECT * INTO #tmp_user_rolegroups FROM user_rolegroups;

TRUNCATE TABLE user_rolegroups;

ALTER TABLE user_rolegroups ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE user_rolegroups ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE user_rolegroups ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE user_rolegroups ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO user_rolegroups (rolegroup_id, user_uuid, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT rolegroup_id, user_uuid, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_user_rolegroups;

-- Position.UserRoles
SELECT * INTO #tmp_position_roles FROM position_roles;

TRUNCATE TABLE position_roles;

ALTER TABLE position_roles ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE position_roles ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE position_roles ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE position_roles ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO position_roles (position_id, role_id, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT position_id, role_id, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_position_roles;

-- Position.RoleGroups
SELECT * INTO #tmp_position_rolegroups FROM position_rolegroups;

TRUNCATE TABLE position_rolegroups;

ALTER TABLE position_rolegroups ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE position_rolegroups ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE position_rolegroups ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE position_rolegroups ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO position_rolegroups (position_id, rolegroup_id, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT position_id, rolegroup_id, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_position_rolegroups;

-- OrgUnit.UserRoles
SELECT * INTO #tmp_ou_roles FROM ou_roles;

TRUNCATE TABLE ou_roles;

ALTER TABLE ou_roles ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE ou_roles ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE ou_roles ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO ou_roles (ou_uuid, role_id, inherit, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT ou_uuid, role_id, inherit, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_ou_roles;

-- OrgUnit.RoleGroups
SELECT * INTO #tmp_ou_rolegroups FROM ou_rolegroups;

TRUNCATE TABLE ou_rolegroups;

ALTER TABLE ou_rolegroups ADD assigned_by_user_id NVARCHAR(255) NOT NULL;
ALTER TABLE ou_rolegroups ADD assigned_by_name NVARCHAR(255) NOT NULL;
ALTER TABLE ou_rolegroups ADD assigned_timestamp DATETIME2 NOT NULL DEFAULT GETDATE();

INSERT INTO ou_rolegroups (ou_uuid, rolegroup_id, inherit, assigned_by_user_id, assigned_by_name, assigned_timestamp) 
  SELECT ou_uuid, rolegroup_id, inherit, 'system', 'Systembruger', '1979-05-21'
  FROM #tmp_ou_rolegroups;

-- Positions
ALTER TABLE positions ADD created DATETIME2 NOT NULL DEFAULT GETDATE();;
