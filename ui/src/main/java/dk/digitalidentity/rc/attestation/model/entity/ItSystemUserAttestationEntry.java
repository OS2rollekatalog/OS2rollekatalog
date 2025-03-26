package dk.digitalidentity.rc.attestation.model.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

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

	@ElementCollection
	@CollectionTable(name = "attestation_it_system_user_attestation_entry_user_role", joinColumns = @JoinColumn(name = "attestation_it_system_user_attestation_entry_id"))
	@Column(name = "user_role")
	private Set<String> rejectedUserRoleIds = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "attestation_it_system_user_attestation_entry_role_group", joinColumns = @JoinColumn(name = "attestation_it_system_user_attestation_entry_id"))
	@Column(name = "role_group")
	private Set<String> rejectedRoleGroupIds  = new HashSet<>();
}
