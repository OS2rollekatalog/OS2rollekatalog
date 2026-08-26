ALTER TABLE req_request_log ADD COLUMN details_json TEXT NULL;
ALTER TABLE req_request_log ADD COLUMN acting_user_id VARCHAR(255) NULL;
ALTER TABLE req_request_log ADD COLUMN target_user_id VARCHAR(255) NULL;
