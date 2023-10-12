package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HistoryUserRoleRowMapper implements RowMapper<HistoryUserRole> {
    @Override
    public HistoryUserRole mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistoryUserRole historyUserRole = new HistoryUserRole();
        historyUserRole.setId(rs.getLong("id"));
        historyUserRole.setUserRoleId(rs.getLong("user_role_id"));
        historyUserRole.setUserRoleName(rs.getString("user_role_name"));
        historyUserRole.setUserRoleDescription(rs.getString("user_role_description"));
        historyUserRole.setUserRoleDelegatedFromCvr(rs.getString("user_role_delegated_from_cvr"));
        historyUserRole.setRoleAssignmentAttestationByAttestationResponsible(rs.getBoolean("role_assignment_attestation_by_attestation_responsible"));
        historyUserRole.setSensitiveRole(rs.getBoolean("sensitive_role"));
        return historyUserRole;
    }
}
