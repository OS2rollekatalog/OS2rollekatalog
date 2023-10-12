package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.dao.history.model.HistorySystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HistorySystemRoleAssignmentConstraintRowMapper implements RowMapper<HistorySystemRoleAssignmentConstraint> {
    @Override
    public HistorySystemRoleAssignmentConstraint mapRow(ResultSet rs, int rowNum) throws SQLException {
        final HistorySystemRoleAssignmentConstraint constraint = new HistorySystemRoleAssignmentConstraint();
        constraint.setId(rs.getLong("id"));
        constraint.setConstraintName(rs.getString("constraint_name"));
        constraint.setConstraintValueType(ConstraintValueType.valueOf(rs.getString("constraint_value_type")));
        constraint.setConstraintValue(rs.getString("constraint_value"));
        return constraint;
    }
}
