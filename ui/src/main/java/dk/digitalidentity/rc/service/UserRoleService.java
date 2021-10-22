package dk.digitalidentity.rc.service;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.log.AuditLogIntercepted;
import lombok.extern.log4j.Log4j;

@Log4j
@Service
public class UserRoleService {

	@Autowired
	private UserRoleDao userRoleDao;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SettingsService settingsService;

	@AuditLogIntercepted
	public boolean addSystemRoleAssignment(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (!userRole.getSystemRoleAssignments().contains(systemRoleAssignment)) {
			userRole.getSystemRoleAssignments().add(systemRoleAssignment);

			return true;
		}

		return false;
	}

	@AuditLogIntercepted
	public boolean removeSystemRoleAssignment(UserRole userRole, SystemRoleAssignment systemRoleAssignment) {
		if (userRole.getSystemRoleAssignments().contains(systemRoleAssignment)) {
			userRole.getSystemRoleAssignments().remove(systemRoleAssignment);

			return true;
		}

		return false;
	}

	public List<UserRole> getAllSensitiveRoles() {
		return userRoleDao.findBySensitiveRoleTrue();
	}

	public List<UserRole> getByItSystemId(long itSystemId) {
		return userRoleDao.findByItSystem(itSystemService.getById(itSystemId));
	}

	public List<UserRole> getByItSystem(ItSystem itSystem) {
		return userRoleDao.findByItSystem(itSystem);
	}

	@AuditLogIntercepted
	public UserRole save(UserRole userRole) {
		return userRoleDao.save(userRole);
	}

	@AuditLogIntercepted
	public void delete(UserRole userRole) {
		userRoleDao.delete(userRole);
	}

	public List<UserRole> getAll() {
		return userRoleDao.findAll();
	}

	public List<UserRole> getAllExceptRoleCatalogue() {
		List<ItSystem> roleCatalogue = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		if (roleCatalogue == null || roleCatalogue.size() != 1) {
			throw new RuntimeException("Role Catalogue does not exist as an ItSystem!");
		}

		return userRoleDao.findAll().stream().filter(it -> !(it.getItSystem().equals(roleCatalogue.get(0)))).collect(Collectors.toList());
	}

	public UserRole getById(long roleId) {
		return userRoleDao.findById(roleId);
	}

    public UserRole getByNameAndItSystem(String name, ItSystem itSystem) {
        return userRoleDao.getByNameAndItSystem(name, itSystem);
    }

	public UserRole getByIdentifier(String identifier) {
		return userRoleDao.getByIdentifier(identifier);
	}
	
	public boolean canRequestRole(UserRole role, User user) {
		if (!role.isCanRequest()) {
			return false;
		}

		// filter on itSystem
		if (settingsService.isItSystemMarkupEnabled()) {
			
			// get all it-systems from all orgunits that the user resides in
			Set<ItSystem> itSystems = user.getPositions().stream()
					.map(p -> p.getOrgUnit().getItSystems())
					.collect(Collectors.toList())
					.stream().flatMap(List::stream)
					.collect(Collectors.toSet());

			boolean found = false;
	    	for (ItSystem itSystem : itSystems) {
	    		if (itSystem.getId() == role.getItSystem().getId()) {
	    			found = true;
	    			break;
	    		}
	    	}

	    	if (!found) {
	    		return false;
	    	}
		}
		
		return true;
	}
	
	public List<UserRole> whichRolesCanBeRequestedByUser(List<UserRole> roles, User user) {

		// filter on canRequest
		roles = roles.stream().filter(r -> r.isCanRequest()).collect(Collectors.toList());
		
		// filter on itSystem
		if (settingsService.isItSystemMarkupEnabled()) {
			
			// get all it-systems from all orgunits that the user resides in
			Set<ItSystem> itSystems = user.getPositions().stream()
					.map(p -> p.getOrgUnit().getItSystems())
					.collect(Collectors.toList())
					.stream().flatMap(List::stream)
					.collect(Collectors.toSet());
			
			// filter out all userroles not found in one of the whitelisted it-systems
			for (Iterator<UserRole> iter = roles.iterator(); iter.hasNext(); ) {
			    UserRole userRole = iter.next();
		    	boolean found = false;

		    	for (ItSystem itSystem : itSystems) {
		    		if (itSystem.getId() == userRole.getItSystem().getId()) {
		    			found = true;
		    			break;
		    		}
		    	}

		    	if (!found) {
		    		iter.remove();
		    	}
			}
		}
		
		return roles;
	}

	@Transactional
	public void updateLinkedUserRoles() {
		List<UserRole> userRoles = findByLinkedSystemRoleNotNull();

		for (UserRole userRole : userRoles) {
			boolean changes = false;

			SystemRole linkedSystemRole = userRole.getLinkedSystemRole();
			if (linkedSystemRole == null) {
				log.warn("Linked systemrole does not exist any more for userRole: " + userRole.getId());
				userRole.setLinkedSystemRole(null);
				userRole.setLinkedSystemRolePrefix(null);
				changes = true;
			}
			else {
				String prefix = userRole.getLinkedSystemRolePrefix();
				if (prefix == null) {
					prefix = "";
				}
	
				// compare name
				if (!Objects.equals(userRole.getName(), prefix + linkedSystemRole.getName())) {
					userRole.setName(prefix + linkedSystemRole.getName());
					changes = true;
				}
	
				// compare description
				if (!Objects.equals(userRole.getDescription(), linkedSystemRole.getDescription())) {
					userRole.setDescription(linkedSystemRole.getDescription());
					changes = true;
				}
			}
			
			if (changes) {
				save(userRole);
			}
		}
	}

	public int countBySystemRoleAssignmentsSystemRole(SystemRole systemRole) {
		return userRoleDao.countBySystemRoleAssignmentsSystemRole(systemRole);
	}

	public List<UserRole> findByLinkedSystemRoleNotNull() {
		return userRoleDao.findByLinkedSystemRoleNotNull();
	}

}
