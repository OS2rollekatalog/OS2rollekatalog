package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttestationResponsibleCollectionDao extends JpaRepository<AttestationResponsibleCollection, Long> {
    Optional<AttestationResponsibleCollection> findFirstByItSystemId(Long itSystemId);
}
