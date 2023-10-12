package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import org.springframework.data.repository.CrudRepository;

public interface OrganisationRoleAttestationEntryDao extends CrudRepository<OrganisationRoleAttestationEntry, Long> {

}
