package dk.digitalidentity.rc.attestation.model.entity.temporal;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_system_role_assignment_constraints")
@ToString
public class AttestationSystemRoleAssignmentConstraint {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="attestation_system_role_assignments_id", nullable=false)
    private AttestationSystemRoleAssignment assignment;

    @Column(length = 64)
    private String name;

    @Column(length = 64)
    @Enumerated(EnumType.STRING)
    private ConstraintValueType valueType;

    @Column
    private String value;
}
