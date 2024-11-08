package dk.digitalidentity.rc.attestation.model.entity.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import dk.digitalidentity.rc.config.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_ou_role_assignments")
public class AttestationOuRoleAssignment extends TemporalAssignmentBase {
    @Column
    @PartOfNaturalKey
    private long roleId;
    @Column
    private String roleName;
    @Column
    private String roleDescription;
    @Column
    @PartOfNaturalKey
    private String ouUuid;
    @Column
    private String ouName;
    @Column
    @PartOfNaturalKey
    private Long roleGroupId;
    @Column
    private String roleGroupName;
    @Column
    private String roleGroupDescription;
    // In case the itSystem have roleAssignmentAttestationByAttestationResponsible set
    // We use responsibleUserUuid in all cases responsibleOu is used.
    @Column
    @PartOfNaturalKey
    private String responsibleUserUuid;
    @Column
    @PartOfNaturalKey
    private String responsibleOuUuid;
    @Column
    private String responsibleOuName;
    @Column
    @Convert(converter = StringListConverter.class)
    @PartOfNaturalKey
    @Builder.Default
    private List<String> titleUuids = Collections.emptyList();
    @Column
    @Convert(converter = StringListConverter.class)
    @PartOfNaturalKey
    @Builder.Default
    private List<String> exceptedUserUuids = Collections.emptyList();
    @Column
    @PartOfNaturalKey
    private Long itSystemId;
    @Column
    private String itSystemName;
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
    private boolean inherit;
    @Column
    private boolean sensitiveRole;
    @Column
    @Convert(converter = StringListConverter.class)
    @PartOfNaturalKey
    @Builder.Default
    private List<String> exceptedTitleUuids = Collections.emptyList();

}
