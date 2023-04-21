package dk.digitalidentity.rc.service.nemlogin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PendingNemLoginGroupUpdate;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.NemLoginConstraintType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.PendingNemLoginGroupUpdateService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.nemlogin.model.CreateGroupRequest;
import dk.digitalidentity.rc.service.nemlogin.model.CreateGroupResponse;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginAllRolesResponse;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginGroup;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginRole;
import dk.digitalidentity.rc.service.nemlogin.model.Scope;
import dk.digitalidentity.rc.service.nemlogin.model.TokenResponse;
import dk.digitalidentity.rc.service.nemlogin.model.UpdateGroupRequest;
import lombok.extern.slf4j.Slf4j;
@EnableCaching
@EnableScheduling
@Slf4j
@Service
public class NemLoginService {
	
	@Qualifier("nemLoginRestTemplate")
	@Autowired
	private RestTemplate restTemplate;
	
	@Autowired
	private NemLoginService self;
	
	@Autowired
	private RoleCatalogueConfiguration config;
	
	@Autowired
	private ItSystemService itSystemService;
	
	@Autowired
	private SystemRoleService systemRoleService;
	
	@Autowired
	private PendingNemLoginGroupUpdateService pendingNemLoginGroupUpdateService;
	
	@Autowired
	private UserRoleService userRoleService;
	
	@Autowired
	private UserRoleDao userRoleDao;

	@Transactional
	public void syncNemLoginRoles(boolean force) {
		if (!config.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}

		log.info("Updating NemLog-in systemroles");
		
		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NEMLOGIN it-system (either 0 or > 1 was found!)");
			return;
		}

		ItSystem itSystem = itSystems.get(0);
		List<SystemRole> existingSystemRoles = systemRoleService.getByItSystem(itSystem);
		if (existingSystemRoles.size() > 0 && !force) {
			log.debug("Will not perform a forced update of NemLog-in systemroles, as some already exists in database, so updating will happen at scheduled intervals instead");
			return;
		}

		List<NemLoginRole> roles = getAllRoles();
		
		if (roles == null || roles.size() == 0) {
			log.warn("Found 0 roles in NemLog-in");
			return;
		}
		
		try {
			SecurityUtil.loginSystemAccount();

			for (NemLoginRole nemLoginRole : roles) {
				if (existingSystemRoles.stream().anyMatch(sr -> Objects.equals(sr.getIdentifier(), nemLoginRole.getUuid()))) {
					SystemRole existingSystemRole = existingSystemRoles.stream()
							.filter(sr -> Objects.equals(sr.getIdentifier(), nemLoginRole.getUuid()))
							.findAny().get();

					boolean changes = false;
					if (!Objects.equals(existingSystemRole.getDescription(), nemLoginRole.getDescription())) {
						changes = true;
					}
					else if (!Objects.equals(existingSystemRole.getName(), nemLoginRole.getName())) {
						changes = true;
					}
					
					if (changes) {
						existingSystemRole.setDescription(nemLoginRole.getDescription());
						existingSystemRole.setName(nemLoginRole.getName());
	
						log.info("Updating " + existingSystemRole.getName() + " / " + existingSystemRole.getId());
						
						// update existing systemroles
						systemRoleService.save(existingSystemRole);
					}
				}
				else {
					SystemRole newSystemRole = new SystemRole();
					newSystemRole.setIdentifier(nemLoginRole.getUuid());
					newSystemRole.setName(nemLoginRole.getName());
					newSystemRole.setDescription(nemLoginRole.getDescription());
					newSystemRole.setItSystem(itSystem);
					newSystemRole.setRoleType(RoleType.BOTH);

					log.info("Creating " + newSystemRole.getName());

					// add new systemroles
					systemRoleService.save(newSystemRole);
				}
			}
			
			// delete removed systemroles
			for (SystemRole systemRole : existingSystemRoles) {
				if (roles.stream().noneMatch(sr -> Objects.equals(sr.getUuid(), systemRole.getIdentifier()))) {
					log.info("Deleting " + systemRole.getName() + " / " + systemRole.getId());

					systemRoleService.delete(systemRole);
				}
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}
		
		log.info("Done synchronizing NemLog-in roles");
	}
	
