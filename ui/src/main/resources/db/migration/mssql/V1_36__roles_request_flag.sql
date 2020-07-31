ALTER TABLE user_roles ADD can_request BIT NOT NULL DEFAULT 0;
ALTER TABLE rolegroup ADD can_request BIT NOT NULL DEFAULT 0;
