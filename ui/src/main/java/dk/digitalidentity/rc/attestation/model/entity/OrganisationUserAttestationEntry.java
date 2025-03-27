package dk.digitalidentity.rc.attestation.model.entity;


import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * This class will contain attestations for a users roles within an organisation.
 */
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attestation_organisation_user_attestation_entry")
public class OrganisationUserAttestationEntry extends BaseUserAttestationEntry {

    // In case the leader want the users ad account to be removed
    private boolean adRemoval;

    @ElementCollection
    @CollectionTable(name = "attestation_organisation_user_attestation_entry_user_role", joinColumns = @JoinColumn(name = "attestation_organisation_user_attestation_entry_id"))
    @Column(name = "user_role")
    private Set<String> rejectedUserRoleIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "attestation_organisation_user_attestation_entry_role_group", joinColumns = @JoinColumn(name = "attestation_organisation_user_attestation_entry_id"))
    @Column(name = "role_group")
    private Set<String> rejectedRoleGroupIds = new HashSet<>();

}
