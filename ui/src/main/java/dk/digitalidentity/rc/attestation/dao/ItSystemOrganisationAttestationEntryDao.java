package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import org.springframework.data.repository.CrudRepository;

public interface ItSystemOrganisationAttestationEntryDao extends CrudRepository<ItSystemOrganisationAttestationEntry, Long> {
}
