START TRANSACTION;

CREATE TABLE manager_delegate
(
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    delegate_uuid VARCHAR(36) NOT NULL,
    manager_uuid  VARCHAR(36) NOT NULL,
    from_date     DATE        NOT NULL DEFAULT CURRENT_DATE,
    to_date       DATE NULL,
    indefinitely  BOOLEAN              DEFAULT FALSE,
    CONSTRAINT fk_manager_delegate_delegate FOREIGN KEY (delegate_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_manager_delegate_manager FOREIGN KEY (manager_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    INDEX         idx_manager_delegate_from_date (from_date),
    INDEX         idx_manager_delegate_to_date (to_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

CREATE TABLE history_attestation_manager_delegate
(
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    date          DATE         NOT NULL,
    delegate_uuid VARCHAR(36)  NOT NULL,
    delegate_name VARCHAR(255) NOT NULL,
    manager_uuid  VARCHAR(36)  NOT NULL,
    manager_name  VARCHAR(255) NOT NULL
);

COMMIT;