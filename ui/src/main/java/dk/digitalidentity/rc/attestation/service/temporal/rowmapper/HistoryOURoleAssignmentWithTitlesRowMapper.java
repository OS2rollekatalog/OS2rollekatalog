package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;

public class HistoryOURoleAssignmentWithTitlesRowMapper implements RowMapper<HistoryOURoleAssignmentWithTitles> {
    @Override
    public HistoryOURoleAssignmentWithTitles mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryOURoleAssignmentWithTitles historyOURoleAssignmentWithTitles = new HistoryOURoleAssignmentWithTitles();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        historyOURoleAssignmentWithTitles.setId(rs.getLong("id"));
        historyOURoleAssignmentWithTitles.setDato(dato);
        historyOURoleAssignmentWithTitles.setOuUuid(rs.getString("ou_uuid"));
        historyOURoleAssignmentWithTitles.setTitleUuids(RowMapperUtils.explodeToList(rs.getString("title_uuids")));
        historyOURoleAssignmentWithTitles.setRoleId(rs.getLong("role_id"));
        historyOURoleAssignmentWithTitles.setRoleName(rs.getString("role_name"));
        historyOURoleAssignmentWithTitles.setRoleItSystemId(rs.getLong("role_it_system_id"));
        historyOURoleAssignmentWithTitles.setRoleItSystemName(rs.getString("role_it_system_name"));
        historyOURoleAssignmentWithTitles.setRoleRoleGroup(rs.getString("role_role_group"));
        historyOURoleAssignmentWithTitles.setRoleRoleGroupId(zeroIsNull(rs.getLong("role_role_group_id")));
        historyOURoleAssignmentWithTitles.setAssignedByUserId(rs.getString("assigned_by_user_id"));
        historyOURoleAssignmentWithTitles.setAssignedByName(rs.getString("assigned_by_name"));
        historyOURoleAssignmentWithTitles.setAssignedWhen(rs.getTimestamp("assigned_when"));
        historyOURoleAssignmentWithTitles.setInherit(rs.getBoolean("inherit"));
        historyOURoleAssignmentWithTitles.setAssignedThroughType(AssignedThrough.valueOf(rs.getString("assigned_through_type")));
        historyOURoleAssignmentWithTitles.setAssignedThroughUuid(rs.getString("assigned_through_uuid"));
        historyOURoleAssignmentWithTitles.setAssignedThroughName(rs.getString("assigned_through_name"));
        return historyOURoleAssignmentWithTitles;
    }

}
