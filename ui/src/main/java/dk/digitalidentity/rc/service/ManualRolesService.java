package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.ManualNotificationPendingUserDao;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.util.StreamExtensions;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ManualRolesService {
	@Autowired
	private ManualAssignmentNotificationMapDao manualAssignmentNotificationMapDao;

	@Autowired
	private UserService userService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private EmailTemplateRenderer emailTemplateRenderer;

	@Autowired
	private UserRoleService userRoleService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	@Autowired
	private SettingsService settingsService;

	@Autowired
	private AssignmentService assignmentService;

	@Autowired
	private ManualAssignmentNotificationMapService manualAssignmentNotificationMapService;

	@Autowired
	private ManualNotificationPendingUserDao manualNotificationPendingUserDao;

	// it-system types covered by the assignment-change notification scan
	private static final List<ItSystemType> NOTIFICATION_IT_SYSTEM_TYPES =
			Arrays.asList(ItSystemType.MANUAL, ItSystemType.AD, ItSystemType.SAML, ItSystemType.KOMBIT, ItSystemType.KSPCICS);

	public record UserInformationDTO(User user, String ouName) {}
	public record UserRoleInformationDTO(UserRole userRole, String responsible) {}

	@Transactional
	public void notifyServicedesk() {
		LocalDateTime firstRun = settingsService.getFirstManualITSystemRun();

		Map<String, User> userMap = userService.getAll().stream().collect(Collectors.toMap(u -> u.getDomain().getId() + "!" + u.getUserId(), Function.identity()));

		List<ItSystem> itSystems = itSystemService.getBySystemTypeIn(NOTIFICATION_IT_SYSTEM_TYPES);
		for (ItSystem itSystem : itSystems) {
			processItSystem(itSystem, userMap, firstRun);
		}

		if (firstRun == null) {
			settingsService.setFirstManualITSystemRun(LocalDateTime.now());
		}
	}

	/**
	 * Records a user whose assignments changed, to be picked up by the next {@link #processPendingUsers()}
	 * flush. The unique constraint on userUuid deduplicates users that change repeatedly between flushes.
	 *
	 * The find-then-insert is safe because the user_assignments_changed queue is drained by a single-threaded
	 * poller and deduplicates messages by user-uuid, so markUserPending is never invoked concurrently for the
	 * same user. The unique constraint remains as a backstop.
	 */
	@Transactional
	public void markUserPending(User user) {
		if (manualNotificationPendingUserDao.findByUserUuid(user.getUuid()).isPresent()) {
			return;
		}

		ManualNotificationPendingUser pending = new ManualNotificationPendingUser();
		pending.setUserUuid(user.getUuid());
		pending.setCreatedAt(LocalDateTime.now());
		manualNotificationPendingUserDao.save(pending);
	}

	/**
	 * Event-driven companion to {@link #notifyServicedesk()}: processes only the users that have been
	 * marked pending by {@code ManualRolesUserAssignmentsChangedListener} since the last flush, batched
	 * together so several changed users on the same it-system still produce a single digest mail.
	 *
	 * The full sweep ({@link #notifyServicedesk()}) keeps running (less frequently) as a safety-net that
	 * bootstraps the baseline, cleans up rows for deleted users and recovers from any lost events.
	 */
	@Transactional
	public void processPendingUsers() {
		List<ManualNotificationPendingUser> pending = manualNotificationPendingUserDao.findAll();
		if (pending.isEmpty()) {
			return;
		}

		LocalDateTime firstRun = settingsService.getFirstManualITSystemRun();

		// deleted users are skipped here (getOptionalByUuid filters deleted=false) — the nightly sweep
		// handles their cleanup; their pending row is deleted below regardless to avoid build-up
		List<User> users = pending.stream()
			.map(p -> userService.getOptionalByUuid(p.getUserUuid()))
			.flatMap(Optional::stream)
			.toList();

		if (!users.isEmpty()) {
			processUsers(users, firstRun);
		}

		// delete by the rows we read; rows inserted during the flush survive for the next run
		manualNotificationPendingUserDao.deleteAll(pending);

		if (firstRun == null) {
			settingsService.setFirstManualITSystemRun(LocalDateTime.now());
		}
	}

	private void processUsers(List<User> users, LocalDateTime firstRun) {
		Map<String, User> userMap = users.stream()
			.collect(Collectors.toMap(u -> u.getDomain().getId() + "!" + u.getUserId(), Function.identity(), (a, b) -> a));

		List<ItSystem> manualItSystems = itSystemService.getBySystemTypeIn(NOTIFICATION_IT_SYSTEM_TYPES);

		// gather each user's active assignments in the manual systems + their notification-map rows once
		Set<CurrentAssignment> allCurrentAssignments = new HashSet<>();
		List<ManualAssignmentNotificationMap> allLastSyncMaps = new ArrayList<>();
		for (User user : users) {
			allCurrentAssignments.addAll(assignmentService.getByUserAndItSystems(user, manualItSystems));
			allLastSyncMaps.addAll(manualAssignmentNotificationMapService.getForUser(user.getDomain().getId(), user.getUserId()));
		}

		// determine which it-systems the batch touches: either a current assignment or an existing map row
		Map<Long, ItSystem> relevantItSystems = new HashMap<>();
		for (CurrentAssignment assignment : allCurrentAssignments) {
			if (assignment.getItSystem() != null) {
				relevantItSystems.put(assignment.getItSystem().getId(), assignment.getItSystem());
			}
		}
		// map rows only carry userRoleId, so resolve their it-systems via the userRoles (catches removals)
		Set<Long> mapRoleIds = allLastSyncMaps.stream().map(ManualAssignmentNotificationMap::getUserRoleId).collect(Collectors.toSet());
		for (UserRole role : userRoleService.findAllByIdIn(mapRoleIds)) {
			if (role.getItSystem() != null) {
				relevantItSystems.put(role.getItSystem().getId(), role.getItSystem());
			}
		}

		Map<Long, List<CurrentAssignment>> currentByItSystem = allCurrentAssignments.stream()
			.filter(a -> a.getItSystem() != null)
			.collect(Collectors.groupingBy(a -> a.getItSystem().getId()));

		for (ItSystem itSystem : relevantItSystems.values()) {
			Map<Long, UserRole> userRoleMap = userRoleService.getByItSystem(itSystem).stream().collect(Collectors.toMap(UserRole::getId, Function.identity()));

			if (!hasContactEmail(itSystem, userRoleMap)) {
				log.info("Skipping it-system without contact email(s) : " + itSystem.getName() + " / " + itSystem.getId());
				continue;
			}

			Set<CurrentAssignment> currentAssignments = new HashSet<>(currentByItSystem.getOrDefault(itSystem.getId(), Collections.emptyList()));
			List<ManualAssignmentNotificationMap> lastSyncMaps = allLastSyncMaps.stream()
				.filter(m -> userRoleMap.containsKey(m.getUserRoleId()))
				.toList();

			processDetectedChanges(itSystem, userRoleMap, currentAssignments, lastSyncMaps, userMap, firstRun);
		}
	}

	private void processItSystem(ItSystem itSystem, Map<String, User> userMap, LocalDateTime firstRun) {
		log.info("Detecting role changes on " + itSystem.getName() + " / " + itSystem.getId());

		Map<Long, UserRole> userRoleMap = userRoleService.getByItSystem(itSystem).stream().collect(Collectors.toMap(UserRole::getId, Function.identity()));

		// neither the it-system or any of the userRoles has an Email, so skip
		if (!hasContactEmail(itSystem, userRoleMap)) {
			log.info("Skipping it-system without contact email(s) : " + itSystem.getName() + " / " + itSystem.getId());
			return;
		}

		Set<CurrentAssignment> currentAssignments = assignmentService.getActiveAssignmentsByItSystem(itSystem);
		List<ManualAssignmentNotificationMap> manualAssignmentNotificationMaps = manualAssignmentNotificationMapService.getForRoles(userRoleMap.keySet());

		processDetectedChanges(itSystem, userRoleMap, currentAssignments, manualAssignmentNotificationMaps, userMap, firstRun);
	}

	private boolean hasContactEmail(ItSystem itSystem, Map<Long, UserRole> userRoleMap) {
		if (StringUtils.hasText(itSystem.getEmail()) || StringUtils.hasText(itSystem.getAdvisEmail())) {
			return true;
		}
		for (UserRole userRole : userRoleMap.values()) {
			if (StringUtils.hasText(userRole.getContactEmail()) || StringUtils.hasText(userRole.getAdvisEmail())) {
				return true;
			}
		}
		return false;
	}

	private void processDetectedChanges(ItSystem itSystem, Map<Long, UserRole> userRoleMap, Set<CurrentAssignment> currentAssignments, List<ManualAssignmentNotificationMap> manualAssignmentNotificationMaps, Map<String, User> userMap, LocalDateTime firstRun) {
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap = new HashMap<>();
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap = new HashMap<>();
		Map<UserRole, List<User>> toAddUserRoleMap = new HashMap<>();
		Map<UserRole, List<User>> toRemoveUserRoleMap = new HashMap<>();

		Map<String, List<CurrentAssignment>> currentAssignmentsMap = currentAssignments.stream().collect(Collectors.groupingBy(c -> c.getUser().getDomain().getId() + "!" + c.getUser().getUserId()));
		Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap = manualAssignmentNotificationMaps.stream().collect(Collectors.groupingBy(m -> m.getDomainId() + "!" + m.getUserUserId()));

		detectAddedRoles(userMap, currentAssignmentsMap, lastSyncAssignmentsMap, userRoleMap, toAddMap, toAddUserRoleMap);
		detectRemovedRoles(userMap, lastSyncAssignmentsMap, currentAssignmentsMap, userRoleMap, toRemoveMap, toRemoveUserRoleMap);

		String[] itEmailAddresses = splitAddresses(itSystem.getEmail());
		String[] itAdvisAddresses = splitAddresses(itSystem.getAdvisEmail());

		// Only send emails if first run was more than 3 hours ago
		boolean shouldSendEmails = firstRun != null && LocalDateTime.now().isAfter(firstRun.plusHours(3));

		if (shouldSendEmails && (!toAddMap.isEmpty() || !toRemoveMap.isEmpty())) {
			sendChangeNotifications(itSystem, toAddMap, toRemoveMap, toAddUserRoleMap, toRemoveUserRoleMap, itEmailAddresses, itAdvisAddresses);
		}
	}

	private static String[] splitAddresses(String emails) {
		return StringUtils.hasLength(emails) ? emails.split(";") : null;
	}

	private void sendChangeNotifications(ItSystem itSystem, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<User>> toAddUserRoleMap, Map<UserRole, List<User>> toRemoveUserRoleMap, String[] itEmailAddresses, String[] itAdvisAddresses) {
		if (toAddMap.isEmpty() && toRemoveMap.isEmpty() && toAddUserRoleMap.isEmpty() && toRemoveUserRoleMap.isEmpty()) {
			return;
		}

		// Manager-action-required mails are independent of itSystem/role contact emails — fire them up front
		triggerManagerActionNotifications(toAddMap);

		EmailTemplate systemPerformerTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_SYSTEM_CONTACT_PERFORMER);
		EmailTemplate systemAdvisTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_SYSTEM_CONTACT_ADVIS);
		EmailTemplate rolePerformerTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ROLE_CONTACT_PERFORMER);
		EmailTemplate roleAdvisTemplate = emailTemplateService.findByTemplateType(EmailTemplateType.MANUAL_ROLE_CONTACT_ADVIS);

		// addresses that will actually receive the it-system mail of each kind — role-level addresses are
		// deduped against these, so they must be null when the corresponding template is disabled (deduping
		// against a mail that is never sent would leave the address without any notification)
		String[] coveredPerformerAddresses = systemPerformerTemplate.isEnabled() ? itEmailAddresses : null;
		String[] coveredAdvisAddresses = systemAdvisTemplate.isEnabled() ? itAdvisAddresses : null;

		Set<UserRole> allUserRoles = new HashSet<>();
		allUserRoles.addAll(toAddUserRoleMap.keySet());
		allUserRoles.addAll(toRemoveUserRoleMap.keySet());

		for (UserRole userRole : allUserRoles) {
			handleUserRoleSpecificMail(toAddUserRoleMap, toRemoveUserRoleMap, coveredPerformerAddresses, coveredAdvisAddresses, userRole, rolePerformerTemplate, roleAdvisTemplate);
		}

		if (hasAddresses(coveredPerformerAddresses) || hasAddresses(coveredAdvisAddresses)) {
			List<EmailTemplateRenderer.Row> userRows = buildItSystemUserRows(toAddMap, toRemoveMap);
			if (!userRows.isEmpty()) {
				Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
				values.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, legacyNullSafe(itSystem.getName()));

				sendContactMails(systemPerformerTemplate, coveredPerformerAddresses, values, userRows);
				sendContactMails(systemAdvisTemplate, coveredAdvisAddresses, values, userRows);
			}
		}
	}

	private void triggerManagerActionNotifications(Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap) {
		for (Map.Entry<UserInformationDTO, List<UserRoleInformationDTO>> entry : toAddMap.entrySet()) {
			User user = entry.getKey().user();
			for (UserRoleInformationDTO addRoleDto : entry.getValue()) {
				if (addRoleDto.userRole().isRequireManagerAction()) {
					notifyManagerActionRequired(user, addRoleDto.userRole());
				}
			}
		}
	}

	private void handleUserRoleSpecificMail(Map<UserRole, List<User>> toAddUserRoleMap, Map<UserRole, List<User>> toRemoveUserRoleMap, String[] coveredPerformerAddresses, String[] coveredAdvisAddresses, UserRole userRole, EmailTemplate performerTemplate, EmailTemplate advisTemplate) {
		List<User> addedUsers = toAddUserRoleMap.getOrDefault(userRole, Collections.emptyList());
		List<User> removedUsers = toRemoveUserRoleMap.getOrDefault(userRole, Collections.emptyList());

		// Skip if both are empty
		if (addedUsers.isEmpty() && removedUsers.isEmpty()) {
			return;
		}

		// addresses already covered by the it-system mail of the same kind are skipped to avoid duplicate mails
		String[] performerAddresses = performerTemplate.isEnabled() ? filterAddresses(userRole.getContactEmail(), coveredPerformerAddresses) : null;
		String[] advisAddresses = advisTemplate.isEnabled() ? filterAddresses(userRole.getAdvisEmail(), coveredAdvisAddresses) : null;
		if (!hasAddresses(performerAddresses) && !hasAddresses(advisAddresses)) {
			return;
		}

		List<EmailTemplateRenderer.Row> userRows = new ArrayList<>();
		for (User user : addedUsers) {
			userRows.add(buildRoleUserRow(user, true));
		}
		for (User user : removedUsers) {
			userRows.add(buildRoleUserRow(user, false));
		}

		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, legacyNullSafe(userRole.getItSystem().getName()));
		values.put(EmailTemplatePlaceholder.ROLE_NAME, legacyNullSafe(userRole.getName()));
		values.put(EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, legacyNullSafe(userRole.getDescription()));

		sendContactMails(performerTemplate, performerAddresses, values, userRows);
		sendContactMails(advisTemplate, advisAddresses, values, userRows);
	}

	private static boolean hasAddresses(String[] addresses) {
		return addresses != null && addresses.length > 0;
	}

	private static String[] filterAddresses(String emails, String[] alreadyNotified) {
		String[] addresses = splitAddresses(emails);
		if (addresses == null) {
			return null;
		}

		Set<String> covered = alreadyNotified != null ? new HashSet<>(Arrays.asList(alreadyNotified)) : Collections.emptySet();
		return Arrays.stream(addresses)
				.filter(email -> email != null && !covered.contains(email))
				.toArray(String[]::new);
	}

	private void sendContactMails(EmailTemplate template, String[] addresses, Map<EmailTemplatePlaceholder, String> values, List<EmailTemplateRenderer.Row> rows) {
		if (!template.isEnabled() || !hasAddresses(addresses)) {
			return;
		}

		String title = emailTemplateRenderer.renderTitle(template, values);
		String message = emailTemplateRenderer.renderMessage(template, values, rows);

		for (String address : addresses) {
			try {
				emailService.sendMessage(address, title, message, null);
			}
			catch (Exception ex) {
				log.error("Exception occured while synchronizing manual ItSystem. Could not send '" + title + "' to: " + address + ". Exception:" + ex.getMessage());
				// we just continue with the next one - someone has to fix this, and then perform a full sync
			}
		}
	}

	// the legacy MessageFormat based mails rendered null arguments as the string "null" — preserved
	// for output compatibility, since some municipalities parse these mails with robots
	private static String legacyNullSafe(String value) {
		return value != null ? value : "null";
	}

	private List<EmailTemplateRenderer.Row> buildItSystemUserRows(Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap) {
		List<EmailTemplateRenderer.Row> rows = new ArrayList<>();

		// Users with added roles (and possibly removed)
		for (UserInformationDTO dto : toAddMap.keySet()) {
			if (!toAddMap.get(dto).isEmpty() || Objects.nonNull(toRemoveMap.get(dto))) {
				rows.add(buildItSystemUserRow(dto, toAddMap.get(dto), toRemoveMap.get(dto)));
			}
		}

		// Users with only removed roles
		for (UserInformationDTO dto : toRemoveMap.keySet()) {
			if (toAddMap.get(dto) == null) {
				rows.add(buildItSystemUserRow(dto, Collections.emptyList(), toRemoveMap.get(dto)));
			}
		}

		return rows;
	}

	private EmailTemplateRenderer.Row buildItSystemUserRow(UserInformationDTO userInfo, List<UserRoleInformationDTO> addedRoles, List<UserRoleInformationDTO> removedRoles) {
		List<EmailTemplateRenderer.Row> changeRows = new ArrayList<>();
		for (UserRoleInformationDTO addRoleDto : addedRoles) {
			changeRows.add(buildChangeRow(addRoleDto, true));
		}
		if (removedRoles != null) {
			for (UserRoleInformationDTO removeRoleDto : removedRoles) {
				changeRows.add(buildChangeRow(removeRoleDto, false));
			}
		}

		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.USER_PLACEHOLDER, legacyNullSafe(userInfo.user().getName()));
		values.put(EmailTemplatePlaceholder.USER_ID_PLACEHOLDER, legacyNullSafe(userInfo.user().getUserId()));
		values.put(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, legacyNullSafe(userInfo.ouName()));
		values.put(EmailTemplatePlaceholder.PERSON_UUID_PLACEHOLDER, legacyNullSafe(userInfo.user().getExtUuid()));

		return new EmailTemplateRenderer.Row(values, changeRows);
	}

	private EmailTemplateRenderer.Row buildChangeRow(UserRoleInformationDTO dto, boolean added) {
		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, added ? "Tilføj" : "Fjern");
		values.put(EmailTemplatePlaceholder.ACTION_PAST_PLACEHOLDER, added ? "tildelt" : "fjernet");
		values.put(EmailTemplatePlaceholder.ROLE_NAME, legacyNullSafe(dto.userRole().getName()));
		values.put(EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, legacyNullSafe(dto.userRole().getDescription()));
		values.put(EmailTemplatePlaceholder.ASSIGNED_BY_PLACEHOLDER, legacyNullSafe(dto.responsible()));

		return new EmailTemplateRenderer.Row(values);
	}

	private EmailTemplateRenderer.Row buildRoleUserRow(User user, boolean added) {
		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, added ? "Tilføj" : "Fjern");
		values.put(EmailTemplatePlaceholder.USER_PLACEHOLDER, legacyNullSafe(user.getName()));
		values.put(EmailTemplatePlaceholder.USER_ID_PLACEHOLDER, legacyNullSafe(user.getUserId()));
		values.put(EmailTemplatePlaceholder.PERSON_UUID_PLACEHOLDER, legacyNullSafe(user.getExtUuid()));

		return new EmailTemplateRenderer.Row(values);
	}

	private void detectRemovedRoles(Map<String, User> userMap, Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap, Map<String, List<CurrentAssignment>> currentAssignmentsMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<User>> toRemoveUserRoleMap) {
		List<ManualAssignmentNotificationMap> toDelete = new ArrayList<>();

		for (String domainAndUserId : lastSyncAssignmentsMap.keySet()) {
			List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser = lastSyncAssignmentsMap.get(domainAndUserId);
			List<CurrentAssignment> todayAssignmentsForUser = currentAssignmentsMap.get(domainAndUserId);
			if (todayAssignmentsForUser == null) {
				todayAssignmentsForUser = new ArrayList<>();
			}

			// group duplicates by userRoleId so they are cleaned up together — otherwise a stale duplicate would re-trigger a removal email on a future run
			Map<Long, List<ManualAssignmentNotificationMap>> groupedByRole = lastSyncAssignmentsForUser.stream()
				.collect(Collectors.groupingBy(ManualAssignmentNotificationMap::getUserRoleId));

			for (Map.Entry<Long, List<ManualAssignmentNotificationMap>> entry : groupedByRole.entrySet()) {
				Long userRoleId = entry.getKey();
				// pick the oldest row as representative so assignedBy/orgUnitName in the email is deterministic regardless of grouping order
				List<ManualAssignmentNotificationMap> duplicates = entry.getValue().stream()
					.sorted(Comparator.comparingLong(ManualAssignmentNotificationMap::getId))
					.toList();
				ManualAssignmentNotificationMap representative = duplicates.get(0);

				boolean existsToday = todayAssignmentsForUser.stream().anyMatch(t -> Objects.equals(t.getUserRole().getId(), userRoleId));

				if (!existsToday) {
					removeRole(userMap, userRoleMap, toRemoveMap, toRemoveUserRoleMap, domainAndUserId, representative);
					toDelete.addAll(duplicates);
				}
				else if (duplicates.size() > 1) {
					// role still assigned, but prune surplus rows so future removal does not fan out into multiple emails
					toDelete.addAll(duplicates.subList(1, duplicates.size()));
				}
			}
		}

		// Delete all removed assignments after iteration
		manualAssignmentNotificationMapDao.deleteAll(toDelete);
	}

	private static void removeRole(Map<String, User> userMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<User>> toRemoveUserRoleMap, String domainAndUserId, ManualAssignmentNotificationMap assignment) {
		log.info("role " + assignment.getUserRoleId() + " has been removed from " + domainAndUserId);

		UserRole userRole = userRoleMap.get(assignment.getUserRoleId());
		if (userRole == null) {
			log.warn("Unknown userRole: " + assignment.getUserRoleId());
			return;
		}

		User user = userMap.get(domainAndUserId);
		if (user == null) {
			log.warn("Unknown user: " + domainAndUserId);
			return;
		}
		UserInformationDTO userInfo = new UserInformationDTO(user, assignment.getOrgUnitName() != null ? assignment.getOrgUnitName() : "");

		List<UserRoleInformationDTO> usersRoles = toRemoveMap.computeIfAbsent(userInfo, _ -> new ArrayList<>());
		UserRoleInformationDTO userRoleInformationDTO = new UserRoleInformationDTO(userRole, assignment.getAssignedBy());
		usersRoles.add(userRoleInformationDTO);
		toRemoveUserRoleMap.computeIfAbsent(userRole, _ -> new ArrayList<>()).add(user);
	}

	private void detectAddedRoles(Map<String, User> userMap, Map<String, List<CurrentAssignment>> currentAssignmentsMap, Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserRole, List<User>> toAddUserRoleMap) {
		for (String domainAndUserId : currentAssignmentsMap.keySet()) {
			List<CurrentAssignment> currentActiveAssignmentsForUser = currentAssignmentsMap.get(domainAndUserId);
			List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser = lastSyncAssignmentsMap.get(domainAndUserId);
			if (lastSyncAssignmentsForUser == null) {
				lastSyncAssignmentsForUser = new ArrayList<>();
			}

			// filter duplicate assignments
			currentActiveAssignmentsForUser = currentActiveAssignmentsForUser.stream().filter(StreamExtensions.distinctByKey(a -> a.getUserRole().getId())).collect(Collectors.toList());

			// compare with yesterday
			for (CurrentAssignment assignment : currentActiveAssignmentsForUser) {
				boolean existedYesterday = lastSyncAssignmentsForUser.stream().anyMatch(a -> Objects.equals(a.getUserRoleId(), assignment.getUserRole().getId()));

				if (!existedYesterday) {

					addRole(userMap, userRoleMap, toAddMap, toAddUserRoleMap, domainAndUserId, assignment, lastSyncAssignmentsForUser);
				}
			}
		}
	}

	private void addRole(Map<String, User> userMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserRole, List<User>> toAddUserRoleMap, String domainAndUserId, CurrentAssignment assignment, List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser) {
		UserRole userRole = userRoleMap.get(assignment.getUserRole().getId());
		if (userRole == null) {
			log.warn("Unknown userRole: " + assignment.getUserRole().getId());
			return;
		}

		User user = userMap.get(domainAndUserId);
		if (user == null) {
			log.warn("Unknown user: " + domainAndUserId);
			return;
		}

		String infoMsg = "role " + assignment.getUserRole().getId() + " has been assigned to " + domainAndUserId;
		UserInformationDTO userInfo = new UserInformationDTO(user, assignment.getResponsibleOrgUnit() != null ? assignment.getResponsibleOrgUnit().getName() : "<ukendt enhed>");
		UserRoleInformationDTO userRoleInformationDTO = new UserRoleInformationDTO(userRole, assignment.getAssignedBy());
		List<UserRoleInformationDTO> usersRoles = toAddMap.computeIfAbsent(userInfo, _ -> new ArrayList<>());
		log.info(infoMsg);
		usersRoles.add(userRoleInformationDTO);
		toAddUserRoleMap.computeIfAbsent(userRole, _ -> new ArrayList<>()).add(user);

		ManualAssignmentNotificationMap notificationMap = createManualAssignmentNotificationMap(assignment, user);
		notificationMap = manualAssignmentNotificationMapService.save(notificationMap);
		lastSyncAssignmentsForUser.add(notificationMap);
	}

	private static @NotNull ManualAssignmentNotificationMap createManualAssignmentNotificationMap(CurrentAssignment assignment, User user) {
		ManualAssignmentNotificationMap notificationMap = new ManualAssignmentNotificationMap();
		notificationMap.setUserRoleId(assignment.getUserRole().getId());
		notificationMap.setUserUserId(user.getUserId());
		notificationMap.setDomainId(user.getDomain().getId());
		notificationMap.setOrgUnitName(assignment.getResponsibleOrgUnit() != null ? assignment.getResponsibleOrgUnit().getName() : null);
		notificationMap.setAssignedBy(assignment.getAssignedBy());
		return notificationMap;
	}

	private void notifyManagerActionRequired(User user, UserRole userRole) {
		UserRoleEmailTemplate template = userRole.getUserRoleEmailTemplate();

		if (template == null) {
			log.warn("The UserRoleEmailTemplate on the user role with id " + userRole.getId() + " was null. Will not send email.");
			return;
		}

		Set<OrgUnit> orgUnits = user.getPositions().stream()
			.map(Position::getOrgUnit)
			.collect(Collectors.toSet());

		Set<User> recipients = getRecipients(user, userRole, orgUnits);

		String recipientNames = recipients.stream().map(User::getName).collect(Collectors.joining(", "));
		String recipientMails = recipients.stream().map(User::getEmail).collect(Collectors.joining(","));

		if (!recipientMails.isBlank()) {
			sendNotifyManagerActionRequiredMail(user, userRole, template, recipientNames, recipientMails);
		}
		else {
			log.warn("Could not notify manager on assignment of " + userRole.getId() + " to user " + user.getUserId() + " because no managers, substitutes or authorizationManagers where available or had email adresses");
		}
	}

	private void sendNotifyManagerActionRequiredMail(User user, UserRole userRole, UserRoleEmailTemplate template, String recipientNames, String recipientMails) {
		String orgMessage = template.getMessage();
		List<InlineImageDTO> inlineImages = transformImages(template);

		String title = template.getTitle();
		title = title.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), recipientNames);
		title = title.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), userRole.getName());
		title = title.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), user.getName() + " (" + user.getUserId() + ")");

		String message = template.getMessage();
		message = message.replace(EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER.getPlaceholder(), recipientNames);
		message = message.replace(EmailTemplatePlaceholder.ROLE_NAME.getPlaceholder(), userRole.getName());
		message = message.replace(EmailTemplatePlaceholder.USER_PLACEHOLDER.getPlaceholder(), user.getName() + " (" + user.getUserId() + ")");

		emailService.sendMessage(recipientMails, title, message, inlineImages, null);

		// we need to set the original message again because for some reason the template is saved to the db
		template.setMessage(orgMessage);
	}

	private @NotNull Set<User> getRecipients(User user, UserRole userRole, Set<OrgUnit> orgUnits) {
		Set<User> recipients = new HashSet<>();
		List<User> managers = userService.getManager(user).stream().filter(m -> StringUtils.hasLength(m.getEmail())).toList();
		recipients.addAll(managers);

		if (userRole.isSendToSubstitutes()) {
			for (OrgUnit orgUnit : orgUnits) {
				for (User substitute : managerSubstituteService.getSubstitutesForOrgUnit(orgUnit)) {
					if (!substitute.isDeleted() && StringUtils.hasLength(substitute.getEmail())) {
						recipients.add(substitute);
					}
				}
			}
		}

		if (userRole.isSendToAuthorizationManagers()) {
			for (OrgUnit orgUnit : orgUnits) {
				recipients.addAll(orgUnit.getAuthorizationManagers().stream()
					.map(AuthorizationManager::getUser)
					.filter(u -> StringUtils.hasLength(u.getEmail()))
					.collect(Collectors.toSet()));
			}
		}
		return recipients;
	}

	private List<InlineImageDTO> transformImages(UserRoleEmailTemplate email) {
		List<InlineImageDTO> inlineImages = new ArrayList<>();
		String message = email.getMessage();
		Document doc = Jsoup.parse(message);

		for (Element img : doc.select("img")) {
			String src = img.attr("src");
			if (!StringUtils.hasLength(src)) {
				continue;
			}

			InlineImageDTO inlineImageDto = new InlineImageDTO();
			inlineImageDto.setBase64(src.contains("base64"));

			if (!inlineImageDto.isBase64()) {
				continue;
			}

			String cID = UUID.randomUUID().toString();
			inlineImageDto.setCid(cID);
			inlineImageDto.setSrc(src);
			inlineImages.add(inlineImageDto);
			img.attr("src", "cid:" + cID);
		}

		email.setMessage(doc.html());

		return inlineImages;
	}
}
