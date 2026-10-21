package dk.digitalidentity.rc.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.ManualNotificationPendingUserDao;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUser;
import dk.digitalidentity.rc.dao.model.ManualNotificationPendingUserProjection;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.assignment.AssignmentReasonService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.assignment.model.AssignmentReasons;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

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
	private AssignmentReasonService assignmentReasonService;

	@Autowired
	private ManualAssignmentNotificationMapService manualAssignmentNotificationMapService;

	@Autowired
	private ManualNotificationPendingUserDao manualNotificationPendingUserDao;

	@Autowired
	private PostponedConstraintService postponedConstraintService;

	@Autowired
	private ObjectProvider<ManualRolesService> self;

	@PersistenceContext
	private EntityManager entityManager;

	// it-system types covered by the assignment-change notification scan
	private static final List<ItSystemType> NOTIFICATION_IT_SYSTEM_TYPES =
			Arrays.asList(ItSystemType.MANUAL, ItSystemType.AD, ItSystemType.SAML, ItSystemType.KOMBIT, ItSystemType.KSPCICS, ItSystemType.NEMLOGIN);

	public record UserInformationDTO(User user, String ouName) {}
	public record UserRoleInformationDTO(UserRole userRole, String responsible, List<CurrentAssignmentPostponedConstraint> postponedConstraints, String reason) {}
	public record RoleUserInformationDTO(User user, String ouName, String assignedBy, String reason, List<CurrentAssignmentPostponedConstraint> postponedConstraints) {}
	private record AddedRole(String domainAndUserId, CurrentAssignment assignment, List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser) {}

	@Transactional
	public void notifyServicedesk() {
		// undgå O(n²) autoflush: loopet over it-systemer/brugere akkumulerer managed entities, og en
		// autoflush før hver query ville dirty-checke/cascade over hele den voksende context (jf.
		// AssignmentChangeEventHandlerService.updateUsers). Alt skrives korrekt ved commit.
		entityManager.setFlushMode(FlushModeType.COMMIT);

		long startTime = System.currentTimeMillis();

		LocalDateTime firstRun = settingsService.getFirstManualITSystemRun();

		// include soft-deleted users: the event-driven processPendingUsers() deliberately skips deleted
		// users and delegates their removal notifications to this sweep, so removed roles on a deleted
		// user would otherwise never be mailed (removeRole() can't resolve the user and drops the mail)
		Map<String, User> userMap = userService.getAllIncludingInactive().stream().collect(Collectors.toMap(u -> u.getDomain().getId() + "!" + u.getUserId(), Function.identity(), (a, _) -> a));

		List<ItSystem> itSystems = itSystemService.getBySystemTypeIn(NOTIFICATION_IT_SYSTEM_TYPES);
		for (ItSystem itSystem : itSystems) {
			processItSystem(itSystem, userMap, firstRun, true);
		}

		if (firstRun == null) {
			settingsService.setFirstManualITSystemRun(LocalDateTime.now());
		}

		log.info("Completed notifyServiceDesk in " + (System.currentTimeMillis() - startTime) + "ms");
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

    public void processPendingUsers() {
        LocalDateTime firstRun = settingsService.getFirstManualITSystemRun();

        long lastId = 0L;
        boolean processedAnything = false;

        while (true) {
            List<ManualNotificationPendingUserProjection> batch = manualNotificationPendingUserDao.findBatchAfter(lastId, Limit.of(100));
            if (batch.isEmpty()) {
                break;
            }

            self.getObject().processBatch(batch, firstRun);
            lastId = batch.getLast().id();
            processedAnything = true;
        }

        if (processedAnything && firstRun == null) {
            settingsService.setFirstManualITSystemRun(LocalDateTime.now());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBatch(List<ManualNotificationPendingUserProjection> batch, LocalDateTime firstRun) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        Set<String> uuids = batch.stream()
                .map(ManualNotificationPendingUserProjection::userUuid)
                .collect(Collectors.toSet());

        List<User> users = userService.getAllActiveByUuidIn(uuids);
        if (!users.isEmpty()) {
            processUsers(users, firstRun);
        }

        manualNotificationPendingUserDao.deleteAllByIdIn(batch.stream().map(ManualNotificationPendingUserProjection::id).toList());
    }

	private void processUsers(List<User> users, LocalDateTime firstRun) {
		Map<String, User> userMap = users.stream()
			.collect(Collectors.toMap(u -> u.getDomain().getId() + "!" + u.getUserId(), Function.identity(), (a, _) -> a));

		List<ItSystem> manualItSystems = itSystemService.getBySystemTypeIn(NOTIFICATION_IT_SYSTEM_TYPES);

		// single query scoped to the pending users + manual it-systems
		Set<CurrentAssignment> allCurrentAssignments = assignmentService.getActiveAssignmentsByUsersAndItSystems(users, manualItSystems);

		// single query for all notification-map rows for the pending users;
		// filter in-memory to guard against domainId×userId cross-product false positives
		Set<Long> domainIds = users.stream().map(u -> u.getDomain().getId()).collect(Collectors.toSet());
		Set<String> userIds = users.stream().map(User::getUserId).collect(Collectors.toSet());
		List<ManualAssignmentNotificationMap> allLastSyncMaps = manualAssignmentNotificationMapService.getForUsers(domainIds, userIds)
			.stream()
			.filter(m -> userMap.containsKey(m.getDomainId() + "!" + m.getUserUserId()))
			.toList();

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

		// single query for all user roles across all relevant it-systems
		Map<Long, Map<Long, UserRole>> userRolesByItSystem = userRoleService.getByItSystems(relevantItSystems.values()).stream()
			.collect(Collectors.groupingBy(ur -> ur.getItSystem().getId(),
				Collectors.toMap(UserRole::getId, Function.identity())));

		for (ItSystem itSystem : relevantItSystems.values()) {
			Map<Long, UserRole> userRoleMap = userRolesByItSystem.getOrDefault(itSystem.getId(), Collections.emptyMap());

			if (!hasContactEmail(itSystem, userRoleMap)) {
				log.debug("Skipping it-system without contact email(s) : " + itSystem.getName() + " / " + itSystem.getId());
				continue;
			}

			Set<CurrentAssignment> currentAssignments = new HashSet<>(currentByItSystem.getOrDefault(itSystem.getId(), Collections.emptyList()));
			List<ManualAssignmentNotificationMap> lastSyncMaps = allLastSyncMaps.stream()
				.filter(m -> userRoleMap.containsKey(m.getUserRoleId()))
				.toList();

			processDetectedChanges(itSystem, userRoleMap, currentAssignments, lastSyncMaps, userMap, firstRun, false);
		}
	}

	private void processItSystem(ItSystem itSystem, Map<String, User> userMap, LocalDateTime firstRun, boolean fullSweep) {
		Map<Long, UserRole> userRoleMap = userRoleService.getByItSystem(itSystem).stream().collect(Collectors.toMap(UserRole::getId, Function.identity()));

		// neither the it-system or any of the userRoles has an Email, so skip
		if (!hasContactEmail(itSystem, userRoleMap)) {
			log.debug("Skipping it-system without contact email(s) : " + itSystem.getName() + " / " + itSystem.getId());
			return;
		}

		log.info("Detecting role changes on " + itSystem.getName() + " / " + itSystem.getId());

		Set<CurrentAssignment> currentAssignments = assignmentService.getActiveAssignmentsByItSystem(itSystem);
		List<ManualAssignmentNotificationMap> manualAssignmentNotificationMaps = manualAssignmentNotificationMapService.getForRoles(userRoleMap.keySet());

		processDetectedChanges(itSystem, userRoleMap, currentAssignments, manualAssignmentNotificationMaps, userMap, firstRun, fullSweep);
	}

	public boolean hasContactEmail(ItSystem itSystem, Map<Long, UserRole> userRoleMap) {
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

	private void processDetectedChanges(ItSystem itSystem, Map<Long, UserRole> userRoleMap, Set<CurrentAssignment> currentAssignments, List<ManualAssignmentNotificationMap> manualAssignmentNotificationMaps, Map<String, User> userMap, LocalDateTime firstRun, boolean fullSweep) {
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap = new HashMap<>();
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap = new HashMap<>();
		Map<UserRole, List<RoleUserInformationDTO>> toAddUserRoleMap = new HashMap<>();
		Map<UserRole, List<RoleUserInformationDTO>> toRemoveUserRoleMap = new HashMap<>();

		Map<String, List<CurrentAssignment>> currentAssignmentsMap = currentAssignments.stream().collect(Collectors.groupingBy(c -> c.getUser().getDomain().getId() + "!" + c.getUser().getUserId()));
		Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap = manualAssignmentNotificationMaps.stream().collect(Collectors.groupingBy(m -> m.getDomainId() + "!" + m.getUserUserId()));

		List<ManualAssignmentNotificationMap> mapsToSave = new ArrayList<>();
		List<ManualAssignmentNotificationMap> mapsToDelete = new ArrayList<>();
		detectAddedRoles(userMap, currentAssignmentsMap, lastSyncAssignmentsMap, userRoleMap, toAddMap, toAddUserRoleMap, mapsToSave, mapsToDelete);
		detectRemovedRoles(userMap, lastSyncAssignmentsMap, currentAssignmentsMap, userRoleMap, toRemoveMap, toRemoveUserRoleMap, mapsToSave);
		if (!mapsToDelete.isEmpty()) {
			manualAssignmentNotificationMapDao.deleteAll(mapsToDelete);
		}
		if (!mapsToSave.isEmpty()) {
			manualAssignmentNotificationMapDao.saveAll(mapsToSave);
		}

		String[] itEmailAddresses = splitAddresses(itSystem.getEmail());
		String[] itAdvisAddresses = splitAddresses(itSystem.getAdvisEmail());

		// Only send emails if first run was more than 3 hours ago
		boolean shouldSendEmails = firstRun != null && LocalDateTime.now().isAfter(firstRun.plusHours(3));

		// an it-system's first eligible run (e.g. it just got a contact/advis email configured) has no
		// baseline yet, so every existing assignment would look "added". Only the full sweep sees every
		// user for the it-system in one go and can therefore build a complete baseline; the event-driven
		// flush only ever sees the batch of users that just changed, so flipping the marker there would
		// mark the it-system "initialized" with a partial baseline, and the next full sweep would then
		// flood mails for every other pre-existing assignment it hadn't seen yet (ROL-503 / #102). The
		// event-driven path therefore reads the marker but never flips it - only the full sweep does.
		if (!itSystem.isContactNotificationsInitialized()) {
			if (fullSweep) {
				itSystem.setContactNotificationsInitialized(true);
			}
			shouldSendEmails = false;
		}

		if (shouldSendEmails && (!toAddMap.isEmpty() || !toRemoveMap.isEmpty())) {
			sendChangeNotifications(itSystem, toAddMap, toRemoveMap, toAddUserRoleMap, toRemoveUserRoleMap, itEmailAddresses, itAdvisAddresses);
		}
	}

	private static String[] splitAddresses(String emails) {
		return StringUtils.hasLength(emails) ? emails.split(";") : null;
	}

	private void sendChangeNotifications(ItSystem itSystem, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<RoleUserInformationDTO>> toAddUserRoleMap, Map<UserRole, List<RoleUserInformationDTO>> toRemoveUserRoleMap, String[] itEmailAddresses, String[] itAdvisAddresses) {
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
			List<EmailTemplateRenderer.Row> performerRows = buildItSystemUserRows(toAddMap, toRemoveMap, true);
			if (!performerRows.isEmpty()) {
				List<EmailTemplateRenderer.Row> advisRows = buildItSystemUserRows(toAddMap, toRemoveMap, false);
				Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
				values.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, legacyNullSafe(itSystem.getName()));

				sendContactMails(systemPerformerTemplate, coveredPerformerAddresses, values, performerRows);
				sendContactMails(systemAdvisTemplate, coveredAdvisAddresses, values, advisRows);
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

	private void handleUserRoleSpecificMail(Map<UserRole, List<RoleUserInformationDTO>> toAddUserRoleMap, Map<UserRole, List<RoleUserInformationDTO>> toRemoveUserRoleMap, String[] coveredPerformerAddresses, String[] coveredAdvisAddresses, UserRole userRole, EmailTemplate performerTemplate, EmailTemplate advisTemplate) {
		List<RoleUserInformationDTO> addedUsers = toAddUserRoleMap.getOrDefault(userRole, Collections.emptyList());
		List<RoleUserInformationDTO> removedUsers = toRemoveUserRoleMap.getOrDefault(userRole, Collections.emptyList());

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

		List<EmailTemplateRenderer.Row> performerRows = new ArrayList<>();
		List<EmailTemplateRenderer.Row> advisRows = new ArrayList<>();
		for (RoleUserInformationDTO roleUser : addedUsers) {
			performerRows.add(buildRoleUserRow(userRole, roleUser, true, true));
			advisRows.add(buildRoleUserRow(userRole, roleUser, true, false));
		}
		for (RoleUserInformationDTO roleUser : removedUsers) {
			performerRows.add(buildRoleUserRow(userRole, roleUser, false, true));
			advisRows.add(buildRoleUserRow(userRole, roleUser, false, false));
		}

		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER, legacyNullSafe(userRole.getItSystem().getName()));
		values.put(EmailTemplatePlaceholder.ROLE_NAME, legacyNullSafe(userRole.getName()));
		values.put(EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, legacyNullSafe(userRole.getDescription()));

		sendContactMails(performerTemplate, performerAddresses, values, performerRows);
		sendContactMails(advisTemplate, advisAddresses, values, advisRows);
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

	private List<EmailTemplateRenderer.Row> buildItSystemUserRows(Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, boolean includeReason) {
		List<EmailTemplateRenderer.Row> rows = new ArrayList<>();

		// Users with added roles (and possibly removed)
		for (UserInformationDTO dto : toAddMap.keySet()) {
			if (!toAddMap.get(dto).isEmpty() || Objects.nonNull(toRemoveMap.get(dto))) {
				rows.add(buildItSystemUserRow(dto, toAddMap.get(dto), toRemoveMap.get(dto), includeReason));
			}
		}

		// Users with only removed roles
		for (UserInformationDTO dto : toRemoveMap.keySet()) {
			if (toAddMap.get(dto) == null) {
				rows.add(buildItSystemUserRow(dto, Collections.emptyList(), toRemoveMap.get(dto), includeReason));
			}
		}

		return rows;
	}

	private EmailTemplateRenderer.Row buildItSystemUserRow(UserInformationDTO userInfo, List<UserRoleInformationDTO> addedRoles, List<UserRoleInformationDTO> removedRoles, boolean includeReason) {
		List<EmailTemplateRenderer.Row> changeRows = new ArrayList<>();
		for (UserRoleInformationDTO addRoleDto : addedRoles) {
			changeRows.add(buildChangeRow(addRoleDto, true, includeReason));
		}
		if (removedRoles != null) {
			for (UserRoleInformationDTO removeRoleDto : removedRoles) {
				changeRows.add(buildChangeRow(removeRoleDto, false, includeReason));
			}
		}

		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.USER_PLACEHOLDER, legacyNullSafe(userInfo.user().getName()));
		values.put(EmailTemplatePlaceholder.USER_ID_PLACEHOLDER, legacyNullSafe(userInfo.user().getUserId()));
		values.put(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, legacyNullSafe(userInfo.ouName()));
		values.put(EmailTemplatePlaceholder.PERSON_UUID_PLACEHOLDER, legacyNullSafe(userInfo.user().getExtUuid()));

		return new EmailTemplateRenderer.Row(values, changeRows);
	}

	private EmailTemplateRenderer.Row buildChangeRow(UserRoleInformationDTO dto, boolean added, boolean includeReason) {
		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, added ? "Tilføj" : "Fjern");
		values.put(EmailTemplatePlaceholder.ACTION_PAST_PLACEHOLDER, added ? "tildelt" : "fjernet");
		values.put(EmailTemplatePlaceholder.ROLE_NAME, legacyNullSafe(dto.userRole().getName()));
		values.put(EmailTemplatePlaceholder.ROLE_DESCRIPTION_PLACEHOLDER, legacyNullSafe(dto.userRole().getDescription()));
		values.put(EmailTemplatePlaceholder.ASSIGNED_BY_PLACEHOLDER, legacyNullSafe(dto.responsible()));
		values.put(EmailTemplatePlaceholder.CONSTRAINT_VALUES_PLACEHOLDER, formatConstraints(dto.userRole(), dto.postponedConstraints()));
		if (includeReason) {
			values.put(EmailTemplatePlaceholder.REQUEST_REASON, dto.reason() != null ? dto.reason() : "");
		}

		return new EmailTemplateRenderer.Row(values);
	}

	private String formatConstraints(UserRole userRole, Collection<CurrentAssignmentPostponedConstraint> constraints) {
		if (constraints == null || constraints.isEmpty()) {
			return "";
		}

		List<SystemRoleAssignment> systemRoleAssignments = userRole.getSystemRoleAssignments();
		Map<Long, String> systemRoleNameById = systemRoleAssignments == null ? Collections.emptyMap() : systemRoleAssignments.stream()
			.collect(Collectors.toMap(sra -> sra.getSystemRole().getId(), sra -> sra.getSystemRole().getName(), (a, _) -> a));

		Map<Long, List<CurrentAssignmentPostponedConstraint>> bySystemRole = constraints.stream()
			.sorted(Comparator.comparing(CurrentAssignmentPostponedConstraint::getConstraintTypeName))
			.collect(Collectors.groupingBy(CurrentAssignmentPostponedConstraint::getSystemRoleId, LinkedHashMap::new, Collectors.toList()));

		StringBuilder sb = new StringBuilder(", afgrænset til");
		for (Long systemRoleId : bySystemRole.keySet().stream().sorted().toList()) {
			sb.append("<br>Systemrolle: ").append(legacyNullSafe(systemRoleNameById.get(systemRoleId)));

			for (CurrentAssignmentPostponedConstraint c : bySystemRole.get(systemRoleId)) {
				sb.append("<br>&nbsp;&nbsp;").append(c.getConstraintTypeName()).append(": ").append(postponedConstraintService.resolveConstraintDisplayValue(c));
			}
		}

		return sb.toString();
	}

	private EmailTemplateRenderer.Row buildRoleUserRow(UserRole userRole, RoleUserInformationDTO roleUser, boolean added, boolean includeReason) {
		User user = roleUser.user();
		Map<EmailTemplatePlaceholder, String> values = new EnumMap<>(EmailTemplatePlaceholder.class);
		values.put(EmailTemplatePlaceholder.ACTION_PLACEHOLDER, added ? "Tilføj" : "Fjern");
		values.put(EmailTemplatePlaceholder.ACTION_PAST_PLACEHOLDER, added ? "tildelt" : "fjernet");
		values.put(EmailTemplatePlaceholder.USER_PLACEHOLDER, legacyNullSafe(user.getName()));
		values.put(EmailTemplatePlaceholder.USER_ID_PLACEHOLDER, legacyNullSafe(user.getUserId()));
		values.put(EmailTemplatePlaceholder.ORGUNIT_PLACEHOLDER, legacyNullSafe(roleUser.ouName()));
		values.put(EmailTemplatePlaceholder.PERSON_UUID_PLACEHOLDER, legacyNullSafe(user.getExtUuid()));
		values.put(EmailTemplatePlaceholder.ASSIGNED_BY_PLACEHOLDER, legacyNullSafe(roleUser.assignedBy()));
		values.put(EmailTemplatePlaceholder.CONSTRAINT_VALUES_PLACEHOLDER, formatConstraints(userRole, roleUser.postponedConstraints()));
		if (includeReason) {
			values.put(EmailTemplatePlaceholder.REQUEST_REASON, roleUser.reason() != null ? roleUser.reason() : "");
		}

		return new EmailTemplateRenderer.Row(values);
	}

	private void detectRemovedRoles(Map<String, User> userMap, Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap, Map<String, List<CurrentAssignment>> currentAssignmentsMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<RoleUserInformationDTO>> toRemoveUserRoleMap, List<ManualAssignmentNotificationMap> mapsToSave) {
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
				// pick the oldest row as representative so orgUnitName/removedBy in the email is deterministic regardless of grouping order
				List<ManualAssignmentNotificationMap> duplicates = entry.getValue().stream()
					.sorted(Comparator.comparingLong(ManualAssignmentNotificationMap::getId))
					.toList();
				ManualAssignmentNotificationMap representative = duplicates.get(0);

				boolean existsToday = todayAssignmentsForUser.stream().anyMatch(t -> Objects.equals(t.getUserRole().getId(), userRoleId));

				if (!existsToday) {
					removeRole(userMap, userRoleMap, toRemoveMap, toRemoveUserRoleMap, domainAndUserId, representative);
					toDelete.addAll(duplicates);
				}
				else {
					// clear a stale removalReason/removedBy so it doesn't attach to some later, unrelated removal
					if (representative.getRemovalReason() != null || representative.getRemovedBy() != null) {
						representative.setRemovalReason(null);
						representative.setRemovedBy(null);
						mapsToSave.add(representative);
					}
					if (duplicates.size() > 1) {
						// role still assigned, but prune surplus rows so future removal does not fan out into multiple emails
						toDelete.addAll(duplicates.subList(1, duplicates.size()));
					}
				}
			}
		}

		// Delete all removed assignments after iteration
		manualAssignmentNotificationMapDao.deleteAll(toDelete);
	}

	private static void removeRole(Map<String, User> userMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<RoleUserInformationDTO>> toRemoveUserRoleMap, String domainAndUserId, ManualAssignmentNotificationMap assignment) {
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
		String ouName = assignment.getOrgUnitName() != null ? assignment.getOrgUnitName() : "";
		UserInformationDTO userInfo = new UserInformationDTO(user, ouName);
		String removedBy = assignment.getRemovedBy() != null ? assignment.getRemovedBy() : SecurityUtil.SYSTEM_ACTOR;

		List<UserRoleInformationDTO> usersRoles = toRemoveMap.computeIfAbsent(userInfo, _ -> new ArrayList<>());
		UserRoleInformationDTO userRoleInformationDTO = new UserRoleInformationDTO(userRole, removedBy, new ArrayList<>(), assignment.getRemovalReason());
		usersRoles.add(userRoleInformationDTO);
		toRemoveUserRoleMap.computeIfAbsent(userRole, _ -> new ArrayList<>()).add(new RoleUserInformationDTO(user, ouName, removedBy, assignment.getRemovalReason(), new ArrayList<>()));
	}

	private void detectAddedRoles(Map<String, User> userMap, Map<String, List<CurrentAssignment>> currentAssignmentsMap, Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserRole, List<RoleUserInformationDTO>> toAddUserRoleMap, List<ManualAssignmentNotificationMap> mapsToSave, List<ManualAssignmentNotificationMap> mapsToDelete) {
		List<AddedRole> addedRoles = new ArrayList<>();
		for (Map.Entry<String, List<CurrentAssignment>> userEntry : currentAssignmentsMap.entrySet()) {
			String domainAndUserId = userEntry.getKey();
			List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser = lastSyncAssignmentsMap.getOrDefault(domainAndUserId, new ArrayList<>());

			Map<Long, List<CurrentAssignment>> assignmentsByRole = userEntry.getValue().stream()
				.collect(Collectors.groupingBy(a -> a.getUserRole().getId()));

			for (Map.Entry<Long, List<CurrentAssignment>> roleEntry : assignmentsByRole.entrySet()) {
				Long userRoleId = roleEntry.getKey();

				// one candidate per distinct fingerprint; the ordering makes the chosen representative independent of set iteration order
				NavigableMap<String, CurrentAssignment> candidates = new TreeMap<>(Comparator.nullsFirst(Comparator.<String>naturalOrder()));
				for (CurrentAssignment assignment : roleEntry.getValue()) {
					candidates.putIfAbsent(buildConstraintFingerprint(assignment.getPostponedConstraints()), assignment);
				}

				List<ManualAssignmentNotificationMap> storedRows = lastSyncAssignmentsForUser.stream()
					.filter(m -> Objects.equals(m.getUserRoleId(), userRoleId))
					.toList();
				Set<String> storedFingerprints = storedRows.stream()
					.map(ManualAssignmentNotificationMap::getConstraintFingerprint)
					.collect(Collectors.toCollection(HashSet::new));

				// a match on any assignment means the role is known, otherwise the outcome would depend on which assignment is picked
				if (candidates.keySet().stream().anyMatch(storedFingerprints::contains)) {
					continue;
				}

				Map.Entry<String, CurrentAssignment> representative = candidates.firstEntry();

				// legacy row from before V1_361 has no fingerprint: upgrade silently instead of mailing
				Optional<ManualAssignmentNotificationMap> legacyRow = storedRows.stream()
					.filter(m -> m.getConstraintFingerprint() == null)
					.findFirst();
				if (legacyRow.isPresent()) {
					legacyRow.get().setConstraintFingerprint(representative.getKey());
					mapsToSave.add(legacyRow.get());
					continue;
				}

				// role existed but constraints changed: delete all stale rows for this role, then re-add
				for (ManualAssignmentNotificationMap stale : storedRows) {
					mapsToDelete.add(stale);
					lastSyncAssignmentsForUser.remove(stale);
				}
				addedRoles.add(new AddedRole(domainAndUserId, representative.getValue(), lastSyncAssignmentsForUser));
			}
		}

		AssignmentReasons reasons = assignmentReasonService.findFor(addedRoles.stream().map(AddedRole::assignment).toList());
		for (AddedRole addedRole : addedRoles) {
			String reason = reasons.reasonFor(addedRole.assignment()).orElse(null);
			addRole(userMap, userRoleMap, toAddMap, toAddUserRoleMap, addedRole.domainAndUserId(), addedRole.assignment(), reason, addedRole.lastSyncAssignmentsForUser(), mapsToSave);
		}
	}

	private void addRole(Map<String, User> userMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserRole, List<RoleUserInformationDTO>> toAddUserRoleMap, String domainAndUserId, CurrentAssignment assignment, String reason, List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser, List<ManualAssignmentNotificationMap> mapsToSave) {
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
		String ouName = assignment.getResponsibleOrgUnit() != null ? assignment.getResponsibleOrgUnit().getName() : "<ukendt enhed>";
		UserInformationDTO userInfo = new UserInformationDTO(user, ouName);
		UserRoleInformationDTO userRoleInformationDTO = new UserRoleInformationDTO(userRole, assignment.getAssignedBy(), new ArrayList<>(assignment.getPostponedConstraints()), reason);
		List<UserRoleInformationDTO> usersRoles = toAddMap.computeIfAbsent(userInfo, _ -> new ArrayList<>());
		log.info(infoMsg);
		usersRoles.add(userRoleInformationDTO);
		toAddUserRoleMap.computeIfAbsent(userRole, _ -> new ArrayList<>()).add(new RoleUserInformationDTO(user, ouName, assignment.getAssignedBy(), reason, new ArrayList<>(assignment.getPostponedConstraints())));

		ManualAssignmentNotificationMap notificationMap = createManualAssignmentNotificationMap(assignment, user);
		mapsToSave.add(notificationMap);
		lastSyncAssignmentsForUser.add(notificationMap);
	}

	private static @NotNull ManualAssignmentNotificationMap createManualAssignmentNotificationMap(CurrentAssignment assignment, User user) {
		ManualAssignmentNotificationMap notificationMap = new ManualAssignmentNotificationMap();
		notificationMap.setUserRoleId(assignment.getUserRole().getId());
		notificationMap.setUserUserId(user.getUserId());
		notificationMap.setDomainId(user.getDomain().getId());
		notificationMap.setOrgUnitName(assignment.getResponsibleOrgUnit() != null ? assignment.getResponsibleOrgUnit().getName() : null);
		notificationMap.setAssignedBy(assignment.getAssignedBy());
		notificationMap.setConstraintFingerprint(buildConstraintFingerprint(assignment.getPostponedConstraints()));
		return notificationMap;
	}

	private static String buildConstraintFingerprint(Collection<CurrentAssignmentPostponedConstraint> constraints) {
		if (constraints == null || constraints.isEmpty()) {
			return null;
		}
		String canonical = constraints.stream()
			.sorted(Comparator.comparingLong(CurrentAssignmentPostponedConstraint::getSystemRoleId)
				.thenComparing(CurrentAssignmentPostponedConstraint::getConstraintTypeEntityId))
			.map(c -> c.getSystemRoleId() + ":" + c.getConstraintTypeEntityId() + "=" + String.join(",", c.getValue()))
			.collect(Collectors.joining("|"));
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
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
