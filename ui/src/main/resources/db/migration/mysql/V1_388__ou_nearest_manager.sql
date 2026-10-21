CREATE TABLE ou_nearest_manager (
    ou_uuid                  VARCHAR(36) NOT NULL PRIMARY KEY,
    nearest_manager_uuid     VARCHAR(36) NOT NULL,
    nearest_manager_ou_uuid  VARCHAR(36) NOT NULL,
    next_manager_uuid        VARCHAR(36) NULL,
    next_manager_ou_uuid     VARCHAR(36) NULL,
    CONSTRAINT fk_ou_nearest_manager_ou FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE
);
