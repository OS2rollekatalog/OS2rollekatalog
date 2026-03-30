ALTER TABLE current_assignment
    ADD INDEX idx_current_assignment_user_dates2 (assignment_user_uuid, start_date, stop_date),
    ADD INDEX idx_current_assignment_dates (start_date, stop_date);