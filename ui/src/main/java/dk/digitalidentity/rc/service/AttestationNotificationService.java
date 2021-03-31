package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.AttestationNotificationDao;
import dk.digitalidentity.rc.dao.model.AttestationNotification;
import dk.digitalidentity.rc.dao.model.OrgUnit;

@Service
public class AttestationNotificationService {

	@Autowired
	private AttestationNotificationDao attestationNotificationDao;
	
	public List<AttestationNotification> getByOrgUnit(OrgUnit orgUnit){
		return attestationNotificationDao.getByOrgUnit(orgUnit);
	}
	
	public AttestationNotification save(AttestationNotification aN) {
		return attestationNotificationDao.save(aN);
	}
	
	public void deleteAll(List<AttestationNotification> aNs) {
		attestationNotificationDao.deleteAll(aNs);
	}
}
