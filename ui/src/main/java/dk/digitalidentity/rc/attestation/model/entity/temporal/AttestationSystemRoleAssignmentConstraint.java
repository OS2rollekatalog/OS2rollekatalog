package dk.digitalidentity.rc.attestation.model.entity.temporal;

import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

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
