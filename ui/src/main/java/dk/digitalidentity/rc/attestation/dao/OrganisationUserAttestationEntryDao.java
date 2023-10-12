package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import org.springframework.data.repository.CrudRepository;

public interface OrganisationUserAttestationEntryDao extends CrudRepository<OrganisationUserAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

}
