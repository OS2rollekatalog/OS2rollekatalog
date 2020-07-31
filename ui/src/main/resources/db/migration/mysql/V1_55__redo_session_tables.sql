DROP TABLE IF EXISTS SPRING_SESSION_ATTRIBUTES;
DROP TABLE IF EXISTS SPRING_SESSION;

CREATE TABLE SPRING_SESSION (
    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT, -- this is custom added, to ensure PKs are auto incremented (to avoid deadlocks)
    primary_id CHAR(36) NOT NULL,                  -- this is the old PK, but no more, it is just a unique column
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100)
);

-- ensure uniqueness (we still use it as a FK on the attribute table)
CREATE UNIQUE INDEX not_really_pk ON SPRING_SESSION (primary_id);
CREATE UNIQUE INDEX spring_session_ix1 ON SPRING_SESSION (session_id);
CREATE INDEX spring_session_ix2 ON SPRING_SESSION (expiry_time);
CREATE INDEX spring_session_ix3 ON SPRING_SESSION (principal_name);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BLOB NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id,attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES SPRING_SESSION(primary_id) ON DELETE CASCADE
);
