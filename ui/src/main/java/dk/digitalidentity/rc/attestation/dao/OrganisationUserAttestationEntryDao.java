package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface OrganisationUserAttestationEntryDao extends CrudRepository<OrganisationUserAttestationEntry, Long> {

    long countByAttestationId(final Long attestationId);

    // projection for batchLoadStatusInfo
    interface AttestationUserProjection {
        Long getAttestationId();
        String getUserUuid();
    }

    @Query("SELECT e.attestation.id AS attestationId, e.userUuid AS userUuid FROM OrganisationUserAttestationEntry e WHERE e.attestation IN :attestations")
    List<AttestationUserProjection> findAttestationUserPairsByAttestationIn(@Param("attestations") List<Attestation> attestations);

    @Query("SELECT DISTINCT e.attestation.id FROM OrganisationUserAttestationEntry e WHERE e.attestation.id IN :ids")
    Set<Long> findAttestationIdsWithAnyEntry(@Param("ids") Collection<Long> ids);
}
