package dk.digitalidentity.rc.service.entraid;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.core.tasks.PageIterator;
import com.microsoft.graph.models.Group;
import com.microsoft.graph.models.GroupCollectionResponse;
import com.microsoft.graph.models.ReferenceCreate;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.AdditionalDataHolder;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.AzureUsernameField;
import dk.digitalidentity.rc.config.model.EntraIDTenant;
import dk.digitalidentity.rc.dao.model.Domain;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleCleanupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EntraIDService {

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private UserService userService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private DomainService domainService;

	@Autowired
	private UserRoleCleanupService userRoleCleanupService;

	// self-reference so the per-user assignment changes run through the transactional proxy
	// (backSync runs in a @Scheduled task with no open-session-in-view)
	@Autowired
	private EntraIDService self;

	private final Map<String, GraphServiceClient> clientCache = new ConcurrentHashMap<>();

	public void backSync() {
		log.info("Starting EntraID backSync");
		SecurityUtil.loginSystemAccount();

		try {
			List<EntraIDTenant> tenants = configuration.getIntegrations().getEntraID().getEffectiveTenants();
			for (EntraIDTenant tenant : tenants) {
				try {
					backSyncForTenant(tenant);
				} catch (Exception e) {
					log.error("Failed to backSync for tenant with domain: {}", tenant.getDomainName() != null ? tenant.getDomainName() : "(no domain filter)", e);
				}
			}
		} finally {
			SecurityUtil.logoutSystemAccount();
		}

		log.info("Finished EntraID backSync");
	}

	private void backSyncForTenant(EntraIDTenant tenant) throws ReflectiveOperationException {
		log.info("BackSync for tenant - domain filter: {}", tenant.getDomainName() != null ? tenant.getDomainName() : "(none)");

		Domain tenantDomain = null;
		if (tenant.getDomainName() != null) {
			tenantDomain = domainService.getByName(tenant.getDomainName());
			if (tenantDomain == null) {
				log.error("Domain '{}' configured for EntraID tenant not found in database. Skipping tenant.", tenant.getDomainName());
				return;
			}
		}

		GraphServiceClient client = getClientForTenant(tenant);

		List<dk.digitalidentity.rc.dao.model.User> dbUsers = userService.getAll(u -> {
			u.getUserRoleAssignments().forEach(ura -> {
				ura.getUserRole().getId();
			});

			// KSPCICS/interceptor looks at this field when modifying assignments
			if (u.getAltAccounts() != null) {
				u.getAltAccounts().forEach(a -> a.getAccountUserId());
			}
		});

		// Filter users by domain if tenant has domain filter
		final Domain domainFilter = tenantDomain;
		if (domainFilter != null) {
			dbUsers = dbUsers.stream()
				.filter(u -> u.getDomain() != null && Objects.equals(u.getDomain().getId(), domainFilter.getId()))
				.toList();
		}

		List<Group> allGroups = getRCGroups(client, tenant);
		Map<Long, List<Group>> itSystemIdGroupMap = generateItSystemGroupMap(allGroups, tenant);
		for (Map.Entry<Long, List<Group>> entry : itSystemIdGroupMap.entrySet()) {
			List<Group> groups = entry.getValue();
			ItSystem itSystem = itSystemService.getById(entry.getKey());
			if (itSystem == null) {
				log.warn("Failed to find it-system with id: {}. At least one group in EntraID is configured as a system role for the it-system with that id. Skipping", entry.getKey());
				continue;
			}

			// Check domain filter for IT-system
			if (tenantDomain != null) {
				if (itSystem.getDomain() == null || itSystem.getDomain().getId() != tenantDomain.getId()) {
					log.debug("IT-system {} does not match tenant domain filter {}. Skipping", itSystem.getName(), tenant.getDomainName());
					continue;
				}
			}

			if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML && itSystem.getSystemType() != ItSystemType.MANUAL) {
				log.warn("IT-system with id: " + entry.getKey() + " can not be managed via EntraID groups, but at least one group is configured as a system role for the it-system with that id. Skipping");
				continue;
			}

			List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem, sr -> {
				sr.getSupportedConstraintTypes().forEach(sc -> sc.getConstraintType().getEntityId());
			});

			List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);

			handleSystemRoles(systemRoles, groups, itSystem, client);
			handleUserRoles(itSystem, userRoles, groups, dbUsers, client, tenant);
		}
	}

	@SneakyThrows
	public void membershipSync() {
		log.info("Starting EntraID membershipSync");
		SecurityUtil.loginSystemAccount();

		try {
			List<EntraIDTenant> tenants = configuration.getIntegrations().getEntraID().getEffectiveTenants();
			for (EntraIDTenant tenant : tenants) {
				try {
					membershipSyncForTenant(tenant);
				} catch (Exception e) {
					log.error("Failed to membershipSync for tenant with domain: {}", tenant.getDomainName() != null ? tenant.getDomainName() : "(no domain filter)", e);
				}
			}
		} finally {
			SecurityUtil.logoutSystemAccount();
		}

		log.info("Finished EntraID membershipSync");
	}

	private void membershipSyncForTenant(EntraIDTenant tenant) throws ReflectiveOperationException {
		log.info("MembershipSync for tenant - domain filter: {}", tenant.getDomainName() != null ? tenant.getDomainName() : "(none)");

		Domain tenantDomain = null;
		if (tenant.getDomainName() != null) {
			tenantDomain = domainService.getByName(tenant.getDomainName());
			if (tenantDomain == null) {
				log.error("Domain '{}' configured for EntraID tenant not found in database. Skipping tenant.", tenant.getDomainName());
				return;
			}
		}

		GraphServiceClient client = getClientForTenant(tenant);
		AzureUsernameField usernameField = tenant.getUsernameField();

		List<User> allAzureUsers = getAllAzureUsers(client, tenant);
		List<Group> allGroups = getRCGroups(client, tenant);
		Map<Long, List<Group>> itSystemIdGroupMap = generateItSystemGroupMap(allGroups, tenant);

		for (Map.Entry<Long, List<Group>> entry : itSystemIdGroupMap.entrySet()) {
			List<Group> groups = entry.getValue();
			ItSystem itSystem = itSystemService.getById(entry.getKey());
			if (itSystem == null) {
				log.warn("Failed to find it-system with id: " + entry.getKey() + ". At least one group in EntraID is configured as a system role for the it-system with that id. Skipping");
				continue;
			}

			// Check domain filter for IT-system
			if (tenantDomain != null) {
				if (itSystem.getDomain() == null || !Objects.equals(itSystem.getDomain().getId(), tenantDomain.getId())) {
					log.debug("IT-system {} does not match tenant domain filter {}. Skipping", itSystem.getName(), tenant.getDomainName());
					continue;
				}
			}

			List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);

			for (Group group : groups) {
				SystemRole systemRole = systemRoles.stream().filter(s -> s.getIdentifier().equals(group.getId())).findAny().orElse(null);
				if (systemRole == null) {
					log.debug("Skipping membersync for EntraID group " + group.getDisplayName() + ". Waiting for backSync to create systemRole");
					continue;
				}

				// Group membership reflects every user who has this systemRole through any userRole (jobfunktionsrolle),
				// regardless of the userRole's identifier or whether multiple systemRoles are bundled into one userRole.
				Set<UserRole> userRolesWithSystemRole = userRoleService.findAllBySystemRole(systemRole);

				// Preserve the race guard only while backSync is enabled: backSync will create the missing 1:1 userRole,
				// so skip until it exists to avoid emptying the group in the window between systemRole and userRole creation.
				// When backSync is disabled, a systemRole with no userRole means nobody is entitled, so the group is emptied.
				if (userRolesWithSystemRole.isEmpty() && configuration.getIntegrations().getEntraID().isBackSyncEnabled()) {
					log.debug("Skipping membersync for EntraID group " + group.getDisplayName() + ". Waiting for backSync to create userRole");
					continue;
				}

				if (userRolesWithSystemRole.isEmpty()) {
					// backSync is disabled (we did not skip above), so no userRole means nobody is entitled and the group will be emptied.
					// Logged at warn so operators can distinguish an intentional empty group from a misconfiguration in production logs.
					log.warn("No userRole covers systemRole '{}' (it-system '{}') and backSync is disabled. Emptying EntraID group {}",
							systemRole.getIdentifier(), itSystem.getName(), group.getDisplayName());
				}

				Set<String> usersWithRoleInRC = new HashSet<>();
				for (UserRole userRole : userRolesWithSystemRole) {
					usersWithRoleInRC.addAll(getUsersWithUserRole(userRole, tenant));
				}
				Set<String> memberUsernames = getMembers(group, client, tenant);
				int added = 0;
				int removed = 0;

				log.debug("Members in RC {} members in EntraID group {}", Strings.join(usersWithRoleInRC, ','), Strings.join(memberUsernames, ','));

				// add missing members
				for (String username : usersWithRoleInRC) {
					if (!memberUsernames.contains(username)) {
						User userWithUsername = allAzureUsers.stream().filter(u -> UsernameUtil.matchesUsername(u, username, usernameField)).findAny().orElse(null);
						if (userWithUsername == null) {
							log.debug("Failed to find user in Azure with username " + username + ". Can not add member to group " + group.getDisplayName());
							continue;
						}

						addMemberToGroup(client, group.getId(), userWithUsername.getId());
						added++;
					}
				}

				// remove members
				for (String memberUsername : memberUsernames) {
					if (!usersWithRoleInRC.contains(memberUsername)) {
						User userWithUsername = allAzureUsers.stream().filter(u -> UsernameUtil.matchesUsername(u, memberUsername, usernameField)).findAny().orElse(null);
						if (userWithUsername == null) {
							// should never happen
							log.debug("Failed to find user in Azure with username " + memberUsername + ". Can not remove member from group " + group.getDisplayName());
							continue;
						}

						removeMemberFromGroup(client, group.getId(), userWithUsername.getId());
						removed++;
					}
				}

				log.info("Added " + added + " new group memberships, and removed " + removed + " group memberships from group: " + group.getDisplayName());
			}
		}
	}

	public void addMemberToGroup(GraphServiceClient client, String groupId, String userId) {
		try {
			log.debug("Adding member to group {} to user {}", groupId, userId);
			ReferenceCreate referenceCreate = new ReferenceCreate();
			referenceCreate.setOdataId("https://graph.microsoft.com/v1.0/directoryObjects/" + userId);
			client.groups().byGroupId(groupId).members().ref().post(referenceCreate);
		} catch (Exception e) {
            if (e.getMessage().contains("Insufficient privileges")) {
                log.error("Failed to add member to group {}: {}", groupId, userId, e);
            }
            else {
                log.warn("Failed to add member to group {}: {}", groupId, userId, e);
            }
		}
	}

	public void removeMemberFromGroup(GraphServiceClient client, String groupId, String userId) {
		try {
			log.debug("Removing member from group {} to user {}", groupId, userId);
			client.groups().byGroupId(groupId).members().byDirectoryObjectId(userId).ref().delete();
		} catch (Exception e) {
            if (e.getMessage().contains("Insufficient privileges")) {
                log.error("Failed to remove member from group {}: {}", groupId, userId, e);
            }
            else {
                log.warn("Failed to remove member from group {}: {}", groupId, userId, e);
            }
		}
	}

	private Set<String> getUsersWithUserRole(UserRole userRole, EntraIDTenant tenant) {
		Set<String> users = new HashSet<>();

		Set<CurrentAssignment> assignments = assignmentService.getActiveByUserRole(userRole, a -> {
			a.getUser().getDomain().getName();
		});

		for (CurrentAssignment assignment : assignments) {
			if (assignment.getUser().isDeleted() || assignment.getUser().isDisabled()) {
				continue;
			}

			// Apply domain filter if tenant has one
			if (tenant.getDomainName() != null) {
				Domain userDomain = assignment.getUser().getDomain();
				if (userDomain == null || !tenant.getDomainName().equals(userDomain.getName())) {
					continue;
				}
			}

			users.add(StringUtils.lowerCase(assignment.getUser().getUserId()));
		}

		return users;
	}

	private void handleSystemRoles(List<SystemRole> systemRoles, List<Group> groups, ItSystem itSystem, GraphServiceClient client) {
		// check for updates and deletes
		for (SystemRole systemRole : systemRoles) {
			Group match = groups.stream().filter(g -> g.getId().equals(systemRole.getIdentifier())).findAny().orElse(null);

			if (match != null) {
				boolean changes = false;

				if (!Objects.equals(match.getDisplayName(), systemRole.getName())) {
					log.info("Updating name on systemRole from " + systemRole.getName() + " to " + match.getDisplayName());
					systemRole.setName(match.getDisplayName());
					changes = true;
				}

				if (!Objects.equals(match.getDescription(), systemRole.getDescription())) {
					log.info("Updating description on systemRole " + systemRole.getName());
					systemRole.setDescription(match.getDescription());
					changes = true;
				}

				if (changes) {
					systemRoleService.save(systemRole);
				}
			} else if (groupStillExistsInAzure(client, systemRole.getIdentifier())) {
				// The group was absent from getRCGroups' result, but a direct lookup proves it still
				// exists - it was just missed by the eventually-consistent $search. Never delete here,
				// or a stale/missed search result would silently remove a live systemRole and userRole.
				log.info("SystemRole {} was not in the search result, but its group {} still exists in Azure - keeping it", systemRole.getName(), systemRole.getIdentifier());
			} else {
				log.info("Removing " + systemRole.getName() + " from " + itSystem.getName());
				systemRoleService.delete(systemRole);
			}
		}

		// check for creates
		systemRoles = systemRoleService.getByItSystem(itSystem);
		for (Group group : groups) {
			SystemRole match = systemRoles.stream().filter(s -> s.getIdentifier().equals(group.getId())).findFirst().orElse(null);
			if (match == null) {
				log.info("Adding " + group.getDisplayName() + " to " + itSystem.getName());
				SystemRole systemRole = new SystemRole();
				systemRole.setName(group.getDisplayName());
				systemRole.setIdentifier(group.getId());
				systemRole.setDescription(group.getDescription());
				systemRole.setItSystem(itSystem);
				systemRoleService.save(systemRole);
			}
		}
	}

	private void handleUserRoles(ItSystem itSystem, List<UserRole> userRoles, List<Group> groups, List<dk.digitalidentity.rc.dao.model.User> dbUsers, GraphServiceClient client, EntraIDTenant tenant) throws ReflectiveOperationException {
		// update user role to match name of system role
		List<SystemRole> finalSystemRoles = systemRoleService.findByItSystem(itSystem);
		List<UserRole> toBeUpdated = userRoles.stream()
				.filter(ur -> finalSystemRoles.stream().anyMatch(sr -> Objects.equals(sr.getIdentifier(), ur.getIdentifier())))
				.collect(Collectors.toList());

		for (UserRole userRole : toBeUpdated) {
			String identifier = userRole.getIdentifier();
			Optional<SystemRole> systemRole = finalSystemRoles.stream()
					.filter(sr -> Objects.equals(sr.getIdentifier(), identifier))
					.findFirst();

			if (!systemRole.isPresent()) {
				continue;
			}

			SystemRole sRole = systemRole.get();

			if (!Objects.equals(userRole.getName(), sRole.getName()) ||
					!Objects.equals(userRole.getDescription(), sRole.getDescription())) {
				userRole.setName(sRole.getName());
				userRole.setDescription(sRole.getDescription());
				userRole = userRoleService.save(userRole);
			}

			if (tenant.isReImportUsersEnabled()) {
				Group group = groups.stream().filter(g -> g.getId().equals(sRole.getIdentifier())).findAny().orElse(null);
				Set<String> memberUsernames = getMembers(group, client, tenant);

				updateUserAssignments(userRole, memberUsernames, dbUsers);
			}
		}

		// create 1:1 user role
		List<SystemRole> toBeCreated = finalSystemRoles.stream().filter(sr -> userRoles.stream().noneMatch(ur -> Objects.equals(ur.getIdentifier(), sr.getIdentifier()))).collect(Collectors.toList());
		for (SystemRole systemRole : toBeCreated) {
			UserRole userRole = new UserRole();
			userRole.setItSystem(itSystem);
			userRole.setIdentifier(systemRole.getIdentifier());
			userRole.setName(systemRole.getName());
			userRole.setDescription(systemRole.getDescription());
			userRole.setApproverPermission(Collections.singletonList(ApprovableBy.INHERIT));
			userRole.setRequesterPermission(Collections.singletonList(RequestableBy.INHERIT));

			SystemRoleAssignment systemRoleAssignment = new SystemRoleAssignment();
			systemRoleAssignment.setAssignedByName("Systembruger");
			systemRoleAssignment.setAssignedByUserId("Systembruger");
			systemRoleAssignment.setAssignedTimestamp(new Date());
			systemRoleAssignment.setSystemRole(systemRole);
			systemRoleAssignment.setUserRole(userRole);
			systemRoleAssignment.setConstraintValues(new ArrayList<>());

			userRole.setSystemRoleAssignments(Arrays.asList(systemRoleAssignment));

			userRole = userRoleService.save(userRole);

			// always relevant for create scenarios
			Group group = groups.stream().filter(g -> g.getId().equals(systemRole.getIdentifier())).findAny().orElse(null);
			Set<String> memberUsernames = getMembers(group, client, tenant);
			if (!memberUsernames.isEmpty()) {
				updateUserAssignments(userRole, memberUsernames, dbUsers);
			}
		}

		// delete user roles that has no system role assignments
		var toBeDeleted = userRoles.stream().filter(ur -> ur.getSystemRoleAssignments().isEmpty()).collect(Collectors.toList());
		for (var userRole : toBeDeleted) {
			try {
				userRoleCleanupService.deleteWithCleanup(userRole);
			}
			catch (Exception ex) {
				log.error("Failed to delete userRole: " + userRole.getId(), ex);
			}
		}
	}

	private void updateUserAssignments(UserRole userRole, Set<String> assignedUsers, List<dk.digitalidentity.rc.dao.model.User> users) {
		if (assignedUsers == null || assignedUsers.isEmpty()) {
			assignedUsers = new HashSet<>();
		}

		// assign
		for (String userId : assignedUsers) {
			dk.digitalidentity.rc.dao.model.User user = users.stream().filter(u -> u.getUserId().equalsIgnoreCase(userId)).findAny().orElse(null);
			if (user == null) {
				log.warn("EntraIDSync: Unable to find user with userID: " + userId + " while updating UserRoles.");
				continue;
			}

			if (user.getUserRoleAssignments().stream().noneMatch(ura -> ura.getUserRole().getId() == userRole.getId())) {
				// Apply on a freshly loaded, managed user in a transaction. backSync runs in a @Scheduled
				// task (no open-session-in-view), so the users in 'users' are detached; mutating +
				// re-merging them across role iterations would re-insert earlier assignments (their
				// generated id stays 0), and the removal below would hit a LazyInitializationException.
				self.assignUserRoleTransactional(user.getUuid(), userRole.getId());
			}
		}

		// remove - only direct and roleGroup assignments (not OrgUnit/Title assignments)
		for (CurrentAssignment assignment : assignmentService.getActiveByUserRoleDirectlyAssignedOrFromRoleGroup(userRole)) {
			String userId = assignment.getUser().getUserId();

			if (assignedUsers.stream().noneMatch(u -> u.equalsIgnoreCase(userId))) {
				self.removeUserRoleTransactional(assignment.getUser().getUuid(), userRole.getId());
			}
		}
	}

	/**
	 * Assigns the userRole to the user in a transaction, operating on a freshly loaded managed entity so
	 * the new assignment is flushed to the database (and picked up by the queued recalculation). Called
	 * from the @Scheduled backSync, which has no surrounding transaction, so each call commits on its own.
	 */
	@Transactional
	public void assignUserRoleTransactional(String userUuid, long userRoleId) {
		dk.digitalidentity.rc.dao.model.User user = userService.getByUuid(userUuid);
		UserRole userRole = userRoleService.getById(userRoleId);
		if (user == null || userRole == null) {
			return;
		}

		// authoritative check on the freshly loaded, managed user (the call site checks a detached
		// snapshot); also guards against the role being assigned concurrently between the two
		if (user.getUserRoleAssignments().stream().noneMatch(ura -> ura.getUserRole().getId() == userRole.getId())) {
			userService.addUserRole(user, userRole, null, null, null);
			userService.save(user);
		}
	}

	/**
	 * Removes the userRole from the user in a transaction, operating on a freshly loaded managed entity so
	 * the removal is flushed to the database (and picked up by the queued recalculation). Called from the
	 * @Scheduled backSync, which has no surrounding transaction, so each call commits on its own.
	 */
	@Transactional
	public void removeUserRoleTransactional(String userUuid, long userRoleId) {
		dk.digitalidentity.rc.dao.model.User user = userService.getByUuid(userUuid);
		UserRole userRole = userRoleService.getById(userRoleId);
		if (user == null || userRole == null) {
			return;
		}

		userService.removeUserRole(user, userRole);
		userService.save(user);
	}

	private Set<String> getMembers(Group group, GraphServiceClient client, EntraIDTenant tenant) throws ReflectiveOperationException {
		AzureUsernameField field = tenant.getUsernameField();
		String graphField = UsernameUtil.getGraphFieldName(field);

		final List<User> members = iterateResource(client, UserCollectionResponse::createFromDiscriminatorValue,
			() -> client.groups().byGroupId(Objects.requireNonNull(group.getId())).members().graphUser().get(requestConfiguration -> {
				assert requestConfiguration.queryParameters != null;
				requestConfiguration.queryParameters.select = new String[]{"id", graphField};
			}),
			requestInformation -> {
				log.debug("Preparing to get next members group page");
				return requestInformation;
			});

		return members.stream().map(user -> UsernameUtil.getUsernameFromUser(user, field)).map(StringUtils::lowerCase).collect(Collectors.toSet());
	}

	private Map<Long, List<Group>> generateItSystemGroupMap(List<Group> groups, EntraIDTenant tenant) {
		HashMap<Long, List<Group>> result = new HashMap<>();
		for (Group group : groups) {
			Long itSystemId = findItSystemIdForGroup(group, tenant);
			if (itSystemId == null) {
				continue;
			}

			if (!result.containsKey(itSystemId)) {
				result.put(itSystemId, new ArrayList<>());
			}

			result.get(itSystemId).add(group);
		}
		return result;
	}

	private GraphServiceClient getClientForTenant(EntraIDTenant tenant) {
		return clientCache.computeIfAbsent(tenant.getClientId(), _ -> {
			ClientSecretCredential credential = new ClientSecretCredentialBuilder()
				.clientId(tenant.getClientId())
				.clientSecret(tenant.getClientSecret())
				.tenantId(tenant.getTenantId())
				.build();

			return new GraphServiceClient(credential, new String[]{"https://graph.microsoft.com/.default"});
		});
	}

	// only these fields are read off a Group anywhere in this service, so $select keeps the
	// deserialized payload tiny - the default groups list returns ~60 properties per group, which
	// adds up fast when a tenant has thousands of groups and we fetch them every membershipSync.
	private static final String[] GROUP_SELECT = new String[]{"id", "displayName", "description"};

	@SneakyThrows
	public List<Group> getRCGroups(GraphServiceClient client, EntraIDTenant tenant) {
		// $search narrows the tenant's groups down to candidate RC groups server-side, so we no longer
		// pull every group (e.g. 7000+) into memory just to discard almost all of them.
		final String safeKey = tenant.getRoleCatalogKey().replace("\\", "").replace("\"", "").replace(":", "");
		final String searchTerm = "\"description:" + safeKey + "\"";

		final List<Group> candidateGroups = iterateResource(client, GroupCollectionResponse::createFromDiscriminatorValue,
			() -> client.groups().get(requestConfiguration -> {
				requestConfiguration.queryParameters.select = GROUP_SELECT;
				requestConfiguration.queryParameters.search = searchTerm;
				requestConfiguration.queryParameters.count = true;
				requestConfiguration.headers.add("ConsistencyLevel", "eventual");
			}),
			requestInformation -> {
				log.debug("Preparing to get next group page");
				// Query params ($select/$search/$count) already travel in the @odata.nextLink, so they
				// must not be re-added here. Only the ConsistencyLevel header must be re-applied to every
				// subsequent page request - headers do not survive the nextLink - or Graph rejects the
				// advanced query.
				requestInformation.headers.add("ConsistencyLevel", "eventual");
				return requestInformation;
			}
		);

		log.info("Found {} candidate groups in Azure (server-side filtered on description)", candidateGroups.size());
		List<Group> filteredGroups = candidateGroups.stream().filter(g -> g.getDescription() != null && g.getDescription().contains(tenant.getRoleCatalogKey())).toList();
		log.info("{} of those groups are RC groups", filteredGroups.size());
		return filteredGroups;
	}

	/**
	 * Confirms a single group still exists in Azure via a direct (strongly consistent) lookup. Used as a
	 * safety net before deleting a systemRole whose group was missing from the eventually-consistent
	 * $search result in {@link #getRCGroups}. On any non-404 error we assume the group still exists, so a
	 * transient Graph failure can never cause us to delete a live role.
	 */
	private boolean groupStillExistsInAzure(GraphServiceClient client, String groupId) {
		if (groupId == null) {
			// A null identifier means corrupt/incomplete data, not a confirmed-gone group. Deleting the
			// role on the back of that would break the fail-open contract, so keep the role and warn.
			log.warn("SystemRole has null group identifier; keeping the role and skipping the deletion guard");
			return true;
		}
		try {
			Group group = client.groups().byGroupId(groupId).get(requestConfiguration -> {
				requestConfiguration.queryParameters.select = GROUP_SELECT;
			});
			return group != null && group.getId() != null;
		} catch (Exception e) {
			// Only a confirmed 404 means the group is really gone. Any other failure - transport error,
			// throttling, token refresh, an error wrapped in a non-ApiException RuntimeException - must
			// NOT abort the surrounding backSync loop, and must leave the role in place. Returning true
			// keeps the role; we never delete on the back of a failed verification.
			if (e instanceof ApiException apiException && apiException.getResponseStatusCode() == 404) {
				return false; // confirmed gone
			}
			log.warn("Could not verify whether group {} still exists before deleting its systemRole; keeping the role to be safe", groupId, e);
			return true;
		}
	}

	public List<User> getAllAzureUsers(GraphServiceClient client, EntraIDTenant tenant) throws ReflectiveOperationException {
		AzureUsernameField field = tenant.getUsernameField();
		String graphField = UsernameUtil.getGraphFieldName(field);

		return iterateResource(client, UserCollectionResponse::createFromDiscriminatorValue,
			() -> client.users().get(requestConfiguration -> {
				assert requestConfiguration.queryParameters != null;
				requestConfiguration.queryParameters.select = new String[]{"id", graphField};
			}),
			requestInfo -> {
				log.debug("Preparing to get next user page");
				// re-add the query parameters to subsequent requests
				requestInfo.addQueryParameter("%24select", new String[]{"id", graphField});
				return requestInfo;
			});
	}

	public Long findItSystemIdForGroup(Group group, EntraIDTenant tenant) {
		String regex = "\\b" + tenant.getRoleCatalogKey() + "_\\w+";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(group.getDescription());

		try {
			if (matcher.find()) {
				String match = matcher.group();
				String idAsString = match.replace(tenant.getRoleCatalogKey() + "_", "");
				return Long.parseLong(idAsString);
			}
		} catch (NumberFormatException e) {
			// ignore
		}

		log.warn("Failed to find it-system id for EntraID group with name: " + group.getDisplayName() + ". Will not sync group to RC");
		return null;
	}

	/**
	 * Paginate through a resource, and return a list containing all the results
	 */
	private <T extends Parsable, R extends Parsable & AdditionalDataHolder> List<T> iterateResource(
		final GraphServiceClient client,
		final ParsableFactory<R> collectionPageFactory,
		final Supplier<R> firstRequest,
		final Function<RequestInformation, RequestInformation> requestConfigurator
	) throws ReflectiveOperationException {
		final List<T> resources = new ArrayList<>();
		final PageIterator<T, R> pageIterator = new PageIterator.Builder<T, R>()
				.client(client)
				// response from the first request
				.collectionPage(Objects.requireNonNull(firstRequest.get()))
				// factory to create a new collection response
				.collectionPageFactory(collectionPageFactory)
				// used to configure subsequent requests
				.requestConfigurator(requestConfigurator::apply)
				// callback executed for each item in the collection
				.processPageItemCallback(entity -> {
					resources.add(entity);
					return true;
				}).build();
		pageIterator.iterate();
		return resources;
	}
}
