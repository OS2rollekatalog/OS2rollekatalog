ALTER TABLE it_systems ADD COLUMN can_edit_through_api BIT NULL;

UPDATE it_systems SET can_edit_through_api = 0;

ALTER TABLE it_systems MODIFY COLUMN can_edit_through_api BIT NOT NULL DEFAULT 0;