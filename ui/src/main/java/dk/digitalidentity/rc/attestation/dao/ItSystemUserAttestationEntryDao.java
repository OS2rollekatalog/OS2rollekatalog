package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ItSystemUserAttestationEntryDao extends CrudRepository<ItSystemUserAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

    List<ItSystemUserAttestationEntry> findAllByAttestationIn(List<Attestation> attestations);
}
