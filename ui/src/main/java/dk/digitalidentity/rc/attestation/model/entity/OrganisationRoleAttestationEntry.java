package dk.digitalidentity.rc.attestation.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

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

    @ElementCollection
    @CollectionTable(name = "attestation_organisation_role_attestation_entry_user_role", joinColumns = @JoinColumn(name = "attestation_organisation_role_attestation_entry_id"))
    @Column(name = "user_role")
    @Builder.Default
    private Set<String> rejectedUserRoleIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "attestation_organisation_role_attestation_entry_role_group", joinColumns = @JoinColumn(name = "attestation_organisation_role_attestation_entry_id"))
    @Column(name = "role_group")
    @Builder.Default
    private Set<String> rejectedRoleGroupIds = new HashSet<>();

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "attestation_id")
    private Attestation attestation;

}
