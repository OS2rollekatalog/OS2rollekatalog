package dk.digitalidentity.rc.service.nemlogin;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ConstraintTypeSupport;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.PNumber;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SENumber;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.RoleType;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.nemlogin.model.AssignedRole;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginAllRolesResponse;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginRole;
import dk.digitalidentity.rc.service.nemlogin.model.NemLoginUserProfile;
import dk.digitalidentity.rc.service.nemlogin.model.Scope;
import dk.digitalidentity.rc.service.nemlogin.model.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@EnableCaching
@EnableScheduling
@Slf4j
@Service
public class NemLoginService {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Qualifier("nemLoginRestClient")
	@Autowired
	private RestClient restClient;

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
	private AssignmentService assignmentService;

	public void syncUserRoleAssignments(User user) {
		if (!StringUtils.hasLength(user.getNemloginUuid()) || user.isDeleted()) {
			return;
		}

		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NemLog-in it-system (either 0 or > 1 was found!)");
			return;
		}
		ItSystem itSystem = itSystems.get(0);

		Set<String> systemRoleIdentifiers = systemRoleService.findByItSystem(itSystem).stream()
			.map(SystemRole::getIdentifier)
			.collect(Collectors.toSet());

		Set<CurrentAssignment> assignments = assignmentService.getByUserAndItSystemsWithRoleDetails(user, Collections.singletonList(itSystem));

		try {
			log.info("Checking for NemLog-in role modifications on user with uuid: {}", user.getUuid());
			syncNemLoginRolesForUser(user, assignments, itSystem, systemRoleIdentifiers);
		} catch (Exception ex) {
			log.error("Failed to process NemLog-in role modifications on user with uuid: {}", user.getUuid(), ex);
		}
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
			// We do not know the exact time this was assigned, so use start of current date
			final Date assignedAt = Date.from(LocalDate.now().atStartOfDay(ZoneId.of("Europe/Paris")).toInstant());
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
						existingUserRole.setApproverPermission(Collections.singletonList(ApprovableBy.INHERIT));
						existingUserRole.setRequesterPermission(Collections.singletonList(RequestableBy.INHERIT));

						existingUserRole.setSystemRoleAssignments(new ArrayList<>());
						SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
						systemRoleAssignment.setSystemRole(matchingSystemRole);
						systemRoleAssignment.setUserRole(existingUserRole);
						systemRoleAssignment.setConstraintValues(new ArrayList<>());
						systemRoleAssignment.setAssignedByName("Systembruger");
						systemRoleAssignment.setAssignedByUserId("system");
						systemRoleAssignment.setAssignedTimestamp(assignedAt);

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
	public void syncAdminRoleAssignments() {
		if (!config.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}

		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NEMLOGIN it-system (either 0 or > 1 was found!)");
			return;
		}

		if (userService.getAllWithNemLoginUuid().size() == 0) {
			log.info("Will not perform MitID Erhverv admin role migration, because no users exists in DB with MitID Erhverv UUID");
			return;
		}

		log.info("Performing migration of existing admin role assignments in MitID Erhverv to OS2rollekatalog");

		try {
			// We do not know the exact time this was assigned, so use start of current date
			final Date assignedAt = Date.from(LocalDate.now().atStartOfDay(ZoneId.of("Europe/Paris")).toInstant());
			SecurityUtil.loginSystemAccount();

			// Get admin role identifiers for comparison
			Set<String> adminRoleIdentifiers = Set.of(
					config.getIntegrations().getNemLogin().getOrganizationAdministratorIdentifier(),
					config.getIntegrations().getNemLogin().getIdentityAdministratorIdentifier(),
					config.getIntegrations().getNemLogin().getRightsAdministratorIdentifier()
			);

			List<User> nemLoginUsers = userService.getAllWithNemLoginUuid();
			for (User user : nemLoginUsers) {
				// Sync admin roles only
				NemLoginUserProfile userProfile = getUserProfile(user);
				if (userProfile != null && userProfile.getIdentityProfile() != null && userProfile.getIdentityProfile().getRoles() != null) {
					syncAdminRoles(user, userProfile.getIdentityProfile().getRoles(), assignedAt, adminRoleIdentifiers);
				}
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}
	}

