package dk.digitalidentity.rc.attestation.dao;

import dk.digitalidentity.rc.attestation.model.entity.AttestationMail;
import org.springframework.data.repository.CrudRepository;

public interface AttestationMailDao extends CrudRepository<AttestationMail, Long>  {
}
