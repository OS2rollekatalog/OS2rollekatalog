-- Create table only if it doesn't exist
CREATE TABLE IF NOT EXISTS user_ou_function (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ou_uuid VARCHAR(36) NOT NULL,
    user_uuid VARCHAR(36) NOT NULL,
    function_uuid VARCHAR(36) NOT NULL,
    CONSTRAINT fk_user_ou_function_ou FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_user_ou_function_user FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_user_ou_function_function FOREIGN KEY (function_uuid) REFERENCES functions(uuid) ON DELETE CASCADE
);

-- Only migrate data if source table still exists
INSERT INTO user_ou_function (ou_uuid, user_uuid, function_uuid)
SELECT
    p.ou_uuid,
    p.user_uuid,
    pfa.function_uuid
FROM position_function_assignments pfa
INNER JOIN positions p ON pfa.position_id = p.id
WHERE p.ou_uuid IS NOT NULL
  AND p.user_uuid IS NOT NULL
  AND pfa.function_uuid IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM INFORMATION_SCHEMA.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'position_function_assignments'
  );

-- Drop old table if it exists
DROP TABLE IF EXISTS position_function_assignments;