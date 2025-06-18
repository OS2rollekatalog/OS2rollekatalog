START TRANSACTION;

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
            request_group_identifier)
    SELECT
        requester_uuid,
        requested_for_uuid,
        ou_uuid,
        CASE WHEN role_type = 'ROLEGROUP' THEN role_id ELSE NULL END,
        CASE WHEN role_type = 'USERROLE' THEN role_id ELSE NULL END,
        reason,
        reject_reason,
        role_assigner_notified,
        status,
        request_timestamp,
        status_timestamp,
        email_sent,
        request_action,
        NULL
    FROM request_approve;

    INSERT INTO req_request_postponed_constraint (constraint_value, role_request_id, constraint_type_id, system_role_id)
    SELECT constraint_value, request_approve_id, constraint_type_id, system_role_id
    FROM request_approve_postponed_constraints;

    DROP TABLE request_approve_postponed_constraints;
    DROP TABLE request_approve;

COMMIT;