	@Transactional
	public void synchronizeUserRoles() {
		if (!config.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}
		
		try {
			List<PendingNemLoginGroupUpdate> pendingUserRoleUpdates = pendingNemLoginGroupUpdateService.findAllByFailedFalse();
			if (pendingUserRoleUpdates == null || pendingUserRoleUpdates.size() == 0) {
				return;
			}
			
			log.info("Synchronizing " + pendingUserRoleUpdates.size() + " userroles to NemLog-in");
	
			for (PendingNemLoginGroupUpdate pendingNemLoginGroupUpdate : pendingUserRoleUpdates) {
				boolean success = false;

				log.info("Synchronizing " + pendingNemLoginGroupUpdate.getUserRoleId() + " / " + pendingNemLoginGroupUpdate.getUserRoleUuid() +  " to NemLog-in");

				switch (pendingNemLoginGroupUpdate.getEventType()) {
					case DELETE:
						success = deleteUserRole(pendingNemLoginGroupUpdate);
						break;
					case UPDATE:
						if (pendingNemLoginGroupUpdate.getUserRoleUuid() == null) {
							success = createUserRole(pendingNemLoginGroupUpdate);
						}
						else {
							success = updateUserRole(pendingNemLoginGroupUpdate);
						}
						break;
				}
	
				if (success) {
					pendingNemLoginGroupUpdateService.delete(pendingNemLoginGroupUpdate);
				}
				else {
					pendingNemLoginGroupUpdate.setFailed(true);
					pendingNemLoginGroupUpdateService.save(pendingNemLoginGroupUpdate);
				}
			}

			log.info("Done synchronizing userroles to NemLog-in");
		}
		catch (Exception ex) {
			log.error("Synchronizing userRoles to NemLog-in failed", ex);
		}
	}

