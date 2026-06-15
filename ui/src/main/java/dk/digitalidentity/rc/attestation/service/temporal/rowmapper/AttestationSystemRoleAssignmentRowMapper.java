package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;

public class AttestationSystemRoleAssignmentRowMapper implements RowMapper<AttestationSystemRoleAssignment> {
    @Override
    public AttestationSystemRoleAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        final AttestationSystemRoleAssignment assignment = new AttestationSystemRoleAssignment();
        assignment.setId(rs.getLong("id"));
        assignment.setValidFrom(RowMapperUtils.nullSafeLocalDate(rs.getDate("valid_from")));
        assignment.setValidTo(RowMapperUtils.nullSafeLocalDate(rs.getDate("valid_to")));
        assignment.setUpdatedAt(RowMapperUtils.nullSafeLocalDate(rs.getDate("updated_at")));
        assignment.setRecordHash(rs.getString("record_hash"));
        assignment.setUserRoleId(rs.getLong("user_role_id"));
        assignment.setUserRoleName(rs.getString("user_role_name"));
        assignment.setUserRoleDescription(rs.getString("user_role_description"));
        assignment.setSystemRoleId(rs.getLong("system_role_id"));
        assignment.setSystemRoleName(rs.getString("system_role_name"));
        assignment.setSystemRoleDescription(rs.getString("system_role_description"));
        assignment.setResponsibleCollectionId(zeroIsNull(rs.getLong("attestation_responsible_collection_id")));
        assignment.setItSystemId(zeroIsNull(rs.getLong("it_system_id")));
        assignment.setItSystemName(rs.getString("it_system_name"));
        return assignment;
    }
}
