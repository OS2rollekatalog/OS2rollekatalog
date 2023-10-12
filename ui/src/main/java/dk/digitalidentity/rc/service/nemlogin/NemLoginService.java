package dk.digitalidentity.rc.service.nemlogin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.DirtyNemLoginUserDao;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.DirtyNemLoginUser;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PNumber;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.SENumber;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentWithInfo;
import dk.digitalidentity.rc.service.model.UserWithRole;
import dk.digitalidentity.rc.service.nemlogin.model.AssignedRole;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginAllRolesResponse;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginRole;
import dk.digitalidentity.rc.service.nemlogin.model.Scope;
import dk.digitalidentity.rc.service.nemlogin.model.TokenResponse;
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
	private UserRoleService userRoleService;
	
	@Autowired
	private UserService userService;

	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private ConstraintTypeService constraintTypeService;

	@Autowired
	private DirtyNemLoginUserDao dirtyNemLoginUserDao;
	
	public void addRoleGroupToQueue(RoleGroup roleGroup) {
		if (roleGroup != null && roleGroup.getUserRoleAssignments() != null) {
			List<UserRole> userRoles = roleGroup.getUserRoleAssignments().stream().map(ura -> ura.getUserRole()).collect(Collectors.toList());

			for (UserRole userRole : userRoles) {
				addUserRoleToQueue(userRole);
			}
		}
	}

	public void addUserRoleToQueue(UserRole userRole) {
		ItSystem itSystem = userRole.getItSystem();

		if (!itSystem.getSystemType().equals(ItSystemType.NEMLOGIN)) {
			return;
		}

		for (UserWithRole userWithRole : userService.getUsersWithUserRole(userRole, true)) {
			addUserToQueue(userWithRole.getUser());
		}
	}

	public void addOrgUnitToQueue(OrgUnit orgUnit, boolean inherit) {
		for (User user : userService.findByOrgUnit(orgUnit)) {
			addUserToQueue(user);
		}

		// check if the assignment to the OrgUnit is flagged with inherit
		if (inherit) {
			addOrgUnitToQueueRecursive(orgUnit.getChildren());
		}
	}

	private void addOrgUnitToQueueRecursive(List<OrgUnit> children) {
		if (children == null) {
			return;
		}

		for (OrgUnit child : children) {
			for (User user : userService.findByOrgUnit(child)) {
				addUserToQueue(user);
			}
			
			addOrgUnitToQueueRecursive(child.getChildren());
		}
	}

	public void addUserToQueue(User user) {
		if (!StringUtils.hasLength(user.getNemloginUuid()) || user.isDeleted()) {
			return;
		}

		DirtyNemLoginUser dirty = new DirtyNemLoginUser();
		dirty.setUser(user);
		dirty.setTimestamp(LocalDateTime.now());

		dirtyNemLoginUserDao.save(dirty);
	}

	@Transactional
	public void syncExistingRoleAssignments() {
		if (!config.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}

		if (settingsService.isMitIDErhvervMigrationPerformed()) {
			return;
		}
	
		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NEMLOGIN it-system (either 0 or > 1 was found!)");
			return;
		}

		ItSystem itSystem = itSystems.get(0);
		List<SystemRole> existingRoles = systemRoleService.getByItSystem(itSystem);
		if (existingRoles.isEmpty()) {
			log.info("Will not perform MitID Erhverv migration, as no NemLog-in systemRoles exists in database");
			return;
		}
		
		if (userService.getAllWithNemLoginUuid().size() == 0) {
			log.info("Will not perform MitID Erhverv migration, because no users exists in DB with MitID Erhverv UUID");
			return;
		}

		ConstraintType pnrConstraint = constraintTypeService.getByEntityId("https://nemlogin.dk/constraints/pnr/1");
		ConstraintType senrConstraint = constraintTypeService.getByEntityId("https://nemlogin.dk/constraints/senr/1");
		if (pnrConstraint == null || senrConstraint == null) {
			log.error("ABORT! Failed to find pnr/senr constraints");
			return;
		}

		log.info("Performing migration of existing role assignments in MitID Erhverv to OS2rollekatalog");

		try {
			SecurityUtil.loginSystemAccount();

			List<User> nemLoginUsers = userService.getAllWithNemLoginUuid();
			for (User user : nemLoginUsers) {
				List<AssignedRole> assignedRoles = getRolesForUser(user);
				if (assignedRoles == null || assignedRoles.size() == 0) {
					continue;
				}

				for (AssignedRole assignedRole : assignedRoles) {
					SystemRole matchingSystemRole = systemRoleService.getFirstByIdentifierAndItSystemId(assignedRole.getUuid(), itSystem.getId());
					if (matchingSystemRole == null) {
						log.warn("Could not find a systemRole matching " + assignedRole.getName() + " (" + assignedRole.getUuid() + ") for " + user.getUserId());
						continue;
					}

					UserRole existingUserRole = null;
					boolean assigned = false;

					if (assignedRole.getScope().getType().equals("SE")) {
						existingUserRole = userRoleService.getByNameAndItSystem(assignedRole.getName() + " med senr", itSystem);
					}
					else if (assignedRole.getScope().getType().equals("PU")) {
						existingUserRole = userRoleService.getByNameAndItSystem(assignedRole.getName() + " med pnr", itSystem);
					}
					else if (assignedRole.getScope().getType().equals("Cvr")) {
						existingUserRole = userRoleService.getByNameAndItSystem(assignedRole.getName(), itSystem);
					}
					else {
						log.error("Unknown scope type " + assignedRole.getScope().getType() + " on NemLogin role with name " + assignedRole.getName());
						continue;
					}

					if (existingUserRole == null) {
						existingUserRole = new UserRole();
						existingUserRole.setItSystem(itSystem);
						existingUserRole.setDescription(assignedRole.getDescription());

						existingUserRole.setSystemRoleAssignments(new ArrayList<>());
						SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
						systemRoleAssignment.setSystemRole(matchingSystemRole);
						systemRoleAssignment.setUserRole(existingUserRole);
						systemRoleAssignment.setConstraintValues(new ArrayList<>());
						systemRoleAssignment.setAssignedByName("Systembruger");
						systemRoleAssignment.setAssignedByUserId("system");

						if (assignedRole.getScope().getType().equals("SE")) {
							existingUserRole.setName(assignedRole.getName() + " med senr");
							existingUserRole.setAllowPostponing(true);
							existingUserRole.setIdentifier(assignedRole.getUuid() + "_SE");

							SystemRoleAssignmentConstraintValue systemRoleAssignmentConstraintValue = new SystemRoleAssignmentConstraintValue();
							systemRoleAssignmentConstraintValue.setSystemRoleAssignment(systemRoleAssignment);
							systemRoleAssignmentConstraintValue.setPostponed(true);
							systemRoleAssignmentConstraintValue.setConstraintValueType(ConstraintValueType.POSTPONED);
							systemRoleAssignmentConstraintValue.setConstraintType(senrConstraint);

							systemRoleAssignment.getConstraintValues().add(systemRoleAssignmentConstraintValue);
						}
						else if (assignedRole.getScope().getType().equals("PU")) {
							existingUserRole.setName(assignedRole.getName() + " med pnr");
							existingUserRole.setAllowPostponing(true);
							existingUserRole.setIdentifier(assignedRole.getUuid() + "_PU");

							SystemRoleAssignmentConstraintValue systemRoleAssignmentConstraintValue = new SystemRoleAssignmentConstraintValue();
							systemRoleAssignmentConstraintValue.setSystemRoleAssignment(systemRoleAssignment);
							systemRoleAssignmentConstraintValue.setPostponed(true);
							systemRoleAssignmentConstraintValue.setConstraintValueType(ConstraintValueType.POSTPONED);
							systemRoleAssignmentConstraintValue.setConstraintType(pnrConstraint);

							systemRoleAssignment.getConstraintValues().add(systemRoleAssignmentConstraintValue);
						}
						else if (assignedRole.getScope().getType().equals("Cvr")) {
							existingUserRole.setName(assignedRole.getName());
							existingUserRole.setAllowPostponing(false);
							existingUserRole.setIdentifier(assignedRole.getUuid() + "_Cvr");
						}

						existingUserRole.getSystemRoleAssignments().add(systemRoleAssignment);
						userRoleService.save(existingUserRole);
					}
					else {
						long existingId = existingUserRole.getId();
						
						if (user.getUserRoleAssignments().stream().anyMatch(u -> u.getUserRole().getId() == existingId)) {
							assigned = true;
						}
					}

					if (!assigned) {
						UserUserRoleAssignment userUserRoleAssignment = new UserUserRoleAssignment();
						userUserRoleAssignment.setUserRole(existingUserRole);
						userUserRoleAssignment.setUser(user);
						userUserRoleAssignment.setAssignedByUserId("system");
						userUserRoleAssignment.setAssignedByName("Systembruger");
						userUserRoleAssignment.setPostponedConstraints(new ArrayList<>());

						if (assignedRole.getScope().getType().equals("SE") || assignedRole.getScope().getType().equals("PU")) {
							PostponedConstraint postponedConstraint = new PostponedConstraint();
							postponedConstraint.setUserUserRoleAssignment(userUserRoleAssignment);
							postponedConstraint.setValue(assignedRole.getScope().getValue());
							postponedConstraint.setSystemRole(matchingSystemRole);
							postponedConstraint.setConstraintType(assignedRole.getScope().getType().equals("SE") ? senrConstraint : pnrConstraint);
							userUserRoleAssignment.getPostponedConstraints().add(postponedConstraint);
						}

						user.getUserRoleAssignments().add(userUserRoleAssignment);
						userService.save(user);
					}
				}
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
			
			settingsService.setMitIDErhvervMigrationPerformed();
		}
	}

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

			ConstraintType pnrConstraint = constraintTypeService.getByEntityId("https://nemlogin.dk/constraints/pnr/1");
			ConstraintTypeSupport pnrSupportedConstraint = new ConstraintTypeSupport();
			pnrSupportedConstraint.setConstraintType(pnrConstraint);
			pnrSupportedConstraint.setMandatory(false);

			ConstraintType senrConstraint = constraintTypeService.getByEntityId("https://nemlogin.dk/constraints/senr/1");
			ConstraintTypeSupport senrSupportedConstraint = new ConstraintTypeSupport();
			senrSupportedConstraint.setConstraintType(senrConstraint);
			senrSupportedConstraint.setMandatory(false);

			for (NemLoginRole nemLoginRole : roles) {
				if (existingSystemRoles.stream().anyMatch(sr -> Objects.equals(sr.getIdentifier(), nemLoginRole.getUuid()))) {
					SystemRole existingSystemRole = existingSystemRoles.stream()
							.filter(sr -> Objects.equals(sr.getIdentifier(), nemLoginRole.getUuid()))
							.findAny().get();

					boolean changes = false;
					if (!Objects.equals(existingSystemRole.getDescription(), nemLoginRole.getDescription())) {
						existingSystemRole.setDescription(nemLoginRole.getDescription());
						changes = true;
					}
					if (!Objects.equals(existingSystemRole.getName(), nemLoginRole.getName())) {
						existingSystemRole.setName(nemLoginRole.getName());
						changes = true;
					}

					if (existingSystemRole.getSupportedConstraintTypes().stream().noneMatch(s -> s.getConstraintType().getEntityId().equals(pnrConstraint.getEntityId()))) {
						existingSystemRole.getSupportedConstraintTypes().add(pnrSupportedConstraint);
						changes = true;
					}
					if (existingSystemRole.getSupportedConstraintTypes().stream().noneMatch(s -> s.getConstraintType().getEntityId().equals(senrConstraint.getEntityId()))) {
						existingSystemRole.getSupportedConstraintTypes().add(senrSupportedConstraint);
						changes = true;
					}

					if (changes) {
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
					newSystemRole.setSupportedConstraintTypes(new ArrayList<>());
					newSystemRole.getSupportedConstraintTypes().add(pnrSupportedConstraint);
					newSystemRole.getSupportedConstraintTypes().add(senrSupportedConstraint);

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

	// This is called from NemLoginUpdateTask and does a periodic sync of data from OS2rollekatalog to NemLog-in
	// - synchronize any dirty user's roles (flagged as such by NemLoginUpdaterHook)
	@Transactional(rollbackFor = Exception.class)
	public void updateUserRoleAssignments() {
		List<DirtyNemLoginUser> dirtyUsers = dirtyNemLoginUserDao.findAll();
		if (dirtyUsers.isEmpty()) {
			return;
		}

		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NemLog-in it-system (either 0 or > 1 was found!)");
			return;
		}
		ItSystem itSystem = itSystems.get(0);

		List<DirtyNemLoginUser> processed = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (DirtyNemLoginUser dirtyUser : dirtyUsers) {
			if (!seen.contains(dirtyUser.getUser().getUuid())) {
				log.info("Checking for NemLog-in role modifications on user with uuid: " + dirtyUser.getUser().getUuid());

				try {
					// ensure we only process each of these once
					seen.add(dirtyUser.getUser().getUuid());

					if (dirtyUser.getUser().getNemloginUuid() != null && !dirtyUser.getUser().isDeleted()) {
						syncNemLoginRolesForUser(dirtyUser.getUser(), itSystem);
					}

					processed.add(dirtyUser);
					processed.addAll(dirtyUsers.stream().filter(d -> d.getUser().getUuid().equals(dirtyUser.getUser().getUuid())).collect(Collectors.toList()));
				}
				catch (Exception ex) {
					log.error("Failed to process NemLog-in role modifications on user with uuid: " + dirtyUser.getUser().getUuid(), ex);

					// if it failed, we do not flag it as processed
					continue;
				}
			}
		}

		// cleanup queue
		dirtyNemLoginUserDao.deleteAll(processed);

		log.info("DirtyNemLoginUser synchronization completed");
	}

	@Transactional(rollbackFor = Exception.class)
	public void fullRoleSync() {
		log.info("Running full NemLog-In role sync");
		
		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NemLog-in it-system (either 0 or > 1 was found!)");
			return;
		}
		ItSystem itSystem = itSystems.get(0);

		for (User user : userService.getAllWithNemLoginUuid()) {
			try {
				if (!user.isDeleted()) {
					syncNemLoginRolesForUser(user, itSystem);
				}
			}
			catch (Exception ex) {
				log.error("Failed to process NemLog-in role modifications on user with uuid: " + user.getUuid(), ex);
			}
		}

		log.info("Full NemLog-In role sync completed");
	}

	private void syncNemLoginRolesForUser(User user, ItSystem itSystem) throws Exception {
		List<UserRoleAssignmentWithInfo> assignedUserRoles = userService.getAllUserRolesAssignmentsWithInfo(user, Collections.singletonList(itSystem));

		Set<String> systemRoleIdentifiersNoConstraints = new HashSet<>();
		Map<String, Set<String>> systemRoleIdentifiersPnrConstraints = new HashMap<>();
		Map<String, Set<String>> systemRoleIdentifiersSenrConstraints = new HashMap<>();
		
		for (UserRoleAssignmentWithInfo userRoleAssignmentWithInfo : assignedUserRoles) {
			for (SystemRoleAssignment systemRoleAssignment : userRoleAssignmentWithInfo.getUserRole().getSystemRoleAssignments()) {
				if (userRoleAssignmentWithInfo.getPostponedConstraints() == null) {
					systemRoleIdentifiersNoConstraints.add(systemRoleAssignment.getSystemRole().getIdentifier());
				}
				else {
					boolean anyPostponedConstraint = false;

					for (PostponedConstraint postponedConstraint : userRoleAssignmentWithInfo.getPostponedConstraints()) {
						if (postponedConstraint.getSystemRole().getIdentifier().equals(systemRoleAssignment.getSystemRole().getIdentifier())) {
							if (postponedConstraint.getConstraintType().getEntityId().equals("https://nemlogin.dk/constraints/pnr/1")) {
								anyPostponedConstraint = true;
								if (!systemRoleIdentifiersPnrConstraints.containsKey(systemRoleAssignment.getSystemRole().getIdentifier())) {
									systemRoleIdentifiersPnrConstraints.put(systemRoleAssignment.getSystemRole().getIdentifier(), new HashSet<>());
								}

								systemRoleIdentifiersPnrConstraints.get(systemRoleAssignment.getSystemRole().getIdentifier()).add(postponedConstraint.getValue());
							}
							else if (postponedConstraint.getConstraintType().getEntityId().equals("https://nemlogin.dk/constraints/senr/1")) {
								anyPostponedConstraint = true;
								if (!systemRoleIdentifiersSenrConstraints.containsKey(systemRoleAssignment.getSystemRole().getIdentifier())) {
									systemRoleIdentifiersSenrConstraints.put(systemRoleAssignment.getSystemRole().getIdentifier(), new HashSet<>());
								}

								systemRoleIdentifiersSenrConstraints.get(systemRoleAssignment.getSystemRole().getIdentifier()).add(postponedConstraint.getValue());
							}
						}
					}

					if (!anyPostponedConstraint) {
						systemRoleIdentifiersNoConstraints.add(systemRoleAssignment.getSystemRole().getIdentifier());
					}
				}
			}
		}

		List<AssignedRole> assignedNemLoginRoles = getRolesForUser(user);
		if (assignedNemLoginRoles == null) {
			log.warn("assignedNemLoginRoles for user with NemLoginUuid " + user.getUuid() + " was null. Should at least be empty. Won't sync user's nemLogin roles.");
			return;
		}

		// remove roles
		for (AssignedRole assignedRole : assignedNemLoginRoles) {
			boolean delete = false;

			if (assignedRole.getScope().getType().equals("SE")) {
				if (systemRoleIdentifiersSenrConstraints.containsKey(assignedRole.getUuid())) {
					if (!systemRoleIdentifiersSenrConstraints.get(assignedRole.getUuid()).contains(assignedRole.getScope().getValue())) {
						delete = true;
					}
				}
				else {
					delete = true;
				}
			}
			else if (assignedRole.getScope().getType().equals("PU")) {
				if (systemRoleIdentifiersPnrConstraints.containsKey(assignedRole.getUuid())) {
					if (!systemRoleIdentifiersPnrConstraints.get(assignedRole.getUuid()).contains(assignedRole.getScope().getValue())) {
						delete = true;
					}
				}
				else {
					delete = true;
				}
			}
			else if (assignedRole.getScope().getType().equals("Cvr")) {
				if (!systemRoleIdentifiersNoConstraints.contains(assignedRole.getUuid())) {
					delete = true;
				}
			}
			else {
				log.error("Unknown scope type " + assignedRole.getScope().getType() + " on NemLogin role with name " + assignedRole.getName());
				continue;
			}

			if (delete) {
				deleteRoleFromUser(user, assignedRole);
			}
		}

		// add missing roles
		String cvr = config.getCustomer().getCvr();
		Scope cvrScope = new Scope("Cvr", cvr);
		for (String identifier : systemRoleIdentifiersNoConstraints) {
			if (assignedNemLoginRoles.stream().noneMatch(a -> a.getUuid().equals(identifier) && a.getScope().getType().equals("Cvr"))) {
				addRoleToUser(user, cvrScope, identifier);
			}
		}

		for (Map.Entry<String, Set<String>> pair : systemRoleIdentifiersPnrConstraints.entrySet()) {
			for (String pnrValue : pair.getValue()) {
				if (assignedNemLoginRoles.stream().noneMatch(a -> a.getUuid().equals(pair.getKey()) && a.getScope().getType().equals("PU") && a.getScope().getValue().equals(pnrValue))) {
					addRoleToUser(user, new Scope("PU", pnrValue), pair.getKey());
				}
			}
		}

		for (Map.Entry<String, Set<String>> pair : systemRoleIdentifiersSenrConstraints.entrySet()) {
			for (String senrValue : pair.getValue()) {
				if (assignedNemLoginRoles.stream().noneMatch(a -> a.getUuid().equals(pair.getKey()) && a.getScope().getType().equals("SE") && a.getScope().getValue().equals(senrValue))) {
					addRoleToUser(user, new Scope("SE", senrValue), pair.getKey());
				}
			}
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

	private List<AssignedRole> getRolesForUser(User user) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/identity/" + user.getNemloginUuid() + "/roles";
		HttpHeaders headers = getHeader();

		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<AssignedRole[]> response = restTemplate.exchange(url, HttpMethod.GET, request, AssignedRole[].class);
			
			// this checking is not really needed, as an exception is throw, but experience shows that updating the core framework sometimes
			// changes the default behavior of the framework, so let's be safe :)
			if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
				return Arrays.asList(response.getBody());
			}
			else {
				log.error("Failed to fetch roles assigned to user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
				return null;
			}
		}
		catch (Exception ex) {
			// if the user does not exist, the MitID Erhverv API will return HTTP 500 (and not 404), so we should do an extra check to see if the user
			// exists, and then log something more relevant in that case
			if (!userExists(user)) {
				log.warn("User does not exist in MitID Erhverv with UUID: " + user.getNemloginUuid() + " / " + user.getUserId());
			}
			else {
				log.error("Failed to fetch roles assigned to user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi.", ex);
			}
			
			return null;
		}
	}
	
	private boolean userExists(User user) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/identity/employee/" + user.getNemloginUuid();
		HttpHeaders headers = getHeader();

		HttpEntity<String> request = new HttpEntity<>(headers);
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
			
			// this checking is not really needed, as an exception is throw, but experience shows that updating the core framework sometimes
			// changes the default behavior of the framework, so let's be safe :)
			if (response.getStatusCodeValue() == 200 && response.getBody() != null) {
				return true;
			}
			else if (response.getStatusCodeValue() == 404) {
				return false;
			}

			log.warn("Failed to verify user existence of user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
		}
		catch (HttpStatusCodeException ex) {
			if (ex.getRawStatusCode() == 404) {
				return false;
			}
			
			log.warn("Failed to verify user existence of user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi. Code " + ex.getRawStatusCode(), ex);
		}

		return false;
	}

	record RoleBody(Scope scope, List<String> roleUuids) {}
	private void deleteRoleFromUser(User user, AssignedRole assignedRole) {
		if (config.getIntegrations().getNemLogin().isUserDryRunOnly()) {
			log.info("Removing " + assignedRole.getName() + " from " + user.getUserId());
			return;
		}

		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/identity/" + user.getNemloginUuid() + "/roles";
		HttpHeaders headers = getHeader();

		RoleBody roleBody = new RoleBody(assignedRole.getScope(), Collections.singletonList(assignedRole.getUuid()));
		HttpEntity<RoleBody> request = new HttpEntity<>(roleBody, headers);
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, request, String.class);
			if (response.getStatusCodeValue() == 204) {
				log.info("Removed NemLog-In role with uuid " + assignedRole.getUuid() + " from user with nemloginUuid " + user.getNemloginUuid());
			} else {
				log.error("Failed to delete role with uuid " + assignedRole.getUuid() + " assigned to user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
			}
		}
		catch (Exception ex) {
			log.error("Failed to delete role with uuid " + assignedRole.getUuid() + " assigned to user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi.", ex);
		}
	}

	private void addRoleToUser(User user, Scope scope, String roleUuid) {
		if (config.getIntegrations().getNemLogin().isUserDryRunOnly()) {
			log.info("Adding " + roleUuid + " to " + user.getUserId());
			return;
		}
		
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/identity/" + user.getNemloginUuid() + "/roles";
		HttpHeaders headers = getHeader();

		RoleBody roleBody = new RoleBody(scope, Collections.singletonList(roleUuid));
		HttpEntity<RoleBody> request = new HttpEntity<>(roleBody, headers);
		try {
			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
			if (response.getStatusCodeValue() == 204) {
				log.info("Added NemLog-In role with uuid " + roleUuid + " to user with nemloginUuid " + user.getNemloginUuid());
			}
			else {
				log.error("Failed to add role with uuid " + roleUuid + " to user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi. Code " + response.getStatusCodeValue() + " body: " + response.getBody());
			}
		}
		catch (Exception ex) {
			log.error("Failed to add role with uuid " + roleUuid + " to user with nemloginUuid " + user.getNemloginUuid() + " from nemloginApi.", ex);
		}
	}
	
	private HttpHeaders getHeader() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());

		return headers;
	}
	
	private record SeNumberDTORecord(String seNumber) {}
	public List<SENumber> getAllSENR() {
		String SENRResourceUrl = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/organization/senumber";

		HttpEntity<String> request = new HttpEntity<>(getHeader());
		try {
			ResponseEntity<List<SeNumberDTORecord>> response = restTemplate.exchange(SENRResourceUrl, HttpMethod.GET, request, new ParameterizedTypeReference<List<SeNumberDTORecord>>(){});
			List<SENumber> retList = new ArrayList<>();
			
			for (SeNumberDTORecord record : response.getBody()) {
				SENumber se = new SENumber();
				se.setCode(record.seNumber);
				se.setName(record.seNumber);
				
				retList.add(se);
			}
			
			return retList;
		}
		catch (Exception ex) {
			log.error("Failed to get SEnumbers", ex);

			return null;
		}
	}
	
	private record PNumberDTORecord(long pNumber, String cvrNumber, int mainActivity, boolean mainDivision) {}
	public List<PNumber> getAllPNR() {
		String PNRResourceUrl = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/organization/productionunits";
		HttpEntity<String> request = new HttpEntity<>(getHeader());

		try {
			List<PNumber> retList = new ArrayList<>();

			ResponseEntity<List<PNumberDTORecord>> response = restTemplate.exchange(PNRResourceUrl, HttpMethod.GET, request, new ParameterizedTypeReference<List<PNumberDTORecord>>(){});
			List<PNumberDTORecord> responseBody = response.getBody();

			for (PNumberDTORecord record : responseBody) {
				PNumber pNumber = new PNumber();
				pNumber.setCode(String.valueOf(record.pNumber));
				pNumber.setName(String.valueOf(record.pNumber));

				retList.add(pNumber);
			}
			
			return retList;
		}
		catch (Exception ex) {
			log.error("Failed to get Pnumbers", ex);

			return null;
		}
	}
	
	private record PUnitLookupDTORecord(String number, String street, String postalCode, String city, String name){}
	public String getPnrNameByCode(String pnr) {
		if (!config.getIntegrations().getCvr().isEnabled()) {
			log.info("CVR integration not enabled");
			return null;
		}
		
		RestTemplate restTemplate = new RestTemplate();
		String cvrResourceUrl = config.getIntegrations().getCvr().getBaseUrl() + "/CVR/HentCVRData/1/rest/hentProduktionsenhedMedPNummer?ppNummer=" + pnr;
		String apiKey = config.getIntegrations().getCvr().getApiKey();

		HttpEntity<String> request = new HttpEntity<>(getHeaders(apiKey));
		try {
			ResponseEntity<PUnitLookupDTORecord> response = restTemplate.exchange(cvrResourceUrl, HttpMethod.GET, request, PUnitLookupDTORecord.class);
			PUnitLookupDTORecord post = response.getBody();

			return post.name;
		}
		catch (RestClientException ex) {
			log.warn("Failed to lookup: " + pnr, ex);
			return null;
		}
	}
	
	private HttpHeaders getHeaders(String apiKey) {
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");
		headers.add("Authorization", "Bearer " + self.fetchToken());
		headers.add(apiKey, apiKey);
		
		return headers;
	}
}