	private void syncAdminRoles(User user, Set<String> adminRoleIdentifiers, Date assignedAt, Set<String> knownAdminRoleIdentifiers) {
		boolean userChanged = false;

		// Remove admin roles that are no longer in MitID Erhverv
		Iterator<UserUserRoleAssignment> iterator = user.getUserRoleAssignments().iterator();
		while (iterator.hasNext()) {
			UserUserRoleAssignment assignment = iterator.next();
			UserRole userRole = assignment.getUserRole();

			// Check if this is an admin role assignment that should be removed
			if (userRole.getIdentifier() != null &&
				knownAdminRoleIdentifiers.contains(userRole.getIdentifier()) &&
				!adminRoleIdentifiers.contains(userRole.getIdentifier())) {

				iterator.remove();
				userChanged = true;
				log.info("Removing admin role assignment " + userRole.getName() + " from user " + user.getUserId());
			}
		}

		// Add missing admin roles
		for (String adminRoleIdentifier : adminRoleIdentifiers) {
			// Only process admin roles we know about
			if (!knownAdminRoleIdentifiers.contains(adminRoleIdentifier)) {
				continue;
			}

			// Find existing admin UserRole by identifier
			UserRole existingAdminUserRole = userRoleService.getByIdentifier(adminRoleIdentifier);

			if (existingAdminUserRole == null) {
				log.warn("Could not find admin UserRole with identifier " + adminRoleIdentifier + " for " + user.getUserId() + ". Admin UserRoles should be created by syncNemLoginRoles first.");
				continue;
			}

			// Check if user already has this admin role assignment
			final long adminUserRoleId = existingAdminUserRole.getId();
			boolean alreadyAssigned = user.getUserRoleAssignments().stream()
				.anyMatch(ura -> ura.getUserRole().getId() == adminUserRoleId);

			if (!alreadyAssigned) {
				UserUserRoleAssignment userUserRoleAssignment = new UserUserRoleAssignment();
				userUserRoleAssignment.setUserRole(existingAdminUserRole);
				userUserRoleAssignment.setUser(user);
				userUserRoleAssignment.setAssignedByUserId("system");
				userUserRoleAssignment.setAssignedByName("Systembruger");
				userUserRoleAssignment.setAssignedTimestamp(assignedAt);
				userUserRoleAssignment.setPostponedConstraints(new ArrayList<>());

				user.getUserRoleAssignments().add(userUserRoleAssignment);
				userChanged = true;
				log.info("Added admin role " + existingAdminUserRole.getName() + " to user " + user.getUserId());
			}
		}

		// Save user only if changes were made
		if (userChanged) {
			userService.save(user);
		}
	}

	public void syncNemLoginAdminRoles() {
		if (!config.getIntegrations().getNemLogin().isEnabled()) {
			return;
		}

		log.info("Updating NemLog-in admin user roles");

		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NEMLOGIN it-system (either 0 or > 1 was found!)");
			return;
		}

		ItSystem itSystem = itSystems.get(0);
		try {
			SecurityUtil.loginSystemAccount();

			// Create administrator UserRoles directly (no SystemRoles needed)
			List<AdminRole> adminRoles = getAdminRoles();

			for (AdminRole adminRole : adminRoles) {
				UserRole existingAdminUserRole = userRoleService.getByIdentifier(adminRole.identifier);

				if (existingAdminUserRole == null) {
					UserRole newAdminUserRole = new UserRole();
					newAdminUserRole.setItSystem(itSystem);
					newAdminUserRole.setName(adminRole.name);
					newAdminUserRole.setDescription(adminRole.description);
					newAdminUserRole.setIdentifier(adminRole.identifier);
					newAdminUserRole.setReadOnly(true);
					newAdminUserRole.setAllowPostponing(false);
					newAdminUserRole.setUserOnly(true);
					newAdminUserRole.setSystemRoleAssignments(new ArrayList<>());

					userRoleService.save(newAdminUserRole);
					log.debug("Created administrator UserRole: " + newAdminUserRole.getName());
				} else {
					// Update existing admin UserRole if needed
					boolean changes = false;

					if (!Objects.equals(existingAdminUserRole.getDescription(), adminRole.description)) {
						existingAdminUserRole.setDescription(adminRole.description);
						changes = true;
					}

					if (!existingAdminUserRole.isReadOnly()) {
						existingAdminUserRole.setReadOnly(true);
						changes = true;
					}

					if (changes) {
						userRoleService.save(existingAdminUserRole);
						log.debug("Updated administrator UserRole: " + existingAdminUserRole.getName());
					}
				}
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}

		log.info("Done synchronizing admin user roles");
	}

