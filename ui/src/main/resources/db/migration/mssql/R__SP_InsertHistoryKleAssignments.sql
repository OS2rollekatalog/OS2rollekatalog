-- Stored procedure for populating history_role_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryKleAssignments;
 
CREATE OR ALTER PROC SP_InsertHistoryKleAssignments 
AS
BEGIN	
	-- Insert kle assignments to temp table using a recursive common table expression
	WITH cte
	AS
	(
		SELECT 
			uuid
			,parent_uuid
			,uuid as child_uuid
		FROM ous ou
		WHERE
			active = 1

		UNION ALL

		SELECT 
			ou.uuid
			,ou.parent_uuid
			,cte.child_uuid as child_uuid
		FROM ous ou
		INNER JOIN cte ON cte.parent_uuid = ou.uuid
		WHERE
			ou.active = 1
	)
	SELECT DISTINCT
		child_uuid as ou_uuid
		,code
		,assignment_type
	INTO #tmpOrgKLEs
	FROM cte
	INNER JOIN ou_kles kle ON kle.ou_uuid = cte.uuid
	ORDER BY code;

    -- insert users' total kle-assignments to the final output table	
	SELECT
		uuid
		,assignment_type
		,code
	INTO #tmpKleAssignments
	FROM
	(
		SELECT DISTINCT u.uuid, kle.code, kle.assignment_type
		FROM users u
		INNER JOIN user_kles kle ON kle.user_uuid = u.uuid
	) sub

	INSERT INTO history_kle_assignments (dato, user_uuid, assignment_type, kle_values)
	SELECT 
		CURRENT_TIMESTAMP
		,s1.uuid
		,s1.assignment_type
		,STUFF(
				(	
					SELECT ',' + code 
					FROM #tmpKleAssignments s2			
					WHERE 
						s1.uuid = s2.uuid
						AND s1.assignment_type = s2.assignment_type
					FOR XML PATH ('')
				)
				, 1, 1, '') kle_values
	FROM #tmpKleAssignments s1
	GROUP BY
		uuid
		,assignment_type
	ORDER BY
		uuid
		,assignment_type;

	DROP TABLE #tmpKleAssignments;

	-- insert ous total kle-assignments to the final output table
	INSERT INTO history_ou_kle_assignments (dato, ou_uuid, assignment_type, kle_values)
	SELECT
		CURRENT_TIMESTAMP
		,s1.ou_uuid
		,s1.assignment_type
		,STUFF(
				(	
					SELECT ',' + code 
					FROM #tmpOrgKLEs s2			
					WHERE 
						s1.ou_uuid = s2.ou_uuid
						AND s1.assignment_type = s2.assignment_type
					FOR XML PATH ('')
				)
				, 1, 1, '') kle_values
	FROM
	#tmpOrgKLEs s1
	GROUP BY 
		ou_uuid
		,assignment_type
	ORDER BY
		ou_uuid
		,assignment_type;
		
	-- drop the temporary table again
	DROP TABLE #tmpOrgKLEs;
END
GO