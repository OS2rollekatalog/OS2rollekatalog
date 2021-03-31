package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.AttestationNotification;
import dk.digitalidentity.rc.dao.model.OrgUnit;

public interface AttestationNotificationDao extends CrudRepository<AttestationNotification, Long> {
	List<AttestationNotification> getByOrgUnit(OrgUnit orgUnit);

}
