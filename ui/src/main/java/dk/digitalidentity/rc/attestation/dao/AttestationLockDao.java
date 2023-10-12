package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.AttestationLock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttestationLockDao extends JpaRepository<AttestationLock, String> {

}
