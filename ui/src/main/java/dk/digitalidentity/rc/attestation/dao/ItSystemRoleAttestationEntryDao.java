package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import org.springframework.data.repository.CrudRepository;

public interface ItSystemRoleAttestationEntryDao extends CrudRepository<ItSystemRoleAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

}
