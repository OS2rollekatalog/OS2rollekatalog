package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AttestationSystemRoleAssignmentConstraintsRowMapper implements RowMapper<AttestationSystemRoleAssignmentConstraint>  {
    @Override
    public AttestationSystemRoleAssignmentConstraint mapRow(ResultSet rs, int rowNum) throws SQLException {
        final AttestationSystemRoleAssignmentConstraint constraint = new AttestationSystemRoleAssignmentConstraint();
        String valueType = rs.getString("value_Type");
        constraint.setId(rs.getLong("id"));
        constraint.setName(rs.getString("name"));
        constraint.setValue(rs.getString("value"));
        constraint.setValueType(valueType != null ? ConstraintValueType.valueOf(valueType) : null);
        return constraint;
    }
}
