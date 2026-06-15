ALTER TABLE historic_ou_assignment
    ADD COLUMN assigned_by_user_id VARCHAR(255) NULL,
    ADD COLUMN assigned_by_name    VARCHAR(255) NULL,
    ADD COLUMN start_date          DATE         NULL,
    ADD COLUMN stop_date           DATE         NULL;
