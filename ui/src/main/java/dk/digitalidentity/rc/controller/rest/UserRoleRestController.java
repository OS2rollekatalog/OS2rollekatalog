package dk.digitalidentity.rc.controller.rest;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleDeleteStatus;
import dk.digitalidentity.rc.controller.mvc.viewmodel.UserRoleForm;
import dk.digitalidentity.rc.controller.validator.UserRoleValidator;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.PositionService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.util.IdentifierGenerator;
import lombok.extern.log4j.Log4j;

@RequireAdministratorRole
@Log4j
@RestController
public class UserRoleRestController {

	@Value("${kombit.domain}")
	private String domain;

    @Autowired
    private UserService userService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private RoleGroupService roleGroupService;

    @Autowired
    private UserRoleValidator userRoleValidator;

    @Autowired
    private SystemRoleService systemRoleService;
    
    @Autowired
    private ConstraintTypeService constraintTypeService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(userRoleValidator);
    }
    
    @PostMapping(value = "/rest/userroles/flag/{roleId}/{flag}")
    @ResponseBody
    public ResponseEntity<String> setSystemRoleFlag(@PathVariable("roleId") long roleId, @PathVariable("flag") String flag, @RequestParam(name = "active") boolean active) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>("Ukendt Jobfunktionsrolle", HttpStatus.BAD_REQUEST);
        }
        
        switch (flag) {
        	case "useronly":
        		role.setUserOnly(active);
        		userRoleService.save(role);
        		
        		if (active) {
        			List<OrgUnit> orgUnitsWithRole = orgUnitService.getByUserRole(role);
        			
        			// if assigned to an OrgUnit already, return a warning (HTTP 400 is not really
        			// suitable for this, but there does not seem to be HTTP codes to return warnings)
        			if (orgUnitsWithRole.size() > 0) {
        	            return new ResponseEntity<>("Opdateret - bemærk eksisterende enheder har denne rolle tildelt allerede!", HttpStatus.BAD_REQUEST);
        			}
        		}
        		break;
        	case "inherit":
        		role.setOuInheritAllowed(active);
        		userRoleService.save(role);
        		break;
        	case "canrequest":
        		role.setCanRequest(active);
        		userRoleService.save(role);
        		break;
        	default:
        		return new ResponseEntity<>("Ukendt flag: " + flag, HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }
    
    @PostMapping(value = "/rest/userroles/edit/{roleId}/addSystemRole/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> addSystemRole(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        ItSystem itSystem = role.getItSystem();

        if (itSystem == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);

        //Find and assign system role
        for (SystemRole systemRole : systemRoles) {
            if (systemRole.getId() == systemRoleId) {
                SystemRoleAssignment roleAssignment = new SystemRoleAssignment();
                roleAssignment.setUserRole(role);
                roleAssignment.setSystemRole(systemRole);
                roleAssignment.setAssignedByName(SecurityUtil.getUserFullname());
                roleAssignment.setAssignedByUserId(SecurityUtil.getUserId());
                roleAssignment.setAssignedTimestamp(new Date());
                
                userRoleService.addSystemRoleAssignment(role, roleAssignment);
                userRoleService.save(role);
                
                return new ResponseEntity<>(HttpStatus.OK);
            }
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(value = "/rest/userroles/edit/{roleId}/removeSystemRole/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> removeSystemRole(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        SystemRoleAssignment assignment = null;
    	for (SystemRoleAssignment roleAssignment : role.getSystemRoleAssignments()) {
            if (roleAssignment.getSystemRole().getId() == systemRoleId) {
            	assignment = roleAssignment;
            	break;
            }
		}

        if (assignment != null) {
        	userRoleService.removeSystemRoleAssignment(role, assignment);
            userRoleService.save(role);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/userroles/edit/{roleId}/addConstraint/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> addConstraint(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId, String constraintUuid, String constraintValue, ConstraintValueType constraintValueType) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        
        SystemRole systemRole = systemRoleService.getById(systemRoleId);
        if (systemRole == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        SystemRoleAssignment roleAssignment = null;
        for (SystemRoleAssignment assignment : role.getSystemRoleAssignments()) {
            if (assignment.getSystemRole().equals(systemRole)) {
                roleAssignment = assignment;
                break;
            }
        }
        
        if (roleAssignment == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        
        ConstraintType constraintType = constraintTypeService.getByUuid(constraintUuid);
        if (constraintType == null) {
        	return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        if (constraintValueType == null) {
        	constraintValueType = ConstraintValueType.VALUE;
		}

        // perform regex validation (if needed)
        if (constraintValueType.equals(ConstraintValueType.VALUE) && constraintType.getRegex() != null && constraintType.getRegex().length() > 0) {
        	try {
        		Pattern pattern = Pattern.compile(constraintType.getRegex());
        		Matcher matcher = pattern.matcher(constraintValue);
        		if (!matcher.matches()) {
        			log.warn("Input does not match regular expression: " + constraintValue + " for regex: " + constraintType.getRegex());

        			return new ResponseEntity<>("Ugyldig dataafgrænsningsværdi!", HttpStatus.BAD_REQUEST);
        		}
        	}
        	catch (Exception ex) {
        		log.warn("Unable to perform regex validation (giving it a free pass) on '" + constraintType.getEntityId() + "'. Message = " + ex.getMessage());
        	}
        }

        SystemRoleAssignmentConstraintValue systemRoleAssignmentConstraintValue = null;
        for (SystemRoleAssignmentConstraintValue srcav : roleAssignment.getConstraintValues()) {
        	if (srcav.getConstraintType().getUuid().equals(constraintType.getUuid())) {
        		systemRoleAssignmentConstraintValue = srcav;
                systemRoleAssignmentConstraintValue.setConstraintValue(constraintValue);
                systemRoleAssignmentConstraintValue.setConstraintValueType(constraintValueType);
                systemRoleAssignmentConstraintValue.setConstraintIdentifier(IdentifierGenerator.buildKombitConstraintIdentifier(domain));
        		break;
        	}
		}
        
        if (systemRoleAssignmentConstraintValue == null) {
        	systemRoleAssignmentConstraintValue = new SystemRoleAssignmentConstraintValue();
            systemRoleAssignmentConstraintValue.setConstraintValue(constraintValue);
            systemRoleAssignmentConstraintValue.setSystemRoleAssignment(roleAssignment);
            systemRoleAssignmentConstraintValue.setConstraintType(constraintType);
            systemRoleAssignmentConstraintValue.setConstraintValueType(constraintValueType);
            systemRoleAssignmentConstraintValue.setConstraintIdentifier(IdentifierGenerator.buildKombitConstraintIdentifier(domain));
        }
        
		roleAssignment.getConstraintValues().add(systemRoleAssignmentConstraintValue);
        
        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/userroles/edit/{roleId}/removeConstraint/{systemRoleId}")
    @ResponseBody
    public ResponseEntity<String> removeConstraint(@PathVariable("roleId") long roleId, @PathVariable("systemRoleId") long systemRoleId, String constraintUuid) {
        UserRole role = userRoleService.getById(roleId);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        
        SystemRole systemRole = systemRoleService.getById(systemRoleId);
        if (systemRole == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        SystemRoleAssignment roleAssignment = null;
        for (SystemRoleAssignment assignment : role.getSystemRoleAssignments()) {
            if (assignment.getSystemRole().equals(systemRole)) {
                roleAssignment = assignment;
                break;
            }
        }
        
        if (roleAssignment == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        
        ConstraintType constraintType = constraintTypeService.getByUuid(constraintUuid);
        if (constraintType == null) {
        	return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        SystemRoleAssignmentConstraintValue systemRoleAssignmentConstraintValue = null;
        for (SystemRoleAssignmentConstraintValue srcav : roleAssignment.getConstraintValues()) {
        	if (srcav.getConstraintType().getUuid().equals(constraintType.getUuid())) {
        		systemRoleAssignmentConstraintValue = srcav;
        		break;
        	}
		}

        if (systemRoleAssignmentConstraintValue != null) {
        	roleAssignment.getConstraintValues().remove(systemRoleAssignmentConstraintValue);
        }

        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/userroles/edit")
    public ResponseEntity<String> editRoleAsync(@Valid @ModelAttribute("role") UserRoleForm userRoleForm, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        UserRole role = userRoleService.getById(userRoleForm.getId());
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        role.setName(userRoleForm.getName());
        role.setDescription(userRoleForm.getDescription());

        userRoleService.save(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping(value = "/rest/userroles/delete/{id}")
    public ResponseEntity<String> deleteRoleAsync(@PathVariable("id") long id) {
        UserRole role = userRoleService.getById(id);
        if (role == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

		if (role.getItSystem().getSystemType().equals(ItSystemType.KSPCICS)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

        // there are not that many rolegroups, we can do a full scan
        for (RoleGroup roleGroup : roleGroupService.getAll()) {
        	if (roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(role)) {
	        	roleGroupService.removeUserRole(roleGroup, role);
	        	roleGroupService.save(roleGroup);
        	}
        }

        // we have to use the deprecated method to ensure that inactive users are also cleaned up
        @SuppressWarnings("deprecation")
        List<User> users = userService.getByRolesIncludingInactive(role);
        for (User user : users) {
        	userService.removeUserRole(user, role);
        	userService.save(user);
        }

        // we have to use the deprecated method to ensure that inactive OUs are also cleaned up
        @SuppressWarnings("deprecation")
        List<OrgUnit> ous = orgUnitService.getAllWithRoleIncludingInactive(role);
        for (OrgUnit orgUnit : ous) {
        	orgUnitService.removeUserRole(orgUnit, role);
        	orgUnitService.save(orgUnit);
        }

        for (Position position : positionService.getAllWithRole(role)) {
        	positionService.removeUserRole(position, role);
        	positionService.save(position);
        }

        userRoleService.delete(role);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping(value = "/rest/userroles/trydelete/{id}")
    public ResponseEntity<UserRoleDeleteStatus> tryDelete(@PathVariable("id") long id) {
        UserRoleDeleteStatus status = new UserRoleDeleteStatus();

        UserRole userRole = userRoleService.getById(id);
        if (userRole == null) {
        	status.setSuccess(false);
            return new ResponseEntity<>(status, HttpStatus.OK);
        }

        status.setSuccess(true);
        
        long count = orgUnitService.countAllWithRole(userRole);
        if (count > 0) {
        	status.setOus(count);
        	status.setSuccess(false);
        }

        count = userService.countAllWithRole(userRole);
        if (count > 0) {
            status.setUsers(count);
        	status.setSuccess(false);
        }

        // there are not that many role groups, we can do a full scan
        for (RoleGroup roleGroup : roleGroupService.getAll()) {
          	if (roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList()).contains(userRole)) {
                status.setRoleGroups(status.getRoleGroups() + 1);
            	status.setSuccess(false);
            }
        }

        // have to check the corresponding user - could probably improve this at some point with a JOIN
        for (Position position : positionService.getAllWithRole(userRole)) {
        	if (position.getUser().isActive()) {
	            status.setUsers(status.getUsers() + 1);
	        	status.setSuccess(false);
        	}
        }

        return new ResponseEntity<>(status, HttpStatus.OK);
    }
}
