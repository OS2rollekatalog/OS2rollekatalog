-- Stored procedure for populating history_role_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryKleAssignments();
--
 
DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertTempOrgKLERecursive $$
CREATE PROCEDURE SP_InsertTempOrgKLERecursive (IN _ou_uuid varchar(36), IN _child_ou_uuid VARCHAR(36))
BEGIN
	DECLARE _parent_ou_uuid VARCHAR(36);
	IF _ou_uuid IS NOT NULL THEN
		INSERT INTO tmpOrgKLEs
        (
			SELECT _child_ou_uuid,kle.code,kle.assignment_type
            FROM ous ou
			INNER JOIN ou_kles kle ON kle.ou_uuid = ou.uuid
			WHERE ou.uuid = _ou_uuid
			AND (ou.inherit_kle = 1 or _ou_uuid = _child_ou_uuid)
		);

		SET _parent_ou_uuid = (SELECT parent_uuid FROM ous WHERE uuid = _ou_uuid);

		CALL SP_InsertTempOrgKLERecursive(_parent_ou_uuid,_child_ou_uuid);
    END IF;
END $$

DROP PROCEDURE IF EXISTS SP_InsertHistoryKleAssignments $$

CREATE PROCEDURE SP_InsertHistoryKleAssignments()
BEGIN
	DECLARE finished INTEGER DEFAULT 0;
	DECLARE ou_uuid VARCHAR(36);
	DECLARE cursorOus CURSOR FOR SELECT uuid FROM ous WHERE active = 1;
	DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;
	SET max_sp_recursion_depth=255;

    -- create temporary table to hold all org kle assignments
	DROP TABLE IF EXISTS tmpOrgKLEs;
	CREATE TEMPORARY TABLE tmpOrgKLEs
	(
		ou_uuid 		VARCHAR(36) NULL,
		code			VARCHAR(8) NOT NULL,
		assignment_type VARCHAR(16) NOT NULL
	);

    -- for each ou, fill out the above temp table using recursive stored procedure
	OPEN cursorOus;
	getOu: LOOP
		FETCH cursorOus INTO ou_uuid;
		IF finished = 1 THEN
			LEAVE getOu;
		END IF;
		CALL SP_InsertTempOrgKLERecursive(ou_uuid, ou_uuid);
	END LOOP getOu;
	CLOSE cursorOus;

    -- insert users' total kle-assignments to the final output table
	INSERT INTO history_kle_assignments (dato, user_uuid, assignment_type, kle_values)
	SELECT
		CURRENT_TIMESTAMP,
		uuid,
		assignment_type,
		GROUP_CONCAT(code ORDER BY code SEPARATOR ',' ) AS `kle_values`
	FROM
	(
		SELECT DISTINCT u.uuid, kle.code, kle.assignment_type
		FROM users u
		INNER JOIN user_kles kle ON kle.user_uuid = u.uuid

		UNION

		SELECT DISTINCT u.uuid, kle.code, kle.assignment_type
		FROM users u
		INNER JOIN positions p ON p.user_uuid = u.uuid
		INNER JOIN tmpOrgKLEs kle ON p.ou_uuid = kle.ou_uuid
	) sub
	GROUP BY uuid, assignment_type;

	-- insert ous total kle-assignments to the final output table
	INSERT INTO history_ou_kle_assignments (dato, ou_uuid, assignment_type, kle_values)
	SELECT
	CURRENT_TIMESTAMP,
	uuid,
	assignment_type,
	GROUP_CONCAT(code ORDER BY code SEPARATOR ',' ) AS `kle_values`
	FROM
	(
		SELECT DISTINCT ou.uuid, kle.code, kle.assignment_type
		FROM ous ou        
        INNER JOIN tmpOrgKLEs kle ON ou.uuid = kle.ou_uuid
        WHERE ou.active = 1
	) sub
	GROUP BY uuid,assignment_type;
	
	-- drop the temporary table again
    DROP TABLE tmpOrgKLEs;
END $$
DELIMITER ;
