package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface OrganisationUserAttestationEntryDao extends CrudRepository<OrganisationUserAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

    List<OrganisationUserAttestationEntry> findAllByAttestationIn(List<Attestation> ids);
}
