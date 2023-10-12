package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;


public class HistoryRoleAssignmentRowMapper implements RowMapper<HistoryRoleAssignment> {
    @Override
    public HistoryRoleAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryRoleAssignment assignment = new HistoryRoleAssignment();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        assignment.setId(rs.getLong("id"));
        assignment.setDato(dato);
        assignment.setUserUuid(rs.getString("user_uuid"));
        assignment.setRoleId(rs.getLong("role_id"));
        assignment.setRoleItSystemId(rs.getLong("role_it_system_id"));
        assignment.setRoleRoleGroup(rs.getString("role_role_group"));
        assignment.setRoleRoleGroupId(zeroIsNull(rs.getLong("role_role_group_id")));
        assignment.setAssignedThroughType(AssignedThrough.valueOf(rs.getString("assigned_through_type")));
        assignment.setAssignedThroughUuid(rs.getString("assigned_through_uuid"));
        assignment.setAssignedThroughName(rs.getString("assigned_through_name"));
        assignment.setAssignedByUserId(rs.getString("assigned_by_user_id"));
        assignment.setAssignedByName(rs.getString("assigned_by_name"));
        assignment.setAssignedWhen(rs.getDate("assigned_when"));
        assignment.setPostponedConstraints(rs.getString("postponed_constraints"));
        assignment.setOrgUnitUuid(rs.getString("ou_uuid"));
        return assignment;
    }

}
