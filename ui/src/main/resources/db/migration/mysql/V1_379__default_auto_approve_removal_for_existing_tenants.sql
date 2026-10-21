-- Removal requests now default to auto-approve (SettingsService#isAutoApproveRemovalEnabled
-- returns true when no row exists for 'rolerequestAutoApproveRemoval'), so new tenants get
-- the safer/simpler behaviour out of the box. Tenants that already had anmod/godkend
-- ('RequestApproveEnabled' = true) enabled at the time of this migration must keep requiring
-- approval on removal, since their approval workflows are already in production use - so an
-- explicit 'false' row is inserted for them.
INSERT INTO setting (setting_key, setting_value)
SELECT 'rolerequestAutoApproveRemoval', 'false'
FROM setting
WHERE setting_key = 'RequestApproveEnabled' AND setting_value = 'true';
