package dk.digitalidentity.rc.attestation.service.temporal.rowmapper;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import static dk.digitalidentity.rc.attestation.service.temporal.rowmapper.RowMapperUtils.zeroIsNull;

public class AttestationOuRoleAssignmentRowMapper implements RowMapper<AttestationOuRoleAssignment> {
    @Override
    public AttestationOuRoleAssignment mapRow(ResultSet rs, int rowNum) throws SQLException {
        final AttestationOuRoleAssignment assignment = new AttestationOuRoleAssignment();
        assignment.setId(rs.getLong("id"));
        assignment.setValidFrom(RowMapperUtils.nullSafeLocalDate(rs.getDate("valid_from")));
        assignment.setValidTo(RowMapperUtils.nullSafeLocalDate(rs.getDate("valid_to")));
        assignment.setUpdatedAt(RowMapperUtils.nullSafeLocalDate(rs.getDate("updated_at")));
        assignment.setRecordHash(rs.getString("record_hash"));
        assignment.setRoleId(rs.getLong("role_id"));
        assignment.setRoleName(rs.getString("role_name"));
        assignment.setRoleDescription(rs.getString("role_description"));
        assignment.setOuUuid(rs.getString("ou_uuid"));
        assignment.setOuName(rs.getString("ou_name"));
        assignment.setRoleGroupId(zeroIsNull(rs.getLong("role_group_id")));
        assignment.setRoleGroupName(rs.getString("role_group_name"));
        assignment.setRoleGroupDescription(rs.getString("role_group_description"));
        assignment.setResponsibleUserUuid(rs.getString("responsible_user_uuid"));
        assignment.setResponsibleOuUuid(rs.getString("responsible_ou_uuid"));
        assignment.setResponsibleOuName(rs.getString("responsible_ou_name"));
        assignment.setTitleUuids(RowMapperUtils.explodeToList(rs.getString("title_uuids")));
        assignment.setExceptedUserUuids(RowMapperUtils.explodeToList(rs.getString("excepted_user_uuids")));
        assignment.setItSystemId(zeroIsNull(rs.getLong("it_system_id")));
        assignment.setItSystemName(rs.getString("it_system_name"));
        assignment.setAssignedThroughType(AssignedThroughType.valueOf(rs.getString("assigned_through_type")));
        assignment.setAssignedThroughName(rs.getString("assigned_through_name"));
        assignment.setAssignedThroughUuid(rs.getString("assigned_through_uuid"));
        assignment.setInherit(rs.getBoolean("inherit"));
        assignment.setInherited(rs.getBoolean("inherited"));
        assignment.setSensitiveRole(rs.getBoolean("sensitive_role"));
        return assignment;
    }

}
