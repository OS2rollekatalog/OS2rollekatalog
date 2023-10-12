package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import org.springframework.data.repository.CrudRepository;

public interface AttestationUserDao extends CrudRepository<AttestationUser, Long> {
}
