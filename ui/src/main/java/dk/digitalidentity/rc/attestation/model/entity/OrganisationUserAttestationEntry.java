package dk.digitalidentity.rc.attestation.model.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.persistence.Entity;
import javax.persistence.Table;

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

}
