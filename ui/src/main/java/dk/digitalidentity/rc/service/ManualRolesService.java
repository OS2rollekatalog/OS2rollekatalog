package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserRoleEmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentReportEntry;
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

import java.time.LocalDate;
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
	private ReportService reportService;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

	@SuppressWarnings("unchecked")
	private List<UserRoleAssignmentReportEntry> getHistoricalAssignments(LocalDate date, long itSystemId) {
		ReportForm reportForm = new ReportForm();
		reportForm.setDate(date.toString());
		reportForm.setItSystems(new ArrayList<Long>());
		reportForm.getItSystems().add(itSystemId);

		Map<String, Object> model = reportService.getReportModel(reportForm, Locale.ENGLISH);

		Map<String, HistoryUser> users = (Map<String, HistoryUser>) model.get("users");
		Map<String, HistoryOU> orgUnits = (Map<String, HistoryOU>) model.get("orgUnits");
		List<HistoryItSystem> itSystems = (List<HistoryItSystem>) model.get("itSystems");
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments = (Map<String, List<HistoryOURoleAssignment>>) model.get("ouRoleAssignments");
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments = (Map<String, List<HistoryRoleAssignment>>) model.get("userRoleAssignments");

		Map<Long, String> itSystemNameMapping = new HashMap<>();
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                itSystemNameMapping.put(userRole.getUserRoleId(), userRole.getUserRoleName());
            }
        }

		return reportService.getUserRoleAssignmentReportEntries(users, orgUnits, itSystems, userRoleAssignments, ouRoleAssignments, itSystemNameMapping, Locale.ENGLISH, false, false);
	}

	public record UserInformationDTO(User user, String ouName) {}
	public record UserRoleInformationDTO(UserRole userRole, String responsible) {}

	@Transactional(readOnly = true)
	public void notifyServicedesk() {
		// Fail-safe: In case there is no history record for today or yesterday, log an error and do nothing.
		final LocalDate now = LocalDate.now();
		final LocalDate yesterday = now.minusDays(1L);
		if (!historyService.hasHistoryBeenGenerated(now)) {
			log.error("Not notifying service desk, history missing for " + now);
			return;
		}
		if (!historyService.hasHistoryBeenGenerated(yesterday)) {
			log.error("Not notifying service desk, history missing for " + yesterday);
			return;
		}

		Map<String, User> userMap = userService.getAll().stream().collect(Collectors.toMap(u -> u.getDomain().getId() + "!" + u.getUserId(), Function.identity()));

		List<ItSystem> itSystems = itSystemService.getBySystemTypeIn(Arrays.asList(ItSystemType.MANUAL, ItSystemType.AD, ItSystemType.SAML, ItSystemType.KOMBIT));
		for (ItSystem itSystem : itSystems) {
			log.info("Detecting role changes on " + itSystem.getName() + " / " + itSystem.getId());

			Map<Long, UserRole> userRoleMap = userRoleService.getByItSystem(itSystem).stream().collect(Collectors.toMap(UserRole::getId, Function.identity()));
			Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap = new HashMap<>();
			Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap = new HashMap<>();
			Map<UserRole, List<User>> toAddUserRoleMap = new HashMap<>();
			Map<UserRole, List<User>> toRemoveUserRoleMap = new HashMap<>();

			List<UserRoleAssignmentReportEntry> todayAssignments = getHistoricalAssignments(LocalDate.now(), itSystem.getId());
			List<UserRoleAssignmentReportEntry> yesterdayAssignments = getHistoricalAssignments(LocalDate.now().minusDays(1L), itSystem.getId());

			// should filter out duplicates and then compare - maybe collect on userUuid first
			Map<String, List<UserRoleAssignmentReportEntry>> todayAssignmentsMap = todayAssignments.stream().collect(Collectors.groupingBy(u -> u.getDomainId() + "!" + u.getUserId()));
			Map<String, List<UserRoleAssignmentReportEntry>> yesterdayAssignmentsMap = yesterdayAssignments.stream().collect(Collectors.groupingBy(u -> u.getDomainId() + "!" + u.getUserId()));

			// find added roles
			for (String domainAndUserId : todayAssignmentsMap.keySet()) {
				List<UserRoleAssignmentReportEntry> todayAssignmentsForUser = todayAssignmentsMap.get(domainAndUserId);
				List<UserRoleAssignmentReportEntry> yesterdayAssignmentsForUser = yesterdayAssignmentsMap.get(domainAndUserId);
				if (yesterdayAssignmentsForUser == null) {
					yesterdayAssignmentsForUser = new ArrayList<>();
				}

				// filter duplicate assignments
				todayAssignmentsForUser = todayAssignmentsForUser.stream().filter(StreamExtensions.distinctByKey(a -> a.getRoleId())).collect(Collectors.toList());

				// compare with yesterday
				for (UserRoleAssignmentReportEntry assignment : todayAssignmentsForUser) {
					boolean existedYesterday = yesterdayAssignmentsForUser.stream().anyMatch(a -> a.getRoleId() == assignment.getRoleId());

					if (!existedYesterday) {

						UserRole userRole = userRoleMap.get(assignment.getRoleId());
						if (userRole == null) {
							log.warn("Unknown userRole: " + assignment.getRoleId());
							continue;
						}

						User user = userMap.get(domainAndUserId);
						if (user == null) {
							log.warn("Unknown user: " + domainAndUserId);
							continue;
						}

						String infoMsg = "role " + assignment.getRoleId() + " has been assigned to " + domainAndUserId;
						if (!assignment.isNotifyByEmailIfManualSystem()) {
							infoMsg += " and email has been opted out of.";
							log.debug(infoMsg);
							if (userRole.isRequireManagerAction()) {
								notifyManagerActionRequired(user, userRole);
							}
							continue;
						}
						UserInformationDTO userInfo = new UserInformationDTO(user, assignment.getOrgUnitName());
						UserRoleInformationDTO userRoleInformationDTO = new UserRoleInformationDTO(userRole, assignment.getAssignedBy());
						List<UserRoleInformationDTO> usersRoles = toAddMap.computeIfAbsent(userInfo, k -> new ArrayList<>());
						log.info(infoMsg);
						usersRoles.add(userRoleInformationDTO);
						toAddUserRoleMap.computeIfAbsent(userRole, k -> new ArrayList<>()).add(user);
					}
				}
			}

			// find removed roles
			for (String domainAndUserId : yesterdayAssignmentsMap.keySet()) {
				List<UserRoleAssignmentReportEntry> yesterdayAssignmentsForUser = yesterdayAssignmentsMap.get(domainAndUserId);
				List<UserRoleAssignmentReportEntry> todayAssignmentsForUser = todayAssignmentsMap.get(domainAndUserId);
				if (todayAssignmentsForUser == null) {
					todayAssignmentsForUser = new ArrayList<>();
				}

				// filter duplicate assignments
				yesterdayAssignmentsForUser = yesterdayAssignmentsForUser.stream().filter(StreamExtensions.distinctByKey(UserRoleAssignmentReportEntry::getRoleId)).toList();

				// compare with today
				for (UserRoleAssignmentReportEntry assignment : yesterdayAssignmentsForUser) {
					boolean existsToday = todayAssignmentsForUser.stream().anyMatch(a -> a.getRoleId() == assignment.getRoleId());

					if (!existsToday) {
						log.info("role " + assignment.getRoleId() + " has been removed from " + domainAndUserId);

						UserRole userRole = userRoleMap.get(assignment.getRoleId());
						if (userRole == null) {
							log.warn("Unknown userRole: " + assignment.getRoleId());
							continue;
						}

						User user = userMap.get(domainAndUserId);
						if (user == null) {
							log.warn("Unknown user: " + domainAndUserId);
							continue;
						}
						UserInformationDTO userInfo = new UserInformationDTO(user, assignment.getOrgUnitName() != null ? assignment.getOrgUnitName() : "");

						List<UserRoleInformationDTO> usersRoles = toRemoveMap.computeIfAbsent(userInfo, k -> new ArrayList<>());
						UserRoleInformationDTO userRoleInformationDTO = new UserRoleInformationDTO(userRole, assignment.getAssignedBy());
						usersRoles.add(userRoleInformationDTO);
						toRemoveUserRoleMap.computeIfAbsent(userRole, k -> new ArrayList<>()).add(user);
					}
				}
			}
			String[] itEmailAddresses = null;
			if (StringUtils.hasLength(itSystem.getEmail())) {
				itEmailAddresses = itSystem.getEmail().split(";");
			}
			if (!toAddMap.isEmpty() || !toRemoveMap.isEmpty()) {
				StringBuilder usersAndRoles = new StringBuilder();
				// Format message and send it
				if (!toAddMap.isEmpty() || !toRemoveMap.isEmpty() || !toAddUserRoleMap.isEmpty() || !toRemoveUserRoleMap.isEmpty()) {
					Set<UserRole> allUserRoles = new HashSet<>();
					allUserRoles.addAll(toAddUserRoleMap.keySet());
					allUserRoles.addAll(toRemoveUserRoleMap.keySet());

					for (UserRole userRole : allUserRoles) {
						if (userRole.getContactEmail() != null) {
							List<User> addedUsers = toAddUserRoleMap.getOrDefault(userRole, Collections.emptyList());
							List<User> removedUsers = toRemoveUserRoleMap.getOrDefault(userRole, Collections.emptyList());

							// Skip if both are empty
							if (addedUsers.isEmpty() && removedUsers.isEmpty()) {
								continue;
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
					}

					if (itEmailAddresses != null && itEmailAddresses.length > 0) {
						StringBuilder usrAndRls = formatMessageForItSystem(toAddMap, toRemoveMap);
						for (String itEmailAddress : itEmailAddresses) {
							sendNotification(itSystem.getName(), usrAndRls, itEmailAddress);
						}
					}
				}
			}
		}
	}

	private @NotNull StringBuilder formatMessageForItSystem(Map<UserInformationDTO, List<UserRoleInformationDTO>> toAddMap, Map<UserInformationDTO, List<UserRoleInformationDTO>> toRemoveMap) {
		StringBuilder usersAndRoles = new StringBuilder();

				for (UserInformationDTO dto : toAddMap.keySet()) {
					if(!toAddMap.get(dto).isEmpty() || Objects.nonNull(toRemoveMap.get(dto))) {

						usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { dto.user.getName(), dto.user.getUserId(), dto.user.getExtUuid(), dto.ouName}, Locale.ENGLISH));
						usersAndRoles.append("<ul>");
						List<UserRoleInformationDTO> toAdd = toAddMap.get(dto);
						List<UserRoleInformationDTO> toRemove = toRemoveMap.get(dto);

						for (UserRoleInformationDTO addRoleDto : toAdd) {
							usersAndRoles.append(messageSource.getMessage("html.email.manual.message.addRole", new Object[] { addRoleDto.userRole.getName(), addRoleDto.userRole.getDescription(), addRoleDto.responsible }, Locale.ENGLISH));

							// handle manager action required email template
							if (addRoleDto.userRole.isRequireManagerAction()) {
								notifyManagerActionRequired(dto.user, addRoleDto.userRole);
							}
						}

						if (toRemove != null) {
							for (UserRoleInformationDTO removeRoleDto : toRemove) {
								usersAndRoles.append(messageSource.getMessage("html.email.manual.message.removeRole", new Object[] { removeRoleDto.userRole.getName(), removeRoleDto.userRole.getDescription(), removeRoleDto.responsible }, Locale.ENGLISH));
							}
						}

						usersAndRoles.append("</ul>");
					}
				}

				for (UserInformationDTO toRemoveDto : toRemoveMap.keySet()) {
					List<UserRoleInformationDTO> toAdd = toAddMap.get(toRemoveDto);
					if (toAdd != null) {
						continue; // already taken care of above
					}

					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { toRemoveDto.user.getName(), toRemoveDto.user.getUserId(), toRemoveDto.user.getExtUuid(), toRemoveDto.ouName }, Locale.ENGLISH));
					usersAndRoles.append("<ul>");

					List<UserRoleInformationDTO> toRemove = toRemoveMap.get(toRemoveDto);
					for (UserRoleInformationDTO removedRoleDto : toRemove) {
						usersAndRoles.append(messageSource.getMessage("html.email.manual.message.removeRole", new Object[] { removedRoleDto.userRole.getName(), removedRoleDto.userRole.getDescription(), removedRoleDto.responsible }, Locale.ENGLISH));
					}

			usersAndRoles.append("</ul>");
		}
		return usersAndRoles;
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
				.map(p -> p.getOrgUnit())
				.collect(Collectors.toSet());

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
						.map(a -> a.getUser())
						.filter(u -> StringUtils.hasLength(u.getEmail()))
						.collect(Collectors.toSet()));
			}
		}

		String recipientNames = recipients.stream().map(r -> r.getName()).collect(Collectors.joining(", "));
		String recipientMails = recipients.stream().map(r -> r.getEmail()).collect(Collectors.joining(","));

		if (!recipientMails.isBlank()) {
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
		else {
			log.warn("Could not notify manager on assignment of " + userRole.getId() + " to user " + user.getUserId() + " because no managers, substitutes or authorizationManagers where available or had email adresses");
		}
	}

	private boolean existsInItSystem(String[] itSystemEmails, String email) {
		boolean result = false;
		for (String itSystemEmail : itSystemEmails) {
			if (itSystemEmail.equals(email)) {
				result = true;
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
			if (src == null || src == "") {
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
