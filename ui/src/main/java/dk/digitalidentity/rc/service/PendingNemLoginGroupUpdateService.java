package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.PendingNemLoginGroupUpdateDao;
import dk.digitalidentity.rc.dao.model.PendingNemLoginGroupUpdate;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.NemLoginGroupEventType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PendingNemLoginGroupUpdateService {

	@Autowired
	private PendingNemLoginGroupUpdateDao pendingNemLoginGroupUpdateDao;

	public List<PendingNemLoginGroupUpdate> findAll() {
		return pendingNemLoginGroupUpdateDao.findAll();
	}
	
	public List<PendingNemLoginGroupUpdate> findAllByFailedFalse() {
		return pendingNemLoginGroupUpdateDao.findByFailedFalse();
	}

	public void delete(PendingNemLoginGroupUpdate entity) {
		pendingNemLoginGroupUpdateDao.delete(entity);
	}

	public void delete(List<PendingNemLoginGroupUpdate> entities) {
		pendingNemLoginGroupUpdateDao.deleteAll(entities);
	}

	public PendingNemLoginGroupUpdate save(PendingNemLoginGroupUpdate entity) {
		return pendingNemLoginGroupUpdateDao.save(entity);
	}
	
	public boolean hasPendingUpdate(UserRole userRole) {
		return (pendingNemLoginGroupUpdateDao.findByUserRoleId(userRole.getId()) != null);
	}

	public void addUserRoleToQueue(UserRole userRole, NemLoginGroupEventType eventType) {
		try {
			List<PendingNemLoginGroupUpdate> pendingUpdates = pendingNemLoginGroupUpdateDao.findByUserRoleId(userRole.getId());
	
			if (pendingUpdates == null || pendingUpdates.size() == 0) {
				PendingNemLoginGroupUpdate pendingNemLoginGroupUpdate = new PendingNemLoginGroupUpdate();
				pendingNemLoginGroupUpdate.setEventType(eventType);
				pendingNemLoginGroupUpdate.setUserRoleId(userRole.getId());
				pendingNemLoginGroupUpdate.setUserRoleUuid(userRole.getUuid());
				pendingNemLoginGroupUpdateDao.save(pendingNemLoginGroupUpdate);
			}
			else {
				boolean first = true;
	
				for (PendingNemLoginGroupUpdate pendingUpdate : pendingUpdates) {
					if (first) {
						pendingUpdate.setEventType(eventType);
						pendingUpdate.setUserRoleUuid(userRole.getUuid());
						pendingUpdate.setFailed(false);
						pendingNemLoginGroupUpdateDao.save(pendingUpdate);
						
						first = false;
					}
					else {
						pendingNemLoginGroupUpdateDao.delete(pendingUpdate);
					}
				}
			}
		}
		catch (Exception ex) {
			log.error("Failed to add userRole to queue: " + userRole.getId(), ex);
		}
	}
}
