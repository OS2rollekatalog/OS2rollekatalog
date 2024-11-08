package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithNegativeTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class HistoryOURoleAssignmentWithNegativeTitlesRowMapper implements RowMapper<HistoryOURoleAssignmentWithNegativeTitles> {
    @Override
    public HistoryOURoleAssignmentWithNegativeTitles mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryOURoleAssignmentWithNegativeTitles historyOURoleAssignmentWithNegativeTitles = new HistoryOURoleAssignmentWithNegativeTitles();
        final LocalDate dato = new java.sql.Date(rs.getDate("dato").getTime()).toLocalDate();
        historyOURoleAssignmentWithNegativeTitles.setId(rs.getLong("id"));
        historyOURoleAssignmentWithNegativeTitles.setDato(dato);
        historyOURoleAssignmentWithNegativeTitles.setOuUuid(rs.getString("ou_uuid"));
        historyOURoleAssignmentWithNegativeTitles.setTitleUuids(RowMapperUtils.explodeToList(rs.getString("title_uuids")));
        historyOURoleAssignmentWithNegativeTitles.setRoleId(rs.getLong("role_id"));
        historyOURoleAssignmentWithNegativeTitles.setRoleName(rs.getString("role_name"));
        historyOURoleAssignmentWithNegativeTitles.setRoleItSystemId(rs.getLong("role_it_system_id"));
        historyOURoleAssignmentWithNegativeTitles.setRoleItSystemName(rs.getString("role_it_system_name"));
        historyOURoleAssignmentWithNegativeTitles.setRoleRoleGroup(rs.getString("role_role_group"));
        historyOURoleAssignmentWithNegativeTitles.setRoleRoleGroupId(rs.getLong("role_role_group_id"));
        historyOURoleAssignmentWithNegativeTitles.setAssignedByUserId(rs.getString("assigned_by_user_id"));
        historyOURoleAssignmentWithNegativeTitles.setAssignedByName(rs.getString("assigned_by_name"));
        historyOURoleAssignmentWithNegativeTitles.setAssignedWhen(rs.getDate("assigned_when"));
        return historyOURoleAssignmentWithNegativeTitles;
    }

}
