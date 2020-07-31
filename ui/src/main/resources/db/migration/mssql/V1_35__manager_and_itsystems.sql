ALTER TABLE ous ADD manager NVARCHAR(36) REFERENCES users(uuid);

CREATE TABLE ous_itsystems (
    ou_uuid        NVARCHAR(36) NOT NULL REFERENCES ous(uuid) ON DELETE CASCADE,
    itsystem_id    BIGINT NOT NULL REFERENCES it_systems(id) ON DELETE CASCADE
);
