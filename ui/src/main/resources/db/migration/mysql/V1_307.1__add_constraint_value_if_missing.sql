ALTER TABLE req_request_postponed_constraint
    ADD COLUMN IF NOT EXISTS constraint_value TEXT NOT NULL;