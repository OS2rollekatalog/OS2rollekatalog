CREATE TABLE user_permission
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid                VARCHAR(36) NOT NULL,
    section              VARCHAR(50) NOT NULL,
    permission               VARCHAR(50) NOT NULL,
    constrained_itsystem_ids TEXT,
    constrained_ou_uuids     TEXT,

    INDEX idx_user_uuid (user_uuid),
    INDEX idx_user_section_permission (user_uuid, section, permission),

    UNIQUE KEY uk_user_entity_permission (user_uuid, section, permission)
)