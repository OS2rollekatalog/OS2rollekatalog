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
import java.time.LocalDateTime;
import java.util.Date;

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
}
