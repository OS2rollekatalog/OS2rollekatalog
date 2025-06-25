BEGIN TRANSACTION;

CREATE TABLE manager_delegate
(
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    delegate_uuid UNIQUEIDENTIFIER NOT NULL,
    manager_uuid UNIQUEIDENTIFIER NOT NULL,
    from_date DATE NOT NULL DEFAULT CAST(GETDATE() AS DATE),
    to_date DATE NULL,
    indefinitely BIT DEFAULT 0,
    CONSTRAINT fk_manager_delegate_delegate FOREIGN KEY (delegate_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_manager_delegate_manager FOREIGN KEY (manager_uuid) REFERENCES users (uuid) ON DELETE CASCADE
);

CREATE NONCLUSTERED INDEX idx_manager_delegate_from_date ON manager_delegate (from_date);
CREATE NONCLUSTERED INDEX idx_manager_delegate_to_date ON manager_delegate (to_date);

CREATE TABLE history_attestation_manager_delegate
(
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    date DATE NOT NULL,
    delegate_uuid UNIQUEIDENTIFIER NOT NULL,
    delegate_name VARCHAR(255) NOT NULL,
    manager_uuid UNIQUEIDENTIFIER NOT NULL,
    manager_name VARCHAR(255) NOT NULL
);

COMMIT;
