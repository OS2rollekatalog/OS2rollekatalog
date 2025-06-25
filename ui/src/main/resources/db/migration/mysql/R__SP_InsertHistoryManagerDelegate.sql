-- Stored procedure for populating history_kle_assignments table

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryAttestationManagerDelegate $$

CREATE PROCEDURE SP_InsertHistoryAttestationManagerDelegate()
BEGIN

    INSERT INTO history_attestation_manager_delegate (date, delegate_uuid, delegate_name, manager_uuid, manager_name)
    SELECT CURRENT_TIMESTAMP, md.delegate_uuid, ud.name, md.manager_uuid, um.name
    FROM manager_delegate md
             LEFT JOIN users ud ON ud.uuid = md.delegate_uuid
             LEFT JOIN users um ON um.uuid = md.manager_uuid
    WHERE (md.indefinitely = TRUE AND md.from_date < CURRENT_TIMESTAMP)
       OR (CURRENT_TIMESTAMP BETWEEN md.from_date AND md.to_date);

END $$
DELIMITER ;
