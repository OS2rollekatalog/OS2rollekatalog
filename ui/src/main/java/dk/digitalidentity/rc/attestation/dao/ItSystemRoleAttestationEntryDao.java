package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;

public interface ItSystemRoleAttestationEntryDao extends CrudRepository<ItSystemRoleAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

    List<ItSystemRoleAttestationEntry> findAllByAttestationIn(Collection<Attestation> attestations);
}
