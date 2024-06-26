package dk.digitalidentity.rc.attestation.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * This class will contain attestations for a users roles on a given it-system.
 * This will be used when an it-system is marked as "roleAssignmentAttestationByAttestationResponsible"
 */
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "attestation_it_system_user_attestation_entry")
public class ItSystemUserAttestationEntry extends BaseUserAttestationEntry {
}
