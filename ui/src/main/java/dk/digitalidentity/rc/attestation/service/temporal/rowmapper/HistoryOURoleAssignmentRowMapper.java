package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;

public class HistoryOURoleAssignmentRowMapper implements RowMapper<HistoryOURoleAssignment> {
    @Override
    public HistoryOURoleAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryOURoleAssignment historyOURoleAssignment = new HistoryOURoleAssignment();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        historyOURoleAssignment.setId(rs.getLong("id"));
        historyOURoleAssignment.setDato(dato);
        historyOURoleAssignment.setOuUuid(rs.getString("ou_uuid"));
        historyOURoleAssignment.setRoleId(rs.getLong("role_id"));
        historyOURoleAssignment.setInherit(rs.getBoolean("inherit"));
        historyOURoleAssignment.setRoleItSystemId(rs.getLong("role_it_system_id"));
        historyOURoleAssignment.setRoleRoleGroup(rs.getString("role_role_group"));
        historyOURoleAssignment.setRoleRoleGroupId(zeroIsNull(rs.getLong("role_role_group_id")));
        historyOURoleAssignment.setAssignedThroughType(AssignedThrough.valueOf(rs.getString("assigned_through_type")));
        historyOURoleAssignment.setAssignedThroughUuid(rs.getString("assigned_through_uuid"));
        historyOURoleAssignment.setAssignedThroughName(rs.getString("assigned_through_name"));
        historyOURoleAssignment.setAssignedByUserId(rs.getString("assigned_by_user_id"));
        historyOURoleAssignment.setAssignedByName(rs.getString("assigned_by_name"));
        historyOURoleAssignment.setAssignedWhen(rs.getTimestamp("assigned_when"));
        return historyOURoleAssignment;
    }
}
