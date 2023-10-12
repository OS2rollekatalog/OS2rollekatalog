package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;

public class AttestationUserRoleAssignmentRowMapper implements RowMapper<AttestationUserRoleAssignment> {
    @Override
    public AttestationUserRoleAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        final AttestationUserRoleAssignment assignment = new AttestationUserRoleAssignment();
        assignment.setId(rs.getLong("id"));
        assignment.setValidFrom(RowMapperUtils.nullSafeLocalDate(rs.getDate("valid_from")));
        assignment.setValidTo(RowMapperUtils.nullSafeLocalDate(rs.getDate("valid_to")));
        assignment.setUpdatedAt(RowMapperUtils.nullSafeLocalDate(rs.getDate("updated_at")));
        assignment.setRecordHash(rs.getString("record_hash"));
        assignment.setUserUuid(rs.getString("user_uuid"));
        assignment.setUserId(rs.getString("user_id"));
        assignment.setUserName(rs.getString("user_name"));
        assignment.setUserRoleId(rs.getLong("user_role_id"));
        assignment.setUserRoleName(rs.getString("user_role_name"));
        assignment.setUserRoleDescription(rs.getString("user_role_description"));
        assignment.setRoleGroupId(zeroIsNull(rs.getLong("role_group_id")));
        assignment.setRoleGroupName(rs.getString("role_group_name"));
        assignment.setRoleGroupDescription(rs.getString("role_group_description"));
        assignment.setItSystemId(zeroIsNull(rs.getLong("it_system_id")));
        assignment.setItSystemName(rs.getString("it_system_name"));
        assignment.setResponsibleUserUuid(rs.getString("responsible_user_uuid"));
        assignment.setResponsibleOuName(rs.getString("responsible_ou_name"));
        assignment.setResponsibleOuUuid(rs.getString("responsible_ou_uuid"));
        assignment.setRoleOuName(rs.getString("role_ou_name"));
        assignment.setRoleOuUuid(rs.getString("role_ou_uuid"));
        assignment.setManager(rs.getBoolean("manager"));
        assignment.setAssignedThroughType(AssignedThroughType.valueOf(rs.getString("assigned_through_type")));
        assignment.setAssignedThroughName(rs.getString("assigned_through_name"));
        assignment.setAssignedThroughUuid(rs.getString("assigned_through_uuid"));
        assignment.setInherited(rs.getBoolean("inherited"));
        assignment.setSensitiveRole(rs.getBoolean("sensitive_role"));
        return assignment;
    }

}
