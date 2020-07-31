package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.AttestationDao;
import dk.digitalidentity.rc.dao.model.Attestation;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;

@Service
public class AttestationService {

	@Autowired
	private AttestationDao attestationDao;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private OrgUnitService orgUnitService;

	public boolean flagPositionForAttestation(String userUuid, String orgUnitUuid) {
		User user = userService.getByUuid(userUuid);
		OrgUnit orgUnit = orgUnitService.getByUuid(orgUnitUuid);
		
		if (user != null && orgUnit != null) {
			boolean hasRoles = (user.getUserRoleAssignments().size() > 0 || user.getRoleGroupAssignments().size() > 0);

			if (!hasRoles) {
				for (Position position : user.getPositions()) {
					if (position.getUserRoleAssignments().size() > 0 || position.getRoleGroupAssignments().size() > 0) {
						hasRoles = true;
						break;
					}
				}
			}
			
			if (hasRoles) {
				Position position = new Position();
				position.setUser(user);
				position.setOrgUnit(orgUnit);
	
				return flagPositionForAttestation(position);
			}
		}
		
		return false;
	}

	public boolean flagPositionForAttestation(Position position) {
		Attestation attestation = attestationDao.getByUserAndOrgUnit(position.getUser(), position.getOrgUnit());
		if (attestation == null) {
			boolean hasRoles = (position.getUser().getUserRoleAssignments().size() > 0 || position.getUser().getRoleGroupAssignments().size() > 0 ||
								position.getUserRoleAssignments().size() > 0 || position.getRoleGroupAssignments().size() > 0);

			if (hasRoles) {
				attestation = new Attestation();
				attestation.setUser(position.getUser());
				attestation.setOrgUnit(position.getOrgUnit());
				attestation.setNotified(false);
	
				save(attestation);
				
				return true;
			}
		}
		
		return false;
	}

	public List<User> getManagersToNotify() {
		List<User> managers = new ArrayList<>();
		
		List<Attestation> attestations = attestationDao.getByNotifiedFalse();
		for (Attestation attestation : attestations) {
			attestation.setNotified(true);
			save(attestation);

			if (attestation.getOrgUnit().getManager() == null) {
				continue;
			}
			
			addManager(managers, attestation.getOrgUnit().getManager());
		}

		return managers;
	}
	
	// equals on user sucks a bit
	private void addManager(List<User> managers, User manager) {
		boolean found = false;

		if (manager == null) {
			return;
		}

		for (User user : managers) {
			if (user.getUuid().equals(manager.getUuid())) {
				found = true;
			}
		}
		
		if (!found) {
			managers.add(manager);
		}
	}

	public Attestation save(Attestation attestation) {
		return attestationDao.save(attestation);
	}
	
	public List<Attestation> getByOrgUnit(OrgUnit orgUnit) {
		return attestationDao.getByOrgUnit(orgUnit);
	}
	
	public long countByOrgUnit(OrgUnit orgUnit) {
		return attestationDao.countByOrgUnit(orgUnit);
	}

	@Transactional(rollbackFor = Exception.class)
	public void deleteByOrgUnit(OrgUnit orgUnit) {
		attestationDao.deleteByOrgUnit(orgUnit);		
	}
}
