package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttestationResponsibleCollectionDao extends JpaRepository<AttestationResponsibleCollection, Long> {
    Optional<AttestationResponsibleCollection> findFirstByItSystemId(Long itSystemId);

    @Query("SELECT DISTINCT c.itSystemId FROM AttestationResponsibleCollection c WHERE c.itSystemId IS NOT NULL")
    List<Long> findAllItSystemIds();
}
