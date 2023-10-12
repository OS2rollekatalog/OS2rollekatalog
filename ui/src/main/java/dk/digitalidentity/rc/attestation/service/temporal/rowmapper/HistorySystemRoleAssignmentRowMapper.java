package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HistorySystemRoleAssignmentRowMapper implements RowMapper<HistorySystemRoleAssignment> {
    @Override
    public HistorySystemRoleAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistorySystemRoleAssignment roleAssignment = new HistorySystemRoleAssignment();
        roleAssignment.setId(rs.getLong("id"));
        roleAssignment.setSystemRoleId(rs.getLong("system_role_id"));
        roleAssignment.setSystemRoleName(rs.getString("system_role_name"));
        roleAssignment.setSystemRoleDescription(rs.getString("system_role_description"));
        return roleAssignment;
    }
}
