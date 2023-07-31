DROP VIEW IF EXISTS view_notifications_active;
DROP VIEW IF EXISTS view_notifications;

GO

CREATE VIEW view_notifications AS (
    SELECT
		id,
		notification_type,
		affected_entity_uuid,
		affected_entity_type,
		affected_entity_name,
		active,
		message,
		SUBSTRING(CONVERT(NVARCHAR(25), created, 120), 1, 19) AS created,
		SUBSTRING(CONVERT(NVARCHAR(25), last_updated, 120), 1, 19) AS last_updated,
		admin_uuid,
		admin_name
	FROM notifications
);
