package dk.digitalidentity.rc.attestation.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import java.time.ZonedDateTime;

/**
 * This class will contain attestations for user-roles on a given organisation
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_organisation_role_attestation_entry")
public class OrganisationRoleAttestationEntry {
    @Id
    @Column(name = "attestation_id")
    private Long id;

    @Column
    private String remarks;

    @Column
    private ZonedDateTime createdAt;

    @Column
    private String performedByUserId;

    @Column
    private String performedByUserUuid;


    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "attestation_id")
    private Attestation attestation;

}
