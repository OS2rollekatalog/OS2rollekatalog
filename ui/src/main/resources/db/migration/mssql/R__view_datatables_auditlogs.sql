CREATE VIEW view_datatables_auditlogs AS (
	SELECT a.id, a.timestamp, a.username, a.entity_type, a.entity_name, a.event_type, a.secondary_entity_name
	FROM audit_log a
	WHERE a.event_type in (
	  'ASSIGN_KLE', 'REMOVE_KLE',
	  'ASSIGN_SYSTEMROLE', 'REMOVE_SYSTEMROLE',
	  'ASSIGN_ROLE_GROUP', 'REMOVE_ROLE_GROUP',
	  'ASSIGN_USER_ROLE', 'REMOVE_USER_ROLE',
	  'AUTH_MANAGER_ADDED', 'AUTH_MANAGER_REMOVED', 'REQUEST_ROLE_FOR', 'APPROVE_REQUEST', 'REJECT_REQUEST',
	  'ATTESTED_ORGUNIT')
);