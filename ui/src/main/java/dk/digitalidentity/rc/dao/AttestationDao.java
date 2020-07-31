package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.Attestation;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;

public interface AttestationDao extends CrudRepository<Attestation, Long> {
	List<Attestation> getByOrgUnit(OrgUnit orgUnit);
	long countByOrgUnit(OrgUnit orgUnit);
	void deleteByOrgUnit(OrgUnit orgUnit);
	Attestation getByUserAndOrgUnit(User user, OrgUnit orgUnit);
	List<Attestation> getByNotifiedFalse();
}
