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
import com.microsoft.kiota.RequestInformation;
import com.microsoft.kiota.serialization.AdditionalDataHolder;
import com.microsoft.kiota.serialization.Parsable;
import com.microsoft.kiota.serialization.ParsableFactory;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserWithRole;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

	private ClientSecretCredential clientSecretCredential;
	private GraphServiceClient graphClient;

	@Transactional
	public void backSync() throws ReflectiveOperationException {
		log.info("Starting EntraID backSync");
		SecurityUtil.loginSystemAccount();
		initializeClient();
		List<dk.digitalidentity.rc.dao.model.User> dbUsers = userService.getAll();
		List<Group> allGroups = getRCGroups();
		Map<Long, List<Group>> itSystemIdGroupMap = generateItSystemGroupMap(allGroups);
		for (Map.Entry<Long, List<Group>> entry : itSystemIdGroupMap.entrySet()) {
			List<Group> groups = entry.getValue();
			ItSystem itSystem = itSystemService.getById(entry.getKey());
			if (itSystem == null) {
				log.warn("Failed to find it-system with id: " + entry.getKey() + ". At least one group in EntraID is configured as a system role for the it-system with that id. Skipping");
				continue;
			}

			if (itSystem.getSystemType() != ItSystemType.AD && itSystem.getSystemType() != ItSystemType.SAML && itSystem.getSystemType() != ItSystemType.MANUAL) {
				log.warn("IT-system with id: " + entry.getKey() + " can not be managed via EntraID groups, but at least one group is configured as a system role for the it-system with that id. Skipping");
				continue;
			}

			List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
			List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);

			handleSystemRoles(systemRoles, groups, itSystem);
			handleUserRoles(itSystem, userRoles, groups, dbUsers);
		}

		SecurityUtil.logoutSystemAccount();
		log.info("Finished EntraID backSync");
	}

	@SneakyThrows
    @Transactional
	public void membershipSync() {
		log.info("Starting EntraID membershipSync");
		SecurityUtil.loginSystemAccount();
		initializeClient();

		List<User> allAzureUsers = getAllAzureUsers();
		List<Group> allGroups = getRCGroups();
		Map<Long, List<Group>> itSystemIdGroupMap = generateItSystemGroupMap(allGroups);
		for (Map.Entry<Long, List<Group>> entry : itSystemIdGroupMap.entrySet()) {
			List<Group> groups = entry.getValue();
			ItSystem itSystem = itSystemService.getById(entry.getKey());
			if (itSystem == null) {
				log.warn("Failed to find it-system with id: " + entry.getKey() + ". At least one group in EntraID is configured as a system role for the it-system with that id. Skipping");
				continue;
			}

			List<SystemRole> systemRoles = systemRoleService.getByItSystem(itSystem);
			List<UserRole> userRoles = userRoleService.getByItSystem(itSystem);
			for (Group group : groups) {
				SystemRole systemRole = systemRoles.stream().filter(s -> s.getIdentifier().equals(group.getId())).findAny().orElse(null);
				if (systemRole == null) {
					log.debug("Skipping membersync for EntraID group " + group.getDisplayName() + ". Waiting for backSync to create systemRole");
					continue;
				}

				UserRole userRole = userRoles.stream().filter(ur -> systemRole.getIdentifier().equals(ur.getIdentifier())).findAny().orElse(null);
				if (userRole == null) {
					log.debug("Skipping membersync for EntraID group " + group.getDisplayName() + ". Waiting for backSync to create userRole");
					continue;
				}

				Set<String> usersWithRoleInRC = getUsersWithUserRole(userRole);
				Set<String> memberUsernames = getMembers(group);
				int added = 0;
				int removed = 0;

				// add missing members
				for (String username : usersWithRoleInRC) {
					if (!memberUsernames.contains(username)) {
						User userWithUsername = allAzureUsers.stream().filter(u -> u.getMailNickname().equalsIgnoreCase(username)).findAny().orElse(null);
						if (userWithUsername == null) {
							log.debug("Failed to find user in Azure with username " + username + ". Can not add member to group " + group.getDisplayName());
							continue;
						}

						addMemberToGroup(group.getId(), userWithUsername.getId());
						added++;
					}
				}

				// remove members
				for (String memberUsername : memberUsernames) {
					if (!usersWithRoleInRC.contains(memberUsername)) {
						User userWithUsername = allAzureUsers.stream().filter(u -> u.getMailNickname().equalsIgnoreCase(memberUsername)).findAny().orElse(null);
						if (userWithUsername == null) {
							// should never happen
							log.debug("Failed to find user in Azure with username " + memberUsername + ". Can not remove member from group " + group.getDisplayName());
							continue;
						}

						removeMemberFromGroup(group.getId(), userWithUsername.getId());
						removed++;
					}
				}

				log.info("Added " + added + " new group memberships, and removed " + removed + " group memberships from group: " + group.getDisplayName());
			}
		}

		SecurityUtil.logoutSystemAccount();
		log.info("Finished EntraID membershipSync");
	}

	public void addMemberToGroup(String groupId, String userId) {
		try {
			ReferenceCreate referenceCreate = new ReferenceCreate();
			referenceCreate.setOdataId("https://graph.microsoft.com/v1.0/directoryObjects/" + userId);
			graphClient.groups().byGroupId(groupId).members().ref().post(referenceCreate);
		} catch (Exception e) {
			log.warn("Failed to remove member from group {}: {}", groupId, userId, e);
		}
	}

	public void removeMemberFromGroup(String groupId, String userId) {
		try {
			graphClient.groups().byGroupId(groupId).members().byDirectoryObjectId(userId).ref().delete();
		} catch (Exception e) {
            log.warn("Failed to remove member from group {}: {}", groupId, userId, e);
		}
	}

	private Set<String> getUsersWithUserRole(UserRole userRole) {
		Set<String> users = new HashSet<>();

		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, true);
		for (UserWithRole userWithRole : usersWithRole) {
			if (userWithRole.getUser().isDeleted() || userWithRole.getUser().isDisabled()) {
				continue;
			}

			users.add(StringUtils.lowerCase(userWithRole.getUser().getUserId()));
		}

		return users;
	}

	private void handleSystemRoles(List<SystemRole> systemRoles, List<Group> groups, ItSystem itSystem) {

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

	private void handleUserRoles(ItSystem itSystem, List<UserRole> userRoles, List<Group> groups, List<dk.digitalidentity.rc.dao.model.User> dbUsers) throws ReflectiveOperationException {
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

			if (configuration.getIntegrations().getEntraID().isReImportUsersEnabled()) {
				Group group = groups.stream().filter(g -> g.getId().equals(sRole.getIdentifier())).findAny().orElse(null);
				Set<String> memberUsernames = getMembers(group);

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
			Set<String> memberUsernames = getMembers(group);
			if (!memberUsernames.isEmpty()) {
				updateUserAssignments(userRole, memberUsernames, dbUsers);
			}
		}

		// delete user roles that has no system role assignments
		var toBeDeleted = userRoles.stream().filter(ur -> ur.getSystemRoleAssignments().isEmpty()).collect(Collectors.toList());
		for (var userRole : toBeDeleted) {
			try {
				// TODO: if the userRole is included in a RoleGroup, this will fail - need a "on delete cascade" rule to the reference *sigh* (copied from IT-system API)
				userRoleService.delete(userRole);
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
				userService.addUserRole(user, userRole, null, null, null);
			}
		}

		// remove
		List<UserWithRole> usersWithRole = userService.getUsersWithUserRole(userRole, false);
		for (UserWithRole userWithRole : usersWithRole) {
			String userId = userWithRole.getUser().getUserId();

			if (assignedUsers.stream().noneMatch(u -> u.equalsIgnoreCase(userId))) {
				userService.removeUserRole(userWithRole.getUser(), userRole);
			}
		}
	}

	private Set<String> getMembers(Group group) throws ReflectiveOperationException {
		final List<User> members = iterateResource(UserCollectionResponse::createFromDiscriminatorValue,
				() -> graphClient.groups().byGroupId(Objects.requireNonNull(group.getId())).members().graphUser().get(requestConfiguration -> {
                    assert requestConfiguration.queryParameters != null;
                    requestConfiguration.queryParameters.select = new String[]{"id", "mailNickname"};
				}),
				requestInformation -> {
					log.debug("Preparing to get next members group page");
					return requestInformation;
				});

		return members.stream().map(User::getMailNickname).map(StringUtils::lowerCase).collect(Collectors.toSet());
	}

	private Map<Long, List<Group>> generateItSystemGroupMap(List<Group> groups) {
		HashMap<Long, List<Group>> result = new HashMap<>();
		for (Group group : groups) {
			Long itSystemId = findItSystemIdForGroup(group);
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

	public void initializeClient() {
		if (clientSecretCredential == null) {
			clientSecretCredential = new ClientSecretCredentialBuilder()
					.clientId(configuration.getIntegrations().getEntraID().getClientId())
					.clientSecret(configuration.getIntegrations().getEntraID().getClientSecret())
					.tenantId(configuration.getIntegrations().getEntraID().getTenantId())
					.build();
		}

		if (graphClient == null) {
			final String[] scopes = new String[] {"https://graph.microsoft.com/.default"};
			graphClient = new GraphServiceClient(clientSecretCredential, scopes);
		}
	}

	@SneakyThrows
    public List<Group> getRCGroups()  {
		final List<Group> allGroups = iterateResource(GroupCollectionResponse::createFromDiscriminatorValue,
				() -> graphClient.groups().get(),
				requestInformation -> {
					log.debug("Preparing to get next group page");
					return requestInformation;
				}
		);

        log.info("Found {} total groups in Azure", allGroups.size());
		List<Group> filteredGroups = allGroups.stream().filter(g -> g.getDescription() != null && g.getDescription().contains(configuration.getIntegrations().getEntraID().getRoleCatalogKey())).toList();
        log.info("{} of those groups are RC groups", filteredGroups.size());
		return filteredGroups;
	}

	public List<User> getAllAzureUsers() throws ReflectiveOperationException {
		return iterateResource(UserCollectionResponse::createFromDiscriminatorValue,
				() -> graphClient.users().get( requestConfiguration -> {
                    assert requestConfiguration.queryParameters != null;
                    requestConfiguration.queryParameters.select = new String[] {"id, mailNickname"};
				}),
				requestInfo -> {
					log.debug("Preparing to get next user page");
					// re-add the query parameters to subsequent requests
					requestInfo.addQueryParameter("%24select", new String[] {"id, mailNickname"});
					return requestInfo;
				});
	}

	public Long findItSystemIdForGroup(Group group) {
		String regex = "\\b" + configuration.getIntegrations().getEntraID().getRoleCatalogKey() + "_\\w+";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(group.getDescription());

		try {
			if (matcher.find()) {
				String match = matcher.group();
				String idAsString = match.replace(configuration.getIntegrations().getEntraID().getRoleCatalogKey() + "_", "");
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
			final ParsableFactory<R> collectionPageFactory,
			final Supplier<R> firstRequest,
			final Function<RequestInformation, RequestInformation> requestConfigurator
			) throws ReflectiveOperationException {
		final List<T> resources = new ArrayList<>();
		final PageIterator<T, R> pageIterator = new PageIterator.Builder<T, R>()
				.client(graphClient)
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
