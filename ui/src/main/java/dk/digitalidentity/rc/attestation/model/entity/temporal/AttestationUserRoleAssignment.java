package dk.digitalidentity.rc.attestation.model.entity.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Objects;

@Getter
@Setter
@Builder
@Entity
@Table(name = "attestation_user_role_assignments")
@NoArgsConstructor
@AllArgsConstructor
public class AttestationUserRoleAssignment extends TemporalAssignmentBase {
    @Column
    @PartOfNaturalKey
    private String userUuid;
    @Column
    private String userId;
    @Column
    private String userName;
    @Column
    @PartOfNaturalKey
    private long userRoleId;
    @Column
    private String userRoleName;
    @Column
    private String userRoleDescription;
    @Column
    @PartOfNaturalKey
    private Long roleGroupId;
    @Column
    private String roleGroupName;
    @Column
    private String roleGroupDescription;
    @Column
    @PartOfNaturalKey
    private Long itSystemId;
    @Column
    private String itSystemName;
    // In case the itSystem have roleAssignmentAttestationByAttestationResponsible set
    // We use responsibleUserUuid in all cases responsibleOu is used.
    @Column
    @PartOfNaturalKey
    private String responsibleUserUuid;
    @Column
    private String responsibleOuName;
    @Column
    @PartOfNaturalKey
    private String roleOuUuid;
    @Column
    private String roleOuName;
    @Column
    @PartOfNaturalKey
    private String responsibleOuUuid;
    @Column
    @PartOfNaturalKey
    private boolean manager; // This will be set for managers, responsibleOu will contain the parent ou
    @Column
    @Enumerated(EnumType.STRING)
    @PartOfNaturalKey
    private AssignedThroughType assignedThroughType;
    @Column
    @PartOfNaturalKey
    private String assignedThroughName;
    @Column
    @PartOfNaturalKey
    private String assignedThroughUuid;
    @Column
    private boolean inherited;
    @Column
    private boolean sensitiveRole;
    @Column
    private boolean extraSensitiveRole;
    @Column
    private String postponedConstraints;

    @Column
    private LocalDate assignedFrom;

    /**
     * Compare two objects, all fields except temporal fields and the id
     */
    public boolean contentEquals(AttestationUserRoleAssignment that) {
        return userRoleId == that.userRoleId
                && manager == that.manager
                && inherited == that.inherited
                && sensitiveRole == that.sensitiveRole
                && extraSensitiveRole == that.extraSensitiveRole
                && Objects.equals(userUuid, that.userUuid)
                && Objects.equals(userId, that.userId)
                && Objects.equals(userName, that.userName)
                && Objects.equals(userRoleName, that.userRoleName)
                && Objects.equals(userRoleDescription, that.userRoleDescription)
                && Objects.equals(roleGroupId, that.roleGroupId)
                && Objects.equals(roleGroupName, that.roleGroupName)
                && Objects.equals(roleGroupDescription, that.roleGroupDescription)
                && Objects.equals(itSystemId, that.itSystemId)
                && Objects.equals(itSystemName, that.itSystemName)
                && Objects.equals(responsibleUserUuid, that.responsibleUserUuid)
                && Objects.equals(responsibleOuName, that.responsibleOuName)
                && Objects.equals(roleOuUuid, that.roleOuUuid)
                && Objects.equals(roleOuName, that.roleOuName)
                && Objects.equals(responsibleOuUuid, that.responsibleOuUuid)
                && assignedThroughType == that.assignedThroughType
                && Objects.equals(assignedThroughName, that.assignedThroughName)
                && Objects.equals(assignedThroughUuid, that.assignedThroughUuid)
                && Objects.equals(postponedConstraints, that.postponedConstraints)
                && Objects.equals(assignedFrom, that.assignedFrom);
    }

}
