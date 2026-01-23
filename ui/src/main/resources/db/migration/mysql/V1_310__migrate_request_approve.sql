
-- 1) Tilføj midlertidig kolonne til at holde gamle request_approve-id'er
ALTER TABLE req_role_request
    ADD COLUMN old_request_approve_id BIGINT NULL,
    ADD INDEX idx_old_request_approve_id (old_request_approve_id);

-- 2) Indsæt rækker fra request_approve OG bevar mapping til old_request_approve_id
INSERT INTO req_role_request (
    requester_uuid,
    reciever_uuid,
    ou_uuid,
    rolegroup_id,
    user_role_id,
    reason,
    reject_reason,
    role_assigner_notified,
    status,
    request_timestamp,
    status_timestamp,
    email_sent,
    request_action,
    request_group_identifier,
    old_request_approve_id
)
SELECT
    ra.requester_uuid,
    ra.requested_for_uuid,
    ra.ou_uuid,
    CASE WHEN ra.role_type = 'ROLEGROUP' THEN ra.role_id ELSE NULL END,
    CASE WHEN ra.role_type = 'USERROLE'  THEN ra.role_id ELSE NULL END,
    ra.reason,
    ra.reject_reason,
    ra.role_assigner_notified,
    ra.status,
    ra.request_timestamp,
    ra.status_timestamp,
    ra.email_sent,
    ra.request_action,
    NULL,
    ra.id
FROM request_approve ra
         JOIN users u_req ON u_req.uuid = ra.requester_uuid
         JOIN users u_rec ON u_rec.uuid = ra.requested_for_uuid
         LEFT JOIN ous o ON o.uuid = ra.ou_uuid
WHERE (ra.ou_uuid IS NULL OR o.uuid IS NOT NULL);

-- 3) Indsæt postponed constraints ved at join'e på mapping (kun de med mapping)
INSERT INTO req_request_postponed_constraint (constraint_value, role_request_id, constraint_type_id, system_role_id)
SELECT p.constraint_value, r.id AS role_request_id, p.constraint_type_id, p.system_role_id
FROM request_approve_postponed_constraints p
         JOIN req_role_request r ON r.old_request_approve_id = p.request_approve_id;

-- 4) Ryd op: drop gamle tabeller
DROP TABLE request_approve_postponed_constraints;
DROP TABLE request_approve;

-- 5) Fjern midlertidig kolonne og dens index
ALTER TABLE req_role_request
    DROP INDEX idx_old_request_approve_id,
    DROP COLUMN old_request_approve_id;
