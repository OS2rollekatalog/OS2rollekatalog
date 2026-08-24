ALTER TABLE it_systems ADD COLUMN contact_notifications_initialized BOOLEAN NOT NULL DEFAULT 0;

-- it-systems that already have baseline rows have already gone through a bootstrap run;
-- without this backfill, DEFAULT 0 would re-suppress their next legitimate change notification
UPDATE it_systems s SET contact_notifications_initialized = 1
WHERE EXISTS (SELECT 1 FROM manual_assignment_notification_map m
              JOIN user_roles r ON r.id = m.user_role_id WHERE r.it_system_id = s.id);
