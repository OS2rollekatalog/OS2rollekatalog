package dk.digitalidentity.rc.task;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.PendingOrganisationUpdateDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PendingOrganisationUpdate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.AttestationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.OrganisationEventAction;
import lombok.extern.log4j.Log4j;

@Component
@EnableScheduling
@Log4j
public class ActOnOrganisationChangesTask {
	private OrganisationEventAction ouNewManagerAction = OrganisationEventAction.RIGHTS_KEPT;
	private OrganisationEventAction ouNewParentAction = OrganisationEventAction.RIGHTS_KEPT;
	private OrganisationEventAction userNewPositionAction = OrganisationEventAction.RIGHTS_KEPT;
	
	@Autowired
	private PendingOrganisationUpdateDao pendingOrganisationUpdateDao;
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private UserService userService;
	
	@Autowired
	private AttestationService attestationService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private RoleCatalogueConfiguration configuration;

	// Run daily at 06:00
	@Scheduled(cron = "0 0 6 * * ?")
	@Transactional(rollbackFor = Exception.class)
	public void actOnOrganisationChanges() {
		if (!configuration.getScheduled().isEnabled()) {
			return;
		}
		
		List<PendingOrganisationUpdate> events = pendingOrganisationUpdateDao.findAll();
		if (events.size() == 0) {
			return;
		}
		
		// reload configuration
		ouNewManagerAction = settingsService.getOuNewManagerAction();
		ouNewParentAction = settingsService.getOuNewParentAction();
		userNewPositionAction = settingsService.getUserNewPositionAction();
		
		try {
			SecurityUtil.loginSystemAccount();

			for (PendingOrganisationUpdate event : events) {
				switch (event.getEventType()) {
					case NEW_MANAGER:
						handleOuNewManager(event);
						break;
					case NEW_PARENT:
						handleOuNewParent(event);
						break;
					case NEW_POSITION:
						handleUserNewPosition(event);
						break;
					default:
						log.error("PendingOrganisationUpdate of type " + event.toString() + " is unknown!");
						break;
				}
				
				pendingOrganisationUpdateDao.delete(event);
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}
	}

	private void handleUserNewPosition(PendingOrganisationUpdate event) {
		User user = userService.getByUuid(event.getUserUuid());
		if (user == null) {
			log.warn("event on " + event.getUserUuid() + " ignored, because user does not exist");
			return;
		}
		
		switch (userNewPositionAction) {
			case RIGHTS_KEPT:
				// do nothing
				break;
			case RIGHTS_REMOVED: // currently same as direct rights removed
			case DIRECT_RIGHTS_REMOVED:
				userService.removeAllDirectlyAssignedRolesAndInformUser(user);
				break;
			case RIGHTS_NEEDS_APPROVAL:
				// TODO: not optimal, but probably what we can do for now
				OrgUnit ou = orgUnitService.getByUuid(event.getOrgUnitUuid());
				if (ou != null) {
					attestationService.flagOrgUnitForImmediateAttesation(ou);
				}
				break;
			default:
				log.error("userNewPositionAction of type " + userNewPositionAction.toString() + " is unknown!");
				break;
		}
	}

	private void handleOuNewParent(PendingOrganisationUpdate event) {
		OrgUnit orgUnit = orgUnitService.getByUuid(event.getOrgUnitUuid());
		if (orgUnit == null) {
			log.warn("event on " + event.getOrgUnitUuid() + " ignored, because orgUnit does not exist");
			return;
		}

		switch (ouNewParentAction) {
			case RIGHTS_KEPT:
				// do nothing
				break;
			case RIGHTS_REMOVED:
				removeAllRightsForAllUsers(orgUnit, true);
				break;
			case DIRECT_RIGHTS_REMOVED:
				removeDirectRightsForAllUsers(orgUnit, true);
				break;
			case RIGHTS_NEEDS_APPROVAL:
				flagAllUserForAttestation(orgUnit, true);
				break;
			default:
				log.error("ouNewParentAction of type " + ouNewParentAction.toString() + " is unknown!");
				break;
		}
	}
	
	private void handleOuNewManager(PendingOrganisationUpdate event) {
		OrgUnit orgUnit = orgUnitService.getByUuid(event.getOrgUnitUuid());
		if (orgUnit == null) {
			log.warn("event on " + event.getOrgUnitUuid() + " ignored, because orgUnit does not exist");
			return;
		}

		switch (ouNewManagerAction) {
			case RIGHTS_KEPT:
				// do nothing
				break;
			case RIGHTS_REMOVED:
				removeAllRightsForAllUsers(orgUnit, false);
				break;
			case DIRECT_RIGHTS_REMOVED:
				removeDirectRightsForAllUsers(orgUnit, false);
				break;
			case RIGHTS_NEEDS_APPROVAL:
				flagAllUserForAttestation(orgUnit, false);
				break;
			default:
				log.error("ouNewManagerAction of type " + ouNewManagerAction.toString() + " is unknown!");
				break;
		}
	}
	
	private void flagAllUserForAttestation(OrgUnit orgUnit, boolean recursive) {
		attestationService.flagOrgUnitForImmediateAttesation(orgUnit);
		
		if (recursive && orgUnit.getChildren() != null && orgUnit.getChildren().size() > 0) {
			for (OrgUnit child : orgUnit.getChildren()) {
				flagAllUserForAttestation(child, recursive);
			}
		}
	}

	private void removeAllRightsForAllUsers(OrgUnit orgUnit, boolean recursive) {
		List<User> users = userService.findByOrgUnit(orgUnit);
		for (User user : users) {
			userService.removeAllDirectAndOrgUnitAssignedRolesAndInformUser(user, orgUnit);
		}
		
		if (recursive && orgUnit.getChildren() != null && orgUnit.getChildren().size() > 0) {
			for (OrgUnit child : orgUnit.getChildren()) {
				removeAllRightsForAllUsers(child, recursive);
			}
		}
	}

	private void removeDirectRightsForAllUsers(OrgUnit orgUnit, boolean recursive) {
		List<User> users = userService.findByOrgUnit(orgUnit);
		for (User user : users) {
			userService.removeAllDirectlyAssignedRolesAndInformUser(user);
		}

		if (recursive && orgUnit.getChildren() != null && orgUnit.getChildren().size() > 0) {
			for (OrgUnit child : orgUnit.getChildren()) {
				removeDirectRightsForAllUsers(child, recursive);
			}
		}
	}
}
