package dk.digitalidentity.rc.attestation.model.entity.temporal;

import dk.digitalidentity.rc.attestation.annotation.PartOfNaturalKey;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_system_role_assignments")
public class AttestationSystemRoleAssignment extends TemporalAssignmentBase {

    @Column
    @PartOfNaturalKey
    private long userRoleId;
    @Column
    private String userRoleName;
    @Column
    private String userRoleDescription;
    @Column
    @PartOfNaturalKey
    private long systemRoleId;
    @Column
    private String systemRoleName;
    @Column
    private String systemRoleDescription;
    @Column
    @PartOfNaturalKey
    private String responsibleUserUuid;
    @Column
    @PartOfNaturalKey
    private long itSystemId;
    @Column
    private String itSystemName;

    @PartOfNaturalKey
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL)
    @Builder.Default
    private Set<AttestationSystemRoleAssignmentConstraint> constraints = new HashSet<>();

}
