package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentExclusion;
import dk.digitalidentity.rc.service.model.AssignedThrough;

public class HistoryOURoleAssignmentRowMapper implements ResultSetExtractor<List<HistoryOURoleAssignment>> {
	@Override
	public List<HistoryOURoleAssignment> extractData(ResultSet rs) throws SQLException, DataAccessException {
		Map<Long, HistoryOURoleAssignment> map = new LinkedHashMap<>();

		while (rs.next()) {
			long assignmentId = rs.getLong("assignment_id");
			HistoryOURoleAssignment assignment = map.get(assignmentId);
			if (assignment == null) {
				assignment = new HistoryOURoleAssignment();
				assignment.setId(assignmentId);
				assignment.setDato(rs.getDate("dato").toLocalDate());
				assignment.setOuUuid(rs.getString("ou_uuid"));
				assignment.setRoleId(rs.getLong("role_id"));
				assignment.setRoleName(rs.getString("role_name"));
				assignment.setRoleItSystemId(rs.getLong("role_it_system_id"));
				assignment.setRoleItSystemName(rs.getString("role_it_system_name"));
				assignment.setRoleRoleGroup(rs.getString("role_role_group"));
				assignment.setAssignedThroughType(rs.getString("assigned_through_type") != null
                        ? AssignedThrough.valueOf(rs.getString("assigned_through_type"))
                        : null);
				assignment.setAssignedThroughUuid(rs.getString("assigned_through_uuid"));
				assignment.setAssignedThroughName(rs.getString("assigned_through_name"));
				assignment.setRoleRoleGroupId(rs.getLong("role_role_group_id"));
				assignment.setInherit(rs.getBoolean("inherit"));
				assignment.setManager(rs.getBoolean("manager"));
				assignment.setSubstitutes(rs.getBoolean("substitutes"));
				assignment.setAssignedByUserId(rs.getString("assigned_by_user_id"));
				assignment.setAssignedByName(rs.getString("assigned_by_name"));
				assignment.setAssignedWhen(rs.getTimestamp("assigned_when").toLocalDateTime());
				assignment.setStartDate(rs.getDate("start_date") != null ? rs.getDate("start_date").toLocalDate() : null);
				assignment.setStopDate(rs.getDate("stop_date") != null ? rs.getDate("stop_date").toLocalDate() : null);

				assignment.setExclusions(new ArrayList<>());
				map.put(assignmentId, assignment);
			}

			// Map exclusion if exists
			Long exclusionId = rs.getLong("exclusion_id");
			if (exclusionId != 0) {
				HistoryOURoleAssignmentExclusion exclusion = new HistoryOURoleAssignmentExclusion();
				exclusion.setId(exclusionId);
				exclusion.setExclusionType(rs.getString("exclusion_type") != null
								? HistoryOURoleAssignmentExclusion.ExclusionType.valueOf(rs.getString("exclusion_type"))
								: null);
				exclusion.setUserUuids(rs.getString("user_uuids"));
				exclusion.setTitleUuids(rs.getString("title_uuids"));
				exclusion.setFunctionUuids(rs.getString("function_uuids"));
				assignment.getExclusions().add(exclusion);
			}
		}

		return new ArrayList<>(map.values());
	}
}
