CREATE OR REPLACE VIEW view_notifications_active AS SELECT
		id,
		notification_type,
		affected_entity_uuid,
		affected_entity_type,
		affected_entity_name,
		active,
		message,
		SUBSTRING(created, 1, 19) AS created,
		SUBSTRING(last_updated, 1, 19) AS last_updated,
		admin_uuid,
		admin_name
	FROM notifications n
	WHERE n.active = 1;

CREATE OR REPLACE VIEW view_notifications_inactive AS SELECT
		id,
		notification_type,
		affected_entity_uuid,
		affected_entity_type,
		affected_entity_name,
		active,
		message,
		SUBSTRING(created, 1, 19) AS created,
		SUBSTRING(last_updated, 1, 19) AS last_updated,
		admin_uuid,
		admin_name
	FROM notifications n
	WHERE n.active = 0;