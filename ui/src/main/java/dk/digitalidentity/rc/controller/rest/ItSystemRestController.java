package dk.digitalidentity.rc.controller.rest;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import dk.digitalidentity.rc.controller.rest.model.OUFilterDTO;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingADGroupOperation;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ADGroupType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PendingADUpdateService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireAdministratorRole
@RestController
public class ItSystemRestController {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;
	
	@Autowired
	private UserRoleService userRoleService;
		
	@Autowired
	private PendingADUpdateService pendingADUpdateService;

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private PositionService positionService;

	@PostMapping(value = { "/rest/systemrole/delete/{id}" })
	@ResponseBody
	public HttpEntity<String> deleteSystemRole(Model model, @PathVariable("id") long id) {
		SystemRole systemRole = systemRoleService.getById(id);

		if (systemRole == null || (!systemRole.getItSystem().getSystemType().equals(ItSystemType.AD) &&
								   !systemRole.getItSystem().getSystemType().equals(ItSystemType.SAML) &&
								   !systemRole.getItSystem().getSystemType().equals(ItSystemType.MANUAL))) {

			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		List<UserRole> userRoles = userRoleService.getByItSystem(systemRole.getItSystem());
		if (userRoles != null) {
			for (UserRole userRole : userRoles) {
				for (SystemRoleAssignment assignment : userRole.getSystemRoleAssignments()) {
					if (assignment.getSystemRole().equals(systemRole)) {
						return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
					}
				}
			}
		}

		systemRoleService.delete(systemRole);

		if (systemRole.getItSystem().getSystemType().equals(ItSystemType.AD)) {
			PendingADGroupOperation operation = new PendingADGroupOperation();
			operation.setActive(false);
			operation.setItSystemIdentifier(systemRole.getItSystem().getIdentifier());
			operation.setSystemRoleId(null);
			operation.setSystemRoleIdentifier(systemRole.getIdentifier());
			operation.setTimestamp(new Date());
			operation.setAdGroupType(ADGroupType.NONE);
			operation.setDomain(systemRole.getItSystem().getDomain());

			pendingADUpdateService.save(operation);
		}
		
		return new ResponseEntity<>(HttpStatus.OK);
	}
	
    @PostMapping(value = "/rest/itsystem/delete/{id}")
    public ResponseEntity<String> deleteItSystemAsync(@PathVariable("id") long id) {
        ItSystem itSystem = itSystemService.getById(id);
        if (itSystem == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        log.info("Deleting it-system " + itSystem.getName() + " with id " + itSystem.getId());

        if (itSystem.getSystemType().equals(ItSystemType.KOMBIT) ||
        	itSystem.getSystemType().equals(ItSystemType.KSPCICS) ||
        	itSystem.getIdentifier().equals(Constants.ROLE_CATALOGUE_IDENTIFIER)) {
        	return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        
        // delete itsystem
        itSystem.setDeleted(true);
        itSystem.setDeletedTimestamp(new Date());
        itSystemService.save(itSystem);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/itsystem/name")
    public ResponseEntity<String> editItSystemName(long id, String name) {
    	if (name == null || name.length() < 2) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    
    	ItSystem itSystem = itSystemService.getById(id);
    	if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
    	
    	ItSystem existingSystem = itSystemService.getFirstByName(name);
    	if (existingSystem != null && existingSystem.getId() != itSystem.getId()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	itSystem.setName(name);
    	itSystemService.save(itSystem);

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@PostMapping(value = "/rest/itsystem/email")
	public ResponseEntity<String> editItSystemEmail(long id, String email) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (itSystem.getSystemType() == ItSystemType.MANUAL) {
			if (email == null || email.isEmpty()) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}

			if (!isEmailCorrect(email)) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
		}
		
		if ((itSystem.getSystemType() == ItSystemType.AD || itSystem.getSystemType() == ItSystemType.SAML) && (StringUtils.hasLength(email) && !isEmailCorrect(email))) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setEmail(email);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private boolean isEmailCorrect(CharSequence email) {
		if (email == null) {
			return false;
		}

		// Regular Expression by RFC 5322 for Email Validation
		String regex = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$";
		return Pattern.matches(regex, email);
	}
	
	@PostMapping(value = "/rest/itsystem/notificationemail")
	public ResponseEntity<String> editItSystemnNotificationEmail(long id, String email) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setNotificationEmail(email);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}
    
    @PostMapping(value = "/rest/itsystem/notes")
    public ResponseEntity<String> editItSystemNotes(long id, String notes) {
    	ItSystem itSystem = itSystemService.getById(id);
    	if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
    	
    	itSystem.setNotes(notes);
    	itSystemService.save(itSystem);

        return new ResponseEntity<>(HttpStatus.OK);
    }
    
    @PostMapping(value = "/rest/itsystem/paused")
    public ResponseEntity<String> editItSystemPaused(long id, boolean paused) {
    	ItSystem itSystem = itSystemService.getById(id);
    	if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
    	
    	itSystem.setPaused(paused);
    	itSystemService.save(itSystem);

    	if (!paused) {
    		// if the pause flag is removed, add the full it-system
    		// to the queue for synchronization
    		pendingADUpdateService.addItSystemToQueue(itSystem);
    	}
    	else {
    		pendingADUpdateService.removeItSystemFromQueue(itSystem);
    	}
    	
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/itsystem/readonly")
    public ResponseEntity<String> editItSystemReadonly(long id, boolean readonly) {
    	ItSystem itSystem = itSystemService.getById(id);
    	if (itSystem == null || itSystem.getSystemType() != ItSystemType.AD) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

    	itSystem.setReadonly(readonly);
    	itSystemService.save(itSystem);

    	if (!readonly) {
    		pendingADUpdateService.addItSystemToQueue(itSystem);
    	}
    	else {
    		pendingADUpdateService.removeItSystemFromQueue(itSystem);
    	}

    	return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/itsystem/canEditThroughApi")
    public ResponseEntity<String> editItSystemCanEditThroughApi(long id, boolean canEditThroughApi) {
    	ItSystem itSystem = itSystemService.getById(id);
    	if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML && itSystem.getSystemType() != ItSystemType.MANUAL) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

    	itSystem.setCanEditThroughApi(canEditThroughApi);
    	itSystemService.save(itSystem);

        return new ResponseEntity<>(HttpStatus.OK);
    }

	@PostMapping(value = "/rest/itsystem/hidden")
	public ResponseEntity<String> editItSystemHidden(long id, boolean hidden) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setHidden(hidden);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/itsystem/accessBlocked")
	public ResponseEntity<String> editItSystemAccessBlocked(long id, boolean accessBlocked) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setAccessBlocked(accessBlocked);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/itsystem/apiManagedRoleAssignments")
	public ResponseEntity<String> editItSystemManagedRoleAssignments(long id, boolean apiManagedRoleAssignments) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setApiManagedRoleAssignments(apiManagedRoleAssignments);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping(value = "/rest/itsystem/subscribedTo")
	public ResponseEntity<String> editItSystemSubscribedTo(long id, String masterId) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		// quick'n'dirty workaround for Javascript and null values
		if (masterId.equals("null")) {
			masterId = null;
		}

		itSystem.setSubscribedTo(masterId);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@PostMapping("/rest/itsystem/ouFilterEnabled")
	public ResponseEntity<String> editOUFilterEnabled(long id, boolean ouFilterEnabled) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		itSystem.setOuFilterEnabled(ouFilterEnabled);
		if (!ouFilterEnabled) {
			itSystem.getOrgUnitFilterOrgUnits().clear();
		}
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	@ResponseBody
	@PostMapping(value = "/rest/itsystem/oufilter")
	public ResponseEntity<String> editItSystemOUFilter(@RequestBody OUFilterDTO dto) {
		ItSystem itSystem = itSystemService.getById(dto.getId());
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		List<OrgUnit> ous = orgUnitService.getByUuidIn(dto.getSelectedOUs());
		itSystem.getOrgUnitFilterOrgUnits().clear();
		itSystem.getOrgUnitFilterOrgUnits().addAll(ous);
		itSystemService.save(itSystem);

		return new ResponseEntity<>(HttpStatus.OK);
	}
	
	// TODO: really should use the count() method instead
	@SuppressWarnings("deprecation")
	@PostMapping(value = "/rest/itsystem/userrole/unused/{id}")
	public ResponseEntity<String> deleteUnusedUserRoles(@PathVariable("id") long id) {
		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (itSystem.getSystemType().equals(ItSystemType.AD) && itSystem.isReadonly()) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (itSystem.getSystemType().equals(ItSystemType.KSPCICS)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		// delete unused user roles
		List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
		for (UserRole userRole : userRoles) {
			// check if assigned to user
			if (userService.countAllWithRole(userRole) > 0) {
				continue;
			}

			// check if assigned to orgUnit
			if (orgUnitService.countAllWithRole(userRole) > 0) {
				continue;
			}
			
			// check if assigned to position
			if (positionService.getAllWithRole(userRole).size() > 0) {
				continue;
			}

			userRoleService.delete(userRole);
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}

}
