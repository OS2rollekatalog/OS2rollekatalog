package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.LocaleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.PendingManualUpdateDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingManualUpdate;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ManualRolesService {

	private static final String localeString = "da_DK";

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private EmailService emailService;

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
						addToQueue = true;
						break;
					}
				}
				
				if (addToQueue) {
					break;
				}
			}
		}
		
		// check userroles assigned to position
		if (!addToQueue && position.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = position.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
					addUserToQueue(user, userRole.getItSystem());
					addToQueue = true;
					break;
				}
			}
		}
		
		// check rolegroups assigned to OU that the position points to
		if (!addToQueue) {
			List<RoleGroup> rgs = orgUnitService.getRoleGroupsWithUserFilter(position.getOrgUnit(), true, user);

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
			List<UserRole> urs = orgUnitService.getUserRolesWithUserFilter(position.getOrgUnit(), true, user);

			for (UserRole userRole : urs) {
				if (userRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL)) {
					addUserToQueue(user, userRole.getItSystem());
				}
			}
		}
	}
	
	@Transactional
	public void notifyServicedesk() {
		List<PendingManualUpdate> pendingManualUpdates = findAll();
		if (pendingManualUpdates == null || pendingManualUpdates.size() == 0) {
			return;
		}
		
		// remove duplicates (the duplicates are there to ensure we don't have a race condition
		// where we miss an update while running this scheduled task)
		List<PendingManualUpdate> filtered = new ArrayList<>();
		List<PendingManualUpdate> toRemove = new ArrayList<>();
		for (PendingManualUpdate update : pendingManualUpdates) {
			boolean alreadyFiltered = false;

			for (PendingManualUpdate f : filtered) {
				if (f.getUserId().equals(update.getUserId()) && f.getItSystemId() == update.getItSystemId()) {
					alreadyFiltered = true;
					break;
				}
			}

			if (!alreadyFiltered) {
				filtered.add(update);
			}
			else {
				toRemove.add(update);
			}
		}

		// wipe all duplicates
		delete(toRemove);

		// Group list by ItSystem so that we send 1 email at a time
		Map<Long, List<PendingManualUpdate>> groupByItSystemMap = filtered.stream().collect(Collectors.groupingBy(PendingManualUpdate::getItSystemId));

		for (Long itSystemId : groupByItSystemMap.keySet()) {
			ItSystem itSystem = itSystemService.getById(itSystemId);
			String emailAddress = itSystem.getEmail();

			List<User> users = groupByItSystemMap.get(itSystemId).stream().map(PendingManualUpdate::getUserId).map(uid -> userService.getByUserId(uid)).collect(Collectors.toList());

			StringBuilder usersAndRoles = new StringBuilder();

			Locale locale = LocaleUtils.toLocale(localeString.replace('-', '_'));

			for (User user : users) {
				String positionName = "";
				if (user.getPositions().size() > 0) {
					positionName = ", ansat i " + user.getPositions().get(0).getOrgUnit().getName();
				}

				usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { user.getName() + " (" + user.getUserId() + positionName + ")" }, locale));

				usersAndRoles.append("<ul>");

				List<SystemRole> systemRoles = userService.getAllSystemRoles(user, Collections.singletonList(itSystem));
				if (systemRoles == null || systemRoles.size() == 0) {
					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.noroles", null, locale));
				}
				else {
					for (SystemRole systemRole : systemRoles) {
						usersAndRoles.append("<li>" + systemRole.getName() + " &nbsp; (" + systemRole.getIdentifier() + ")</li>");
					}
				}

				usersAndRoles.append("</ul>");
			}

			String title = messageSource.getMessage("html.email.manual.title", new Object[] { itSystem.getName() }, locale);
			String message = messageSource.getMessage("html.email.manual.message.format", new Object[] { itSystem.getName(), usersAndRoles.toString() }, locale);

			try {
				emailService.sendMessage(emailAddress, title, message);
			}
			catch (Exception ex) {
				log.error("Exception occured while synchronizing manual ItSystem: " + itSystem + " Exception:" + ex.getMessage());

				// we just continue with the next one - someone has to fix this, and then perform a full sync
			}
		}

		delete(filtered);
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
