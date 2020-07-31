package dk.digitalidentity.rc.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.PendingManualUpdateDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingManualUpdate;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;

@Service
public class ManualRolesService {

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private PendingManualUpdateDao pendingManualUpdateDao;

	public List<PendingManualUpdate> findAll() {
		return pendingManualUpdateDao.findAll();
	}

	public void delete(PendingManualUpdate entity) {
		pendingManualUpdateDao.delete(entity);
	}

	public void delete(List<PendingManualUpdate> entities) {
		pendingManualUpdateDao.deleteAll(entities);
	}

	public void addUserToQueue(User user, UserRole userRole) {
		// if the UserRoles is related to an ItSystem of type 'MANUAL', we add the user to the queue
		if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
			addUserToQueue(user, userRole.getItSystem());
		}
	}

	public void addUserToQueue(User user, RoleGroup roleGroup) {
		// if any of the UserRoles within the RoleGroup are related to an ItSystem of type 'MANUAL', we add the user to the queue
		if (roleGroup.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
					addUserToQueue(user, userRole.getItSystem());
				}
			}
		}
	}

	public void addUserToQueue(User user, Position position) {
		boolean addToQueue = false;
		
		// check rolegroups assigned to position
		if (position.getRoleGroupAssignments() != null) {
	      	List<RoleGroup> prg = position.getRoleGroupAssignments().stream().map(ura -> ura.getRoleGroup()).collect(Collectors.toList());

			for (RoleGroup roleGroup : prg) {
				List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

				for (UserRole userRole : userRoles) {
					if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
						addUserToQueue(user, userRole.getItSystem());
					}
				}
			}
		}
		
		// check userroles assigned to position
		if (!addToQueue && position.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
					addUserToQueue(user, userRole.getItSystem());
				}
			}
		}
		
		// check rolegroups assigned to OU that the position points to
		if (!addToQueue) {
			List<RoleGroup> rgs = orgUnitService.getRoleGroups(position.getOrgUnit(), true);

			for (RoleGroup roleGroup : rgs) {
				if (roleGroup.getUserRoleAssignments() != null) {
					List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

					for (UserRole userRole : userRoles) {
						if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
							addUserToQueue(user, userRole.getItSystem());
						}
					}
				}
			}
		}
		
		// check userroles assigned to the OU that the position points to
		if (!addToQueue) {
			List<UserRole> urs = orgUnitService.getUserRoles(position.getOrgUnit(), true);

			for (UserRole userRole : urs) {
				if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
					addUserToQueue(user, userRole.getItSystem());
				}
			}
		}
	}

	// we always add to queue, duplicates are dealt with elsewhere
	private void addUserToQueue(User user, ItSystem itSystem) {
		PendingManualUpdate pendingManualUpdate = new PendingManualUpdate();
		pendingManualUpdate.setUserId(user.getUserId());
		pendingManualUpdate.setTimestamp(new Date());
		pendingManualUpdate.setItSystemId(itSystem.getId());
		pendingManualUpdateDao.save(pendingManualUpdate);
	}
}
