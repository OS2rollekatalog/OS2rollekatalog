package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.util.StreamExtensions;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
	private MessageSource messageSource;

	@Autowired
	private UserService userService;

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

	public record UserInformationDTO(User user, String ouName) {}
	public record UserRoleInformationDTO(UserRole userRole, String responsible) {}

	@Transactional
	public void notifyServicedesk() {
		LocalDateTime firstRun = settingsService.getFirstManualITSystemRun();

		Map<String, User> userMap = userService.getAll().stream().collect(Collectors.toMap(u -> u.getDomain().getId() + "!" + u.getUserId(), Function.identity()));

		List<ItSystem> itSystems = itSystemService.getBySystemTypeIn(Arrays.asList(ItSystemType.MANUAL, ItSystemType.AD, ItSystemType.SAML, ItSystemType.KOMBIT));
		for (ItSystem itSystem : itSystems) {
			processItSystem(itSystem, userMap, firstRun);
		}

		if (firstRun == null) {
			settingsService.setFirstManualITSystemRun(LocalDateTime.now());
		}
	}

	private void processItSystem(ItSystem itSystem, Map<String, User> userMap, LocalDateTime firstRun) {
		log.info("Detecting role changes on " + itSystem.getName() + " / " + itSystem.getId());

		boolean hasEmail = false;
		if (StringUtils.hasText(itSystem.getEmail())) {
			hasEmail = true;
		}

		Map<Long, UserRole> userRoleMap = userRoleService.getByItSystem(itSystem).stream().collect(Collectors.toMap(UserRole::getId, Function.identity()));

		if (!hasEmail) {
			for (UserRole userRole : userRoleMap.values()) {
				if (StringUtils.hasText(userRole.getContactEmail())) {
					hasEmail = true;
					break;
				}
			}
		}

		// neither the it-system or any of the userRoles has an Email, so skip
		if (!hasEmail) {
			log.info("Skipping it-system without contact email(s) : " + itSystem.getName() + " / " + itSystem.getId());
			return;
		}

		Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap = new HashMap<>();
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap = new HashMap<>();
		Map<UserRole, List<User>> toAddUserRoleMap = new HashMap<>();
		Map<UserRole, List<User>> toRemoveUserRoleMap = new HashMap<>();

		Set<CurrentAssignment> currentAssignments = assignmentService.getActiveAssignmentsByItSystem(itSystem);
		List<ManualAssignmentNotificationMap> manualAssignmentNotificationMaps = manualAssignmentNotificationMapService.getForRoles(userRoleMap.keySet());

		Map<String, List<CurrentAssignment>> currentAssignmentsMap = currentAssignments.stream().collect(Collectors.groupingBy(c -> c.getUser().getDomain().getId() + "!" + c.getUser().getUserId()));
		Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap = manualAssignmentNotificationMaps.stream().collect(Collectors.groupingBy(m -> m.getDomainId() + "!" + m.getUserUserId()));

		detectAddedRoles(userMap, currentAssignmentsMap, lastSyncAssignmentsMap, userRoleMap, toAddMap, toAddUserRoleMap);
		detectRemovedRoles(userMap, lastSyncAssignmentsMap, currentAssignmentsMap, userRoleMap, toRemoveMap, toRemoveUserRoleMap);

		String[] itEmailAddresses = null;
		if (StringUtils.hasLength(itSystem.getEmail())) {
			itEmailAddresses = itSystem.getEmail().split(";");
		}

		// Only send emails if first run was more than 3 hours ago
		boolean shouldSendEmails = firstRun != null && LocalDateTime.now().isAfter(firstRun.plusHours(3));

		if (shouldSendEmails && (!toAddMap.isEmpty() || !toRemoveMap.isEmpty())) {
			sendChangeNotifications(itSystem, toAddMap, toRemoveMap, toAddUserRoleMap, toRemoveUserRoleMap, itEmailAddresses);
		}
	}

	private void sendChangeNotifications(ItSystem itSystem, Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<User>> toAddUserRoleMap, Map<UserRole, List<User>> toRemoveUserRoleMap, String[] itEmailAddresses) {
		// Format message and send it
		if (!toAddMap.isEmpty() || !toRemoveMap.isEmpty() || !toAddUserRoleMap.isEmpty() || !toRemoveUserRoleMap.isEmpty()) {
			Set<UserRole> allUserRoles = new HashSet<>();
			allUserRoles.addAll(toAddUserRoleMap.keySet());
			allUserRoles.addAll(toRemoveUserRoleMap.keySet());

			for (UserRole userRole : allUserRoles) {
				if (StringUtils.hasText(userRole.getContactEmail())) {
					handleUserRoleSpecificMail(toAddUserRoleMap, toRemoveUserRoleMap, itEmailAddresses, userRole);
				}
			}

			if (itEmailAddresses != null && itEmailAddresses.length > 0) {
				StringBuilder usrAndRls = formatMessageForItSystem(toAddMap, toRemoveMap);
				for (String itEmailAddress : itEmailAddresses) {
					sendNotification(itSystem.getName(), usrAndRls, itEmailAddress);
				}
			}
		}
	}

	private void handleUserRoleSpecificMail(Map<UserRole, List<User>> toAddUserRoleMap, Map<UserRole, List<User>> toRemoveUserRoleMap, String[] itEmailAddresses, UserRole userRole) {
		List<User> addedUsers = toAddUserRoleMap.getOrDefault(userRole, Collections.emptyList());
		List<User> removedUsers = toRemoveUserRoleMap.getOrDefault(userRole, Collections.emptyList());

		// Skip if both are empty
		if (addedUsers.isEmpty() && removedUsers.isEmpty()) {
			return;
		}

		String[] emails = userRole.getContactEmail().split(";");
		StringBuilder roleAndUsers = formatMessageForRoleEmail(userRole, addedUsers, removedUsers);
		for (String email : emails) {
			if (itEmailAddresses == null || email == null || existsInItSystem(itEmailAddresses, email)) {
				continue;
			}
			sendNotification(userRole.getName(), roleAndUsers, email);
		}
	}

	private void detectRemovedRoles(Map<String, User> userMap, Map<String, List<ManualAssignmentNotificationMap>> lastSyncAssignmentsMap, Map<String, List<CurrentAssignment>> currentAssignmentsMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<User>> toRemoveUserRoleMap) {
		List<ManualAssignmentNotificationMap> toDelete = new ArrayList<>();

		for (String domainAndUserId : lastSyncAssignmentsMap.keySet()) {
			List<ManualAssignmentNotificationMap> lastSyncAssignmentsForUser = lastSyncAssignmentsMap.get(domainAndUserId);
			List<CurrentAssignment> todayAssignmentsForUser = currentAssignmentsMap.get(domainAndUserId);
			if (todayAssignmentsForUser == null) {
				todayAssignmentsForUser = new ArrayList<>();
			}

			// filter duplicate assignments
			lastSyncAssignmentsForUser = lastSyncAssignmentsForUser.stream().filter(StreamExtensions.distinctByKey(ManualAssignmentNotificationMap::getUserRoleId)).toList();

			// compare with current assignments
			for (ManualAssignmentNotificationMap assignment : lastSyncAssignmentsForUser) {
				boolean existsToday = todayAssignmentsForUser.stream().anyMatch(t -> Objects.equals(t.getUserRole().getId(), assignment.getUserRoleId()));

				if (!existsToday) {
					removeRole(userMap, userRoleMap, toRemoveMap, toRemoveUserRoleMap, domainAndUserId, assignment, toDelete);
				}
			}
		}

		// Delete all removed assignments after iteration
		manualAssignmentNotificationMapDao.deleteAll(toDelete);
	}

	private static void removeRole(Map<String, User> userMap, Map<Long, UserRole> userRoleMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap, Map<UserRole, List<User>> toRemoveUserRoleMap, String domainAndUserId, ManualAssignmentNotificationMap assignment, List<ManualAssignmentNotificationMap> toDelete) {
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

		toDelete.add(assignment);
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

	private @NotNull StringBuilder formatMessageForItSystem(
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap,
		Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap) {

		StringBuilder usersAndRoles = new StringBuilder();

		// Users with added roles (and possibly removed)
		for (UserInformationDTO dto : toAddMap.keySet()) {
			if (!toAddMap.get(dto).isEmpty() || Objects.nonNull(toRemoveMap.get(dto))) {
				appendUserWithRoleChanges(usersAndRoles, dto,
					toAddMap.get(dto), toRemoveMap.get(dto));
			}
		}

		// Users with only removed roles
		for (UserInformationDTO dto : toRemoveMap.keySet()) {
			if (toAddMap.get(dto) == null) {
				appendUserWithRoleChanges(usersAndRoles, dto,
					Collections.emptyList(), toRemoveMap.get(dto));
			}
		}

		return usersAndRoles;
	}

	private void appendUserWithRoleChanges(StringBuilder usersAndRoles,
										   UserInformationDTO userInfo,
										   List<UserRoleInformationDTO> addedRoles,
										   List<UserRoleInformationDTO> removedRoles) {

		usersAndRoles.append(messageSource.getMessage(
			"html.email.manual.message.user",
			new Object[] { userInfo.user().getName(), userInfo.user().getUserId(),
				userInfo.user().getExtUuid(), userInfo.ouName() },
			Locale.ENGLISH));
		usersAndRoles.append("<ul>");

		// Append added roles
		for (UserRoleInformationDTO addRoleDto : addedRoles) {
			usersAndRoles.append(messageSource.getMessage(
				"html.email.manual.message.addRole",
				new Object[] { addRoleDto.userRole().getName(),
					addRoleDto.userRole().getDescription(),
					addRoleDto.responsible() },
				Locale.ENGLISH));

			if (addRoleDto.userRole().isRequireManagerAction()) {
				notifyManagerActionRequired(userInfo.user(), addRoleDto.userRole());
			}
		}

		// Append removed roles
		if (removedRoles != null) {
			for (UserRoleInformationDTO removeRoleDto : removedRoles) {
				usersAndRoles.append(messageSource.getMessage(
					"html.email.manual.message.removeRole",
					new Object[] { removeRoleDto.userRole().getName(),
						removeRoleDto.userRole().getDescription(),
						removeRoleDto.responsible() },
					Locale.ENGLISH));
			}
		}

		usersAndRoles.append("</ul>");
	}

	private void sendNotification(String system, StringBuilder message, String email) {
		String title = messageSource.getMessage("html.email.manual.title", new Object[] { system }, Locale.ENGLISH);
		String formatMessage = messageSource.getMessage("html.email.manual.message.format", new Object[] { system, message.toString() }, Locale.ENGLISH);

		if (!formatMessage.isEmpty()) {

			try {
				emailService.sendMessage(email, title, formatMessage, null);
			}
			catch (Exception ex) {
				log.error("Exception occured while synchronizing manual ItSystem: " + system + ". Could not send email to: " + email + ". Exception:" + ex.getMessage());
				// we just continue with the next one - someone has to fix this, and then perform a full sync
			}
		}
	}

	private @NotNull StringBuilder formatMessageForRoleEmail(UserRole specificUserRole, List<User> addedUsers, List<User> removedUsers) {
		StringBuilder roleAndUsers = new StringBuilder();
		if (specificUserRole != null) {
			roleAndUsers.append(messageSource.getMessage("html.email.manual.message.userRole", new Object[] { specificUserRole.getName(), specificUserRole.getDescription() }, Locale.ENGLISH));
			roleAndUsers.append("<ul>");

			for (User user : addedUsers) {
				roleAndUsers.append(messageSource.getMessage("html.email.manual.message.addUser", new Object[] { user.getName(), user.getUserId(), user.getExtUuid() }, Locale.ENGLISH));
			}
			// We try to build the entire list including removed ones in one go, in case the same User role has removed and added users
			if (removedUsers != null && !removedUsers.isEmpty()) {
				for (User user : removedUsers) {
					roleAndUsers.append(messageSource.getMessage("html.email.manual.message.removeUser", new Object[] { user.getName(), user.getUserId(), user.getExtUuid() }, Locale.ENGLISH));
				}
			}
			roleAndUsers.append("</ul>");
		}

		if (removedUsers != null && !removedUsers.isEmpty()) {
			// List has already been properly created, so we return it
			if (addedUsers != null && !addedUsers.isEmpty()) {
				return roleAndUsers;
			}
			roleAndUsers.append(messageSource.getMessage("html.email.manual.message.userRole", new Object[] { specificUserRole.getName(), specificUserRole.getDescription() }, Locale.ENGLISH));
			roleAndUsers.append("<ul>");

			for (User user : removedUsers) {
				roleAndUsers.append(messageSource.getMessage("html.email.manual.message.removeUser", new Object[] { user.getName(), user.getUserId(), user.getExtUuid() }, Locale.ENGLISH));
			}
			roleAndUsers.append("</ul>");
		}
		return roleAndUsers;
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

	private boolean existsInItSystem(String[] itSystemEmails, String email) {
		if (itSystemEmails == null) {
			return false;
		}
		boolean result = false;
		for (String itSystemEmail : itSystemEmails) {
			if (itSystemEmail.equals(email)) {
				result = true;
				break;
			}
		}
		return result;
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
