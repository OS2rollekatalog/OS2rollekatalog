package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.PendingKOMBITUpdateDao;
import dk.digitalidentity.rc.dao.model.PendingKOMBITUpdate;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.KOMBITEventType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PendingKOMBITUpdateService {

	@Autowired
	private PendingKOMBITUpdateDao pendingKOMBITUpdateDao;

	public List<PendingKOMBITUpdate> findAll() {
		return pendingKOMBITUpdateDao.findAll();
	}
	
	public List<PendingKOMBITUpdate> findAllByFailedFalse() {
		return pendingKOMBITUpdateDao.findByFailedFalse();
	}

	public void delete(PendingKOMBITUpdate entity) {
		pendingKOMBITUpdateDao.delete(entity);
	}

	public void delete(List<PendingKOMBITUpdate> entities) {
		pendingKOMBITUpdateDao.deleteAll(entities);
	}

	public PendingKOMBITUpdate save(PendingKOMBITUpdate entity) {
		return pendingKOMBITUpdateDao.save(entity);
	}
	
	public boolean hasPendingUpdate(UserRole userRole) {
		return (pendingKOMBITUpdateDao.findByUserRoleId(userRole.getId()) != null);
	}

	public void addUserRoleToQueue(UserRole userRole, KOMBITEventType eventType) {
		try {
			List<PendingKOMBITUpdate> pendingUpdates = pendingKOMBITUpdateDao.findByUserRoleId(userRole.getId());
	
			if (pendingUpdates == null || pendingUpdates.size() == 0) {
				PendingKOMBITUpdate pendingKOMBITUpdate = new PendingKOMBITUpdate();
				pendingKOMBITUpdate.setEventType(eventType);
				pendingKOMBITUpdate.setUserRoleId(userRole.getId());
				pendingKOMBITUpdate.setUserRoleUuid(userRole.getUuid());
				pendingKOMBITUpdateDao.save(pendingKOMBITUpdate);
			}
			else {
				boolean first = true;
	
				for (PendingKOMBITUpdate pendingUpdate : pendingUpdates) {
					if (first) {
						pendingUpdate.setEventType(eventType);
						pendingUpdate.setUserRoleUuid(userRole.getUuid());
						pendingUpdate.setFailed(false);
						pendingKOMBITUpdateDao.save(pendingUpdate);
						
						first = false;
					}
					else {
						pendingKOMBITUpdateDao.delete(pendingUpdate);
					}
				}
			}
		}
		catch (Exception ex) {
			log.error("Failed to add userRole to queue: " + userRole.getId(), ex);
		}
	}
}
