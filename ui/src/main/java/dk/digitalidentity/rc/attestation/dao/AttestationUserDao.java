package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AttestationUserDao extends CrudRepository<AttestationUser, Long> {
    AttestationUser findByUserUuidAndAttestation(String userUuid, Attestation attestation);

    // projection for batchLoadStatusInfo — attestation id, user uuid, and sensitive flag
    interface AttestationIdUserUuidProjection {
        Long getAttestationId();
        String getUserUuid();
        boolean isSensitiveRoles();
    }

    @Query("SELECT a.attestation.id AS attestationId, a.userUuid AS userUuid, a.sensitiveRoles AS sensitiveRoles FROM AttestationUser a WHERE a.attestation.id IN :attestationIds")
    List<AttestationIdUserUuidProjection> findUserUuidsByAttestationIdIn(@Param("attestationIds") Collection<Long> attestationIds);
}