	@Cacheable("token")
	public String fetchToken() {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/idmlogin/tls/authenticate";
		String accessToken = null;
		
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		
		try {
			ResponseEntity<TokenResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, TokenResponse.class);

			if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
				accessToken = response.getBody().getAccessToken();
			}
			else {
				log.error("Failed to fetch token from nemloginApi");
			}
		}
		catch (Exception ex) {
			log.error("Failed to fetch token from nemloginApi", ex);
		}
		
		return accessToken;
	}
	
	@CacheEvict(value = "token", allEntries = true)
	public void cleanUpToken() {
		;
	}
	
	@Scheduled(fixedRate = 5 * 60 * 1000)
	public void cleanUpTask() {
		self.cleanUpToken();
	}

	private List<NemLoginRole> getAllRoles() {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/categorizedRoles";
		HttpHeaders headers = getHeader();
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<NemLoginAllRolesResponse> response = restTemplate.exchange(url, HttpMethod.GET, request, NemLoginAllRolesResponse.class);
			if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
				return response.getBody().getRoles();
			}
			else {
				log.error("Failed to fetch all roles from nemloginApi. Won't sync roles. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
				return null;
			}
		}
		catch (Exception ex) {
			log.error("Failed to fetch all roles from nemloginApi. Won't sync roles.", ex);
			return null;
		}
	}
	
	private boolean createUserRole(PendingNemLoginGroupUpdate pendingNemLoginGroupUpdate) {
		UserRole userRole = userRoleService.getById(pendingNemLoginGroupUpdate.getUserRoleId());
		if (userRole == null) {
			log.warn("Attempted to synchronize UserRole to NemLogin with id " + pendingNemLoginGroupUpdate.getUserRoleId() + " but it does not exist anymore");
			
			// we return true to get it removed from the queue
			return true;
		}
		
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/group";
		HttpHeaders headers = getHeader();
		
		CreateGroupRequest createGroupRequest = new CreateGroupRequest();
		createGroupRequest.setName(userRole.getName());
		createGroupRequest.setDescription(userRole.getDescription());
		createGroupRequest.setOrganizationGroupIdentifier(userRole.getIdentifier());
		
		Scope scope = new Scope();
		if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.NONE)) {
			scope.setType("Cvr");
			scope.setValue(config.getCustomer().getCvr());
		}
		else if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.PNR)) {
			scope.setType("PU");
			scope.setValue(userRole.getNemloginConstraintValue());
		}
		else if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.SENR)) {
			scope.setType("SE");
			scope.setValue(userRole.getNemloginConstraintValue());
		}
		
		createGroupRequest.setScope(scope);
		HttpEntity<CreateGroupRequest> request = new HttpEntity<>(createGroupRequest, headers);
		try {
			ResponseEntity<CreateGroupResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, CreateGroupResponse.class);
			if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
				userRole.setUuid(response.getBody().getUuid());
				userRoleDao.save(userRole);
			}
			else {
				log.error("Failed to create userRole/group in NemLog-in. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
				return false;
			}
		}
		catch (Exception ex) {
			log.error("Failed to create userRole/group in NemLog-in", ex);
			return false;
		}
		
		for (SystemRoleAssignment assignment : userRole.getSystemRoleAssignments()) {
			addRoleToGroup(userRole, assignment);
		}
		
		return true;
	}

	private void addRoleToGroup(UserRole userRole, SystemRoleAssignment assignment) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/group/" + userRole.getUuid() + "/role/" + assignment.getSystemRole().getIdentifier();
		HttpHeaders headers = getHeader();
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

			if (response.getStatusCodeValue() > 299) {
				log.error("Failed to add systemRole with id " + assignment.getSystemRole().getId() + " to userRole/group in NemLog-in. UserRole with id " + userRole.getId() + ". Code " + response.getStatusCodeValue() + " body: " + response.getBody());
			}
		}
		catch (Exception ex) {
			log.error("Failed to add systemRole with id " + assignment.getSystemRole().getId() + " to userRole/group in NemLog-in. UserRole with id " + userRole.getId(), ex);
		}
	}
	
	private void deleteRoleFromGroup(UserRole userRole, String roleUuid) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/group/" + userRole.getUuid() + "/role/" + roleUuid;
		HttpHeaders headers = getHeader();
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);
			
			if (response.getStatusCodeValue() > 299) {
				log.error("Failed to delete NemLog-in role with uuid " + roleUuid + " from userRole/group in NemLog-in. UserRole with id " + userRole.getId() + ". Code " + response.getStatusCodeValue() + " body: " + response.getBody());
			}
		}
		catch (Exception ex) {
			log.error("Failed to delete NemLog-in role with uuid " + roleUuid + " from userRole/group in NemLog-in. UserRole with id " + userRole.getId(), ex);
		}
	}
	
	private NemLoginGroup getNemLoginGroup(UserRole userRole) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/group/" + userRole.getUuid();
		HttpHeaders headers = getHeader();
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<NemLoginGroup> response = restTemplate.exchange(url, HttpMethod.GET, request, NemLoginGroup.class);

			if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
				return response.getBody();
			}
			else {
				log.error("Failed to get userRole/group in NemLog-in. UserRole with id " + userRole.getId() + ". Code " + response.getStatusCodeValue() + " body: " + response.getBody());
				return null;
			}
		}
		catch (Exception ex) {
			log.error("Failed to get userRole/group in NemLog-in. UserRole with id " + userRole.getId(), ex);
			return null;
		}
	}

	private boolean updateUserRole(PendingNemLoginGroupUpdate pendingNemLoginGroupUpdate) {
		UserRole userRole = userRoleService.getById(pendingNemLoginGroupUpdate.getUserRoleId());
		if (userRole == null) {
			log.warn("Attempted to synchronize UserRole to NemLog-in with id " + pendingNemLoginGroupUpdate.getUserRoleId() + " but it does not exist anymore");
			
			// we return true to get it removed from the queue
			return true;
		}
		
		NemLoginGroup group = getNemLoginGroup(userRole);
		if (group == null) {
			log.error("Failed to find group with uuid " + userRole.getUuid() + " in NemLog-in. Won't update");
			
			// it will be flagged as failed in the queue, so we can replay it if needed
			return false;
		}
		
		boolean changes = checkForChanges(userRole, group);
		
		if (changes) {
			String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/group/" + userRole.getUuid();
			HttpHeaders headers = getHeader();
			
			UpdateGroupRequest updateGroupRequest = new UpdateGroupRequest();
			updateGroupRequest.setName(userRole.getName());
			updateGroupRequest.setDescription(userRole.getDescription());
			updateGroupRequest.setOrganizationGroupIdentifier(userRole.getIdentifier());

			Scope scope = group.getScope();
			if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.NONE)) {
				scope.setType("Cvr");
				scope.setValue(config.getCustomer().getCvr());
			}
			else if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.PNR)) {
				scope.setType("PU");
				scope.setValue(userRole.getNemloginConstraintValue());
			}
			else if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.SENR)) {
				scope.setType("SE");
				scope.setValue(userRole.getNemloginConstraintValue());
			}
			
			updateGroupRequest.setScope(scope);
			HttpEntity<UpdateGroupRequest> request = new HttpEntity<>(updateGroupRequest, headers);
			try {
				ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);

				if (response.getStatusCodeValue() > 299) {
					log.error("Failed to update userRole/group with id " + userRole.getId() + " in NemLog-in. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
					return false;
				}
			}
			catch (Exception ex) {
				log.warn("Failed to update userRole/group with id " + userRole.getId() + " in NemLog-in", ex);
				return false;
			}
		}
		
		// add missing roles to group
		List<String> assignedRoles = group.getRoles() == null ? new ArrayList<String>() : group.getRoles().stream().map(r -> r.getUuid()).toList();
		for (SystemRoleAssignment assignment : userRole.getSystemRoleAssignments()) {
			if (!assignedRoles.contains(assignment.getSystemRole().getIdentifier())) {
				addRoleToGroup(userRole, assignment);
			}
		}
		
		// remove groups that should not be in group
		List<String> assignedSystemRoleIdentifers = userRole.getSystemRoleAssignments().stream().map(u -> u.getSystemRole().getIdentifier()).toList();
		for (NemLoginRole role : group.getRoles()) {
			if (!assignedSystemRoleIdentifers.contains(role.getUuid())) {
				deleteRoleFromGroup(userRole, role.getUuid());
			}
		}
		
		return true;
	}

	private boolean checkForChanges(UserRole userRole, NemLoginGroup group) {
		if (!Objects.equals(userRole.getName(), group.getName())) {
			return true;
		}
		else if (!Objects.equals(userRole.getDescription(), group.getDescription())) {
			return true;
		}
		else if (!Objects.equals(userRole.getIdentifier(), group.getOrganizationGroupIdentifier())) {
			return true;
		}
		
		// check scopes
		if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.NONE) && !group.getScope().getType().equals("Cvr")) {
			return true;
		}
		else if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.PNR) && !group.getScope().getType().equals("PU")) {
			return true;
		}
		else if (userRole.getNemloginConstraintType().equals(NemLoginConstraintType.SENR) && !group.getScope().getType().equals("SE")) {
			return true;
		}
		else if (!Objects.equals(userRole.getNemloginConstraintValue(), group.getScope().getValue())) {
			return true;
		}
		
		return false;
	}

	private boolean deleteUserRole(PendingNemLoginGroupUpdate pendingNemLoginGroupUpdate) {
		if (pendingNemLoginGroupUpdate.getUserRoleUuid() == null) {
			log.error("Cannot delete UserRole without UUID.");

			return true;
		}
		
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/group/" + pendingNemLoginGroupUpdate.getUserRoleUuid();
		HttpHeaders headers = getHeader();
		
		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);
			if (response.getStatusCodeValue() > 299) {
				log.error("Failed to delete NemLog-in group with uuid " + pendingNemLoginGroupUpdate.getUserRoleUuid() + " in NemLog-in. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
				return false;
			}
			
			return true;
		}
		catch (Exception ex) {
			log.error("Failed to delete NemLog-in group with uuid " + pendingNemLoginGroupUpdate.getUserRoleUuid() + " in NemLog-in", ex);
			return false;
		}
	}
	
	private HttpHeaders getHeader() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		return headers;
	}
}
