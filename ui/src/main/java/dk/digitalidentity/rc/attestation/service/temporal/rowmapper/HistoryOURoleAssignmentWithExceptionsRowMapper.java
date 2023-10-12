package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;

public class HistoryOURoleAssignmentWithExceptionsRowMapper implements RowMapper<HistoryOURoleAssignmentWithExceptions> {
    @Override
    public HistoryOURoleAssignmentWithExceptions mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryOURoleAssignmentWithExceptions historyOURoleAssignmentWithExceptions = new HistoryOURoleAssignmentWithExceptions();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        historyOURoleAssignmentWithExceptions.setId(rs.getLong("id"));
        historyOURoleAssignmentWithExceptions.setDato(dato);
        historyOURoleAssignmentWithExceptions.setOuUuid(rs.getString("ou_uuid"));
        historyOURoleAssignmentWithExceptions.setUserUuids(RowMapperUtils.explodeToList(rs.getString("user_uuids")));
        historyOURoleAssignmentWithExceptions.setRoleId(rs.getLong("role_id"));
        historyOURoleAssignmentWithExceptions.setRoleName(rs.getString("role_name"));
        historyOURoleAssignmentWithExceptions.setRoleItSystemId(rs.getLong("role_it_system_id"));
        historyOURoleAssignmentWithExceptions.setRoleItSystemName(rs.getString("role_it_system_name"));
        historyOURoleAssignmentWithExceptions.setRoleRoleGroup(rs.getString("role_role_group"));
        historyOURoleAssignmentWithExceptions.setRoleRoleGroupId(zeroIsNull(rs.getLong("role_role_group_id")));
        historyOURoleAssignmentWithExceptions.setAssignedByUserId(rs.getString("assigned_by_user_id"));
        historyOURoleAssignmentWithExceptions.setAssignedByName(rs.getString("assigned_by_name"));
        historyOURoleAssignmentWithExceptions.setAssignedWhen(rs.getDate("assigned_when"));
        return historyOURoleAssignmentWithExceptions;
    }
}
