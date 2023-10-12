package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

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
        historyOURoleAssignmentWithTitles.setRoleRoleGroupId(rs.getLong("role_role_group_id"));
        historyOURoleAssignmentWithTitles.setAssignedByUserId(rs.getString("assigned_by_user_id"));
        historyOURoleAssignmentWithTitles.setAssignedByName(rs.getString("assigned_by_name"));
        historyOURoleAssignmentWithTitles.setAssignedWhen(rs.getDate("assigned_when"));
        return historyOURoleAssignmentWithTitles;
    }

}