	private record AdminRole(String identifier, String name, String description) {}
	private List<AdminRole> getAdminRoles() {
		return Arrays.asList(
				new AdminRole(config.getIntegrations().getNemLogin().getOrganizationAdministratorIdentifier(),
						config.getIntegrations().getNemLogin().getOrganizationAdministratorName(),
						config.getIntegrations().getNemLogin().getOrganizationAdministratorDescription()),
				new AdminRole(config.getIntegrations().getNemLogin().getIdentityAdministratorIdentifier(),
						config.getIntegrations().getNemLogin().getIdentityAdministratorName(),
						config.getIntegrations().getNemLogin().getIdentityAdministratorDescription()),
				new AdminRole(config.getIntegrations().getNemLogin().getRightsAdministratorIdentifier(),
						config.getIntegrations().getNemLogin().getRightsAdministratorName(),
						config.getIntegrations().getNemLogin().getRightsAdministratorDescription())
		);
	}

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
		List<SystemRole> existingSystemRoles = systemRoleService.getByItSystem(itSystem, sr -> {
			sr.getSupportedConstraintTypes().forEach(sc -> {
				sc.getConstraintType().getEntityId();
			});
		});

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
			List<SystemRole> toSave = new ArrayList<>();
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
						toSave.add(existingSystemRole);
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
					toSave.add(newSystemRole);
				}
			}

			for (SystemRole systemRole : existingSystemRoles) {
				if (roles.stream().noneMatch(sr -> Objects.equals(sr.getUuid(), systemRole.getIdentifier()))) {
					log.info("Deleting " + systemRole.getName() + " / " + systemRole.getId());
					systemRoleService.delete(systemRole);
				}
			}

			if (toSave.size() > 0) {
				systemRoleService.saveAll(toSave);
			}
		}
		finally {
			SecurityUtil.logoutSystemAccount();
		}

		log.info("Done synchronizing NemLog-in roles");
	}

	public void fullRoleSync() {
		log.info("Running full NemLog-In role sync");

		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.NEMLOGIN);
		if (itSystems == null || itSystems.size() != 1) {
			log.error("Could not find a unique NemLog-in it-system (either 0 or > 1 was found!)");
			return;
		}
		ItSystem itSystem = itSystems.get(0);

		Set<String> systemRoleIdentifiers = systemRoleService.findByItSystem(itSystem).stream().map(sr -> sr.getIdentifier()).collect(Collectors.toSet());

		Map<User, Set<CurrentAssignment>> userMap = userService.getAllWithNemLoginAssignments(Collections.singletonList(itSystem));

		log.info("Synchronizing roles for " + userMap.size() + " users");
		int count = 0;
		for (User user : userMap.keySet()) {
			if (++count % 100 == 0) {
				log.info("Completed " + count + " users");
			}

			try {
				syncNemLoginRolesForUser(user, userMap.get(user), itSystem, systemRoleIdentifiers);
			}
			catch (Exception ex) {
				log.error("Failed to process NemLog-in role modifications on user with uuid: " + user.getUuid(), ex);
			}
		}

		log.info("Full NemLog-In role sync completed");
	}

	private void syncNemLoginRolesForUser(User user, Set<CurrentAssignment> assignedUserRoles, ItSystem itSystem, Set<String> nemloginRoleIdentifiers) throws Exception {
		Set<String> systemRoleIdentifiersNoConstraints = new HashSet<>();
		Map<String, Set<String>> systemRoleIdentifiersPnrConstraints = new HashMap<>();
		Map<String, Set<String>> systemRoleIdentifiersSenrConstraints = new HashMap<>();

		for (CurrentAssignment assignment : assignedUserRoles) {
			for (SystemRoleAssignment systemRoleAssignment : assignment.getUserRole().getSystemRoleAssignments()) {
				if (assignment.getPostponedConstraints() == null || assignment.getPostponedConstraints().isEmpty()) {
					boolean anyConstraint = false;

					if (systemRoleAssignment.getConstraintValues() != null) {
						for (SystemRoleAssignmentConstraintValue constraintValue : systemRoleAssignment.getConstraintValues()) {
							if (constraintValue.getConstraintType().getEntityId().equals("https://nemlogin.dk/constraints/pnr/1")) {
								anyConstraint = true;
								if (!systemRoleIdentifiersPnrConstraints.containsKey(systemRoleAssignment.getSystemRole().getIdentifier())) {
									systemRoleIdentifiersPnrConstraints.put(systemRoleAssignment.getSystemRole().getIdentifier(), new HashSet<>());
								}

								systemRoleIdentifiersPnrConstraints.get(systemRoleAssignment.getSystemRole().getIdentifier()).add(constraintValue.getConstraintValue());
							}
							else if (constraintValue.getConstraintType().getEntityId().equals("https://nemlogin.dk/constraints/senr/1")) {
								anyConstraint = true;
								if (!systemRoleIdentifiersSenrConstraints.containsKey(systemRoleAssignment.getSystemRole().getIdentifier())) {
									systemRoleIdentifiersSenrConstraints.put(systemRoleAssignment.getSystemRole().getIdentifier(), new HashSet<>());
								}

								systemRoleIdentifiersSenrConstraints.get(systemRoleAssignment.getSystemRole().getIdentifier()).add(constraintValue.getConstraintValue());
							}
						}
					}

					if (!anyConstraint) {
						systemRoleIdentifiersNoConstraints.add(systemRoleAssignment.getSystemRole().getIdentifier());
					}
				}
				else {
					boolean anyPostponedConstraint = false;

					for (CurrentAssignmentPostponedConstraint postponedConstraint : assignment.getPostponedConstraints()) {
						if (postponedConstraint.getSystemRoleId() == systemRoleAssignment.getSystemRole().getId()) {
							if (postponedConstraint.getConstraintTypeEntityId().equals("https://nemlogin.dk/constraints/pnr/1")) {
								anyPostponedConstraint = true;
								if (!systemRoleIdentifiersPnrConstraints.containsKey(systemRoleAssignment.getSystemRole().getIdentifier())) {
									systemRoleIdentifiersPnrConstraints.put(systemRoleAssignment.getSystemRole().getIdentifier(), new HashSet<>());
								}

								// value is a List<String>, join all values
								for (String value : postponedConstraint.getValue()) {
									systemRoleIdentifiersPnrConstraints.get(systemRoleAssignment.getSystemRole().getIdentifier()).add(value);
								}
							}
							else if (postponedConstraint.getConstraintTypeEntityId().equals("https://nemlogin.dk/constraints/senr/1")) {
								anyPostponedConstraint = true;
								if (!systemRoleIdentifiersSenrConstraints.containsKey(systemRoleAssignment.getSystemRole().getIdentifier())) {
									systemRoleIdentifiersSenrConstraints.put(systemRoleAssignment.getSystemRole().getIdentifier(), new HashSet<>());
								}

								// value is a List<String>, join all values
								for (String value : postponedConstraint.getValue()) {
									systemRoleIdentifiersSenrConstraints.get(systemRoleAssignment.getSystemRole().getIdentifier()).add(value);
								}
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
				if (nemloginRoleIdentifiers.contains(assignedRole.getUuid())) {
					deleteRoleFromUser(user, assignedRole);
				}
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

	public String fetchToken() {
		String token = self.fetchTokenCached();
		if (StringUtils.hasLength(token)) {
			return token;
		}

		// attempt once more
		self.cleanUpToken();

		return self.fetchTokenCached();
	}

	@Cacheable(value = "token", unless = "#result == null")
	public String fetchTokenCached() {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/idmlogin/tls/authenticate";

		TokenResponse response = invokeRestClient("fetchTokenCached", url, HttpMethod.POST, "{}", TokenResponse.class);
		if (response != null) {
			return response.getAccessToken();
		}

		return null;
	}

	@CacheEvict(value = "token", allEntries = true)
	public void cleanUpToken() {
		;
	}

	// the tokens are valid for 1 hour, so we refresh at least once per 30 minutes
	@Scheduled(fixedRate = 30 * 60 * 1000)
	public void cleanUpTask() {
		self.cleanUpToken();
	}

	private List<NemLoginRole> getAllRoles() {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/categorizedRoles";

		NemLoginAllRolesResponse response = invokeRestClient("getAllRoles", url, HttpMethod.GET, null, NemLoginAllRolesResponse.class);
		if (response != null) {
			return response.getRoles();
		}

		return null;
	}

	private List<AssignedRole> getRolesForUser(User user) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/identity/" + user.getNemloginUuid() + "/roles";

		AssignedRole[] response = invokeRestClient("getRolesForUser: " + user.getUserId(), url, HttpMethod.GET, null, AssignedRole[].class);
		if (response != null) {
			return Arrays.asList(response);
		}

		return null;
	}

	private NemLoginUserProfile getUserProfile(User user) {
		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/identity/employee/" + user.getNemloginUuid();

		return invokeRestClient("getUserProfile: " + user.getUserId(), url, HttpMethod.GET, null, NemLoginUserProfile.class);
	}

	record RoleBody(Scope scope, List<String> roleUuids) {}
	private void deleteRoleFromUser(User user, AssignedRole assignedRole) {
		if (config.getIntegrations().getNemLogin().isUserDryRunOnly()) {
			log.info("Removing " + assignedRole.getName() + " from " + user.getUserId());
			return;
		}

		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/identity/" + user.getNemloginUuid() + "/roles";
		RoleBody roleBody = new RoleBody(assignedRole.getScope(), Collections.singletonList(assignedRole.getUuid()));

		String result = invokeRestClient("deleteRoleFromUser: " + user.getUserId(), url, HttpMethod.DELETE, roleBody, String.class);
		if (result != null) {
			log.info("Removed NemLog-In role with uuid " + assignedRole.getUuid() + " from user with nemloginUuid " + user.getNemloginUuid());
		}
	}

	private void addRoleToUser(User user, Scope scope, String roleUuid) {
		if (config.getIntegrations().getNemLogin().isUserDryRunOnly()) {
			log.info("Adding " + roleUuid + " to " + user.getUserId());
			return;
		}

		String url = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/rights/identity/" + user.getNemloginUuid() + "/roles";
		RoleBody roleBody = new RoleBody(scope, Collections.singletonList(roleUuid));

		String response = invokeRestClient("addRoleToUser: " + user.getUserId(), url, HttpMethod.POST, roleBody, String.class);
		if (response != null) {
			log.info("Added NemLog-In role with uuid " + roleUuid + " to user with nemloginUuid " + user.getNemloginUuid());
		}
	}

	private record SeNumberDTORecord(String seNumber) {}
	public List<SENumber> getAllSENR() {
		String SENRResourceUrl = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/organization/senumber";

		SeNumberDTORecord[] response = invokeRestClient("getAllSENR", SENRResourceUrl, HttpMethod.GET, null, SeNumberDTORecord[].class);
		if (response != null) {
			List<SENumber> retList = new ArrayList<>();

			for (SeNumberDTORecord record : response) {
				SENumber se = new SENumber();
				se.setCode(record.seNumber);
				se.setName(record.seNumber);

				retList.add(se);
			}

			return retList;
		}

		return null;
	}

	private record PNumberDTORecord(long pNumber, String cvrNumber, String name, String streetName, String streetBuildingIdentifier) {}
	public List<PNumber> getAllPNR() {
		String PNRResourceUrl = config.getIntegrations().getNemLogin().getBaseUrl() + "/api/administration/organization/productionunits";

		PNumberDTORecord[] response = invokeRestClient("getAllPNR", PNRResourceUrl, HttpMethod.GET, null, PNumberDTORecord[].class);
		if (response != null) {
			List<PNumber> retList = new ArrayList<>();

			for (PNumberDTORecord record : response) {
				PNumber pNumber = new PNumber();
				pNumber.setCode(String.valueOf(record.pNumber));
				pNumber.setName(String.valueOf(record.name));

				retList.add(pNumber);
			}

			return retList;
		}

		return null;
	}

	private record PUnitLookupDTORecord(String number, String street, String postalCode, String city, String name){}
	public String getPnrNameByCode(String pnr) {
		if (!config.getIntegrations().getCvr().isEnabled()) {
			log.info("CVR integration not enabled");
			return null;
		}

		String cvrResourceUrl = config.getIntegrations().getCvr().getBaseUrl() + "/CVR/HentCVRData/1/rest/hentProduktionsenhedMedPNummer?ppNummer=" + pnr;
		String apiKey = config.getIntegrations().getCvr().getApiKey();

		PUnitLookupDTORecord response = invokeRestClientWithCvrHeader("PUnitLookupDTORecord", cvrResourceUrl, HttpMethod.GET, null, PUnitLookupDTORecord.class, apiKey);
		if (response != null) {
			return response.name;
		}

		return null;
	}

	// Safe way to invoke restClient with all the error handling needed
	private <T> T invokeRestClient(String operation, String url, HttpMethod method, Object requestBody, Class<T> clazz) {
		try {
			var spec = restClient.method(method)
				.uri(url)
				.headers(headers -> {
					headers.add("Content-Type", "application/json");
					headers.add("Accept", "application/json");
					if (method != HttpMethod.POST || !operation.equals("fetchTokenCached")) {
						// All requests except the token fetch need the Bearer token
						headers.add("Authorization", "Bearer " + fetchToken());
					}
				});
			Object body = requestBody != null ? requestBody : (method == HttpMethod.POST ? "{}" : null);
			ResponseEntity<String> response = (body != null ? spec.body(body) : spec)
				.retrieve()
				.toEntity(String.class);

			try {
				if (response.getStatusCode().is2xxSuccessful()) {
					if (response.getBody() == null) {
						if (clazz.equals(String.class)) {
							return clazz.cast("");
						}

						log.error(operation + " : body was null on response");
					}
					else {
						return clazz.cast(objectMapper.readValue(response.getBody(), clazz));
					}
				}
				else {
					// This error message is useless, and since we can do nothing about it, log it as a warn
					if (response.getStatusCode().value() == 500 && response.getBody() != null && response.getBody().contains("See log for exception details")) {
						log.warn(operation + " : got HTTP " + response.getStatusCode() + " with body : " + response.getBody());
					}
					else {
						log.error(operation + " : got HTTP " + response.getStatusCode() + " with body : " + response.getBody());
					}
				}
			}
			catch (Exception ex) {
				log.error(operation + " : exception when parsing responseBody: " + response.getBody(), ex);
			}
		}
		catch (Exception ex) {
			log.error(operation + " : exception when invoking restClient", ex);
		}

		return null;
	}

	// Special version for CVR calls with custom header
	private <T> T invokeRestClientWithCvrHeader(String operation, String url, HttpMethod method, Object requestBody, Class<T> clazz, String apiKey) {
		try {
			var spec = restClient.method(method)
				.uri(url)
				.headers(headers -> {
					headers.add("Content-Type", "application/json");
					headers.add("ApiKey", apiKey);
				});
			Object body = requestBody != null ? requestBody : (method == HttpMethod.POST ? "{}" : null);
			ResponseEntity<String> response = (body != null ? spec.body(body) : spec)
				.retrieve()
				.toEntity(String.class);

			try {
				if (response.getStatusCode().is2xxSuccessful()) {
					if (response.getBody() == null) {
						if (clazz.equals(String.class)) {
							return clazz.cast("");
						}

						log.error(operation + " : body was null on response");
					}
					else {
						return clazz.cast(objectMapper.readValue(response.getBody(), clazz));
					}
				}
				else {
					log.error(operation + " : got HTTP " + response.getStatusCode() + " with body : " + response.getBody());
				}
			}
			catch (Exception ex) {
				log.error(operation + " : exception when parsing responseBody: " + response.getBody(), ex);
			}
		}
		catch (Exception ex) {
			log.error(operation + " : exception when invoking restClient", ex);
		}

		return null;
	}
}
