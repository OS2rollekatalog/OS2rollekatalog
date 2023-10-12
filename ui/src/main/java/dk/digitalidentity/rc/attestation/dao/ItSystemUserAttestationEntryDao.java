package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import org.springframework.data.repository.CrudRepository;

public interface ItSystemUserAttestationEntryDao extends CrudRepository<ItSystemUserAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

}
