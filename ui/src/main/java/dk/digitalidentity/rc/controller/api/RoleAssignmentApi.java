package dk.digitalidentity.rc.controller.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.service.DomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;

@RequireApiRoleManagementRole
@RestController
public class RoleAssignmentApi {
    private static class ErrorMessage {
        private static String USER_NOT_FOUND = "User not found.";
        private static String OU_NOT_FOUND = "OrgUnit not found.";
        private static String USER_ROLE_NOT_FOUND = "User Role not found.";
        private static String ROLE_GROUP_NOT_FOUND = "Role Group not found.";
    }

    @Autowired
    private UserService userService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleGroupService roleGroupService;

	@Autowired
	private DomainService domainService;
	
    // updates all direct assignments on users for this role
    @PostMapping("/api/overwriteAssignments/userrole/{userRoleId}")
    public ResponseEntity<String> overwriteUserRoleAssignments(@PathVariable("userRoleId") long userRoleId, @RequestBody Set<String> userIds, @RequestParam(name = "domain", required = false) String domain) {
        UserRole userRole = userRoleService.getById(userRoleId);
        if (userRole == null) {
            return new ResponseEntity<>("UserRole with id " + userRoleId + " does not exist", HttpStatus.NOT_FOUND);
        }

		Domain foundDomain = domainService.getDomainOrPrimary(domain);
		if (foundDomain == null) {
			return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
		}

        Set<String> usersWhoShouldHaveUserRole = userIds.stream().map(u -> u.toLowerCase()).collect(Collectors.toSet());
        Set<String> usersWithUserRole = userService.getActiveUsersWithUserRole(userRole).stream().map(uwr -> uwr.getUserUserId().toLowerCase()).collect(Collectors.toSet());

        // find all that should be added
        Set<String> toAdd = new HashSet<>(usersWhoShouldHaveUserRole);
        toAdd.removeAll(usersWithUserRole);
        for (String userId : toAdd) {
        	User user = userService.getByUserId(userId, foundDomain);
        	if (user != null) {
        		userService.addUserRole(user, userRole, LocalDate.now(), null);
    			userService.save(user);
        	}
        }
        
        // find all that should be removed
        Set<String> toRemove = new HashSet<>(usersWithUserRole);
        toRemove.removeAll(usersWhoShouldHaveUserRole);
        for (String userId : toRemove) {
        	User user = userService.getByUserId(userId, foundDomain);
        	if (user != null) {
        		userService.removeUserRole(user, userRole);
    			userService.save(user);
        	}
        }
        
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/user/{userUuid}/assign/userrole/{userRoleId}", method = RequestMethod.PUT)
    public ResponseEntity<String> assignUserRoleToUser(@PathVariable("userRoleId") long userRoleId, @PathVariable("userUuid") String userUuid, @RequestParam(name = "startDate", required = false) LocalDate startDate, @RequestParam(name = "stopDate", required = false) LocalDate stopDate, @RequestParam(name = "onlyIfNotAssigned", required = false, defaultValue = "true") boolean onlyIfNotAssigned, @RequestParam(name = "domain", required = false) String domain) {
        List<User> users = userService.getByExtUuid(userUuid);
        if (users == null || users.size() == 0) {
        	users = new ArrayList<>();

			Domain foundDomain = domainService.getDomainOrPrimary(domain);
			if (foundDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
			}
			
        	User user = userService.getByUserId(userUuid, foundDomain);
        	if (user != null) {
        		users.add(user);
        	}
        }
        
        UserRole userRole = userRoleService.getById(userRoleId);

		if (users.size() == 0) {
			return new ResponseEntity<>(ErrorMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
		}
		else if (userRole == null) {
			return new ResponseEntity<>(ErrorMessage.USER_ROLE_NOT_FOUND, HttpStatus.NOT_FOUND);
		}
		
		for (User user : users) {
			if (onlyIfNotAssigned) {
				// if already assigned, skip it
				if (user.getUserRoleAssignments().stream().anyMatch(ura -> ura.getUserRole().getId() == userRoleId)) {
					continue;
				}
			}

			userService.addUserRole(user, userRole, startDate, stopDate);
			userService.save(user);
		}

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/user/{userUuid}/assign/rolegroup/{roleGroupId}", method = RequestMethod.PUT)
    public ResponseEntity<String> assignRoleGroupToUser(@PathVariable("roleGroupId") long roleGroupId, @PathVariable("userUuid") String userUuid, @RequestParam(name = "startDate", required = false) LocalDate startDate, @RequestParam(name = "stopDate", required = false) LocalDate stopDate, @RequestParam(name = "domain", required = false) String domain) {
        List<User> users = userService.getByExtUuid(userUuid);
        if (users == null || users.size() == 0) {
        	users = new ArrayList<>();

			Domain foundDomain = domainService.getDomainOrPrimary(domain);
			if (foundDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
			}
			
        	User user = userService.getByUserId(userUuid, foundDomain);
        	if (user != null) {
        		users.add(user);
        	}
        }

        RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

		if (users.size() == 0) {
			return new ResponseEntity<>(ErrorMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
		}
		else if (roleGroup == null) {
			return new ResponseEntity<>(ErrorMessage.ROLE_GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
		}

		for (User user : users) {
			userService.addRoleGroup(user, roleGroup, startDate, stopDate);
			userService.save(user);

		}

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ou/{ouUuid}/assign/userrole/{userRoleId}", method = RequestMethod.PUT)
    public ResponseEntity<String> assignUserRoleToOrgUnit(@PathVariable("userRoleId") long userRoleId, @PathVariable("ouUuid") String ouUuid, @RequestParam(name = "startDate", required = false) LocalDate startDate, @RequestParam(name = "stopDate", required = false) LocalDate stopDate) {
        OrgUnit ou = orgUnitService.getByUuid(ouUuid);
        UserRole userRole = userRoleService.getById(userRoleId);

        if (ou == null) {
            return new ResponseEntity<>(ErrorMessage.OU_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else if (userRole == null) {
            return new ResponseEntity<>(ErrorMessage.USER_ROLE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

		orgUnitService.addUserRole(ou, userRole, false, startDate, stopDate, new HashSet<>(), new HashSet<>());
		orgUnitService.save(ou);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ou/{ouUuid}/assign/rolegroup/{roleGroupId}", method = RequestMethod.PUT)
    public ResponseEntity<String> assignRoleGroupToOrgUnit(@PathVariable("roleGroupId") long roleGroupId, @PathVariable("ouUuid") String ouUuid, @RequestParam(name = "startDate", required = false) LocalDate startDate, @RequestParam(name = "stopDate", required = false) LocalDate stopDate) {
        OrgUnit ou = orgUnitService.getByUuid(ouUuid);
        RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

        if (ou == null) {
            return new ResponseEntity<>(ErrorMessage.OU_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else if (roleGroup == null) {
            return new ResponseEntity<>(ErrorMessage.ROLE_GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

		orgUnitService.addRoleGroup(ou, roleGroup, false, startDate, stopDate, new HashSet<>(), new HashSet<>());
		orgUnitService.save(ou);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/user/{userUuid}/deassign/userrole/{userRoleId}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deassignUserRoleToUser(@PathVariable("userRoleId") long userRoleId, @PathVariable("userUuid") String userUuid, @RequestParam(name = "domain", required = false) String domain) {
        List<User> users = userService.getByExtUuid(userUuid);
        if (users == null || users.size() == 0) {
        	users = new ArrayList<>();

			Domain foundDomain = domainService.getDomainOrPrimary(domain);
			if (foundDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
			}
			
        	User user = userService.getByUserId(userUuid, foundDomain);
        	if (user != null) {
        		users.add(user);
        	}
        }

        UserRole userRole = userRoleService.getById(userRoleId);

		if (users.size() == 0) {
			return new ResponseEntity<>(ErrorMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
		}
		else if (userRole == null) {
			return new ResponseEntity<>(ErrorMessage.USER_ROLE_NOT_FOUND, HttpStatus.NOT_FOUND);
		}

		for (User user : users) {
	        if (userService.removeUserRole(user, userRole)) {
	        	userService.save(user);
	        }
		}

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/user/{userUuid}/deassign/rolegroup/{roleGroupId}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deassignRoleGroupToUser(@PathVariable("roleGroupId") long roleGroupId, @PathVariable("userUuid") String userUuid, @RequestParam(name = "domain", required = false) String domain) {
        List<User> users = userService.getByExtUuid(userUuid);
        if (users == null || users.size() == 0) {
        	users = new ArrayList<>();

			Domain foundDomain = domainService.getDomainOrPrimary(domain);
			if (foundDomain == null) {
				return new ResponseEntity<>("Failed to find domain with name " + domain, HttpStatus.NOT_FOUND);
			}
			
        	User user = userService.getByUserId(userUuid, foundDomain);
        	if (user != null) {
        		users.add(user);
        	}
        }

        RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

		if (users.size() == 0) {
			return new ResponseEntity<>(ErrorMessage.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
		}
		else if (roleGroup == null) {
			return new ResponseEntity<>(ErrorMessage.ROLE_GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
		}

		for (User user : users) {
	        if (userService.removeRoleGroup(user, roleGroup)) {
	        	userService.save(user);
	        }
		}

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ou/{ouUuid}/deassign/userrole/{userRoleId}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deassignUserRoleToOrgUnit(@PathVariable("userRoleId") long userRoleId, @PathVariable("ouUuid") String ouUuid) {
        OrgUnit ou = orgUnitService.getByUuid(ouUuid);
        UserRole userRole = userRoleService.getById(userRoleId);

        if (ou == null) {
            return new ResponseEntity<>(ErrorMessage.OU_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        else if (userRole == null) {
            return new ResponseEntity<>(ErrorMessage.USER_ROLE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (orgUnitService.removeUserRole(ou, userRole)) {
        	orgUnitService.save(ou);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @RequestMapping(value = "/api/ou/{ouUuid}/deassign/rolegroup/{roleGroupId}", method = RequestMethod.DELETE)
    public ResponseEntity<String> deassignRoleGroupToOrgUnit(@PathVariable("roleGroupId") long roleGroupId, @PathVariable("ouUuid") String ouUuid) {
        OrgUnit ou = orgUnitService.getByUuid(ouUuid);
        RoleGroup roleGroup = roleGroupService.getById(roleGroupId);

        if (ou == null) {
            return new ResponseEntity<>(ErrorMessage.OU_NOT_FOUND, HttpStatus.NOT_FOUND);
        } else if (roleGroup == null) {
            return new ResponseEntity<>(ErrorMessage.ROLE_GROUP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        if (orgUnitService.removeRoleGroup(ou, roleGroup)) {
        	orgUnitService.save(ou);
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }
}
