package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.controller.mvc.viewmodel.InlineImageDTO;
import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
		Map<String, List<HistoryOURoleAssignmentWithExceptions>> ouRoleAssignmentsWithExceptions = (Map<String, List<HistoryOURoleAssignmentWithExceptions>>) model.get("ouRoleAssignmentsWithExceptions");
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments = (Map<String, List<HistoryRoleAssignment>>) model.get("userRoleAssignments");
		Map<String, List<HistoryOURoleAssignmentWithTitles>> titleRoleAssignments = (Map<String, List<HistoryOURoleAssignmentWithTitles>>) model.get("titleRoleAssignments");

		Map<Long, String> itSystemNameMapping = new HashMap<>();
        for (HistoryItSystem itSystem : itSystems) {
            for (HistoryUserRole userRole : itSystem.getHistoryUserRoles()) {
                itSystemNameMapping.put(userRole.getUserRoleId(), userRole.getUserRoleName());
            }
        }

		return reportService.getUserRoleAssignmentReportEntries(users, orgUnits, itSystems, userRoleAssignments, ouRoleAssignments, ouRoleAssignmentsWithExceptions, titleRoleAssignments, itSystemNameMapping, Locale.ENGLISH, false);
	}

	@Transactional
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

		// TODO: this will fail if there are multiple users with the same userId (and since we added domains, that is possible), so
		// we need to do some refactoring on this entire feature *sigh*
		Map<String, User> userMap = userService.getAll().stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
		
		List<ItSystem> itSystems = itSystemService.getBySystemTypeIn(Arrays.asList(ItSystemType.MANUAL, ItSystemType.AD, ItSystemType.SAML));
		for (ItSystem itSystem : itSystems) {
			log.info("Detecting role changes on " + itSystem.getName() + " / " + itSystem.getId());

			String emailAddressesString = itSystem.getEmail();
			if (!StringUtils.hasLength(emailAddressesString)) {
				log.debug("No email address configured for " + itSystem.getName() + " / " + itSystem.getId());
				continue;
			}
			String[] emailAddresses = emailAddressesString.split(";");

			Map<Long, UserRole> userRoleMap = userRoleService.getByItSystem(itSystem).stream().collect(Collectors.toMap(UserRole::getId, Function.identity()));
			Map<User, List<UserRole>> toAddMap = new HashMap<>();
			Map<User, List<UserRole>> toRemoveMap = new HashMap<>();
			
			List<UserRoleAssignmentReportEntry> todayAssignments = getHistoricalAssignments(LocalDate.now(), itSystem.getId());
			List<UserRoleAssignmentReportEntry> yesterdayAssignments = getHistoricalAssignments(LocalDate.now().minusDays(1L), itSystem.getId());

			// should filter out duplicates and then compare - maybe collect on userUuid first
			Map<String, List<UserRoleAssignmentReportEntry>> todayAssignmentsMap = todayAssignments.stream().collect(Collectors.groupingBy(UserRoleAssignmentReportEntry::getUserId));
			Map<String, List<UserRoleAssignmentReportEntry>> yesterdayAssignmentsMap = yesterdayAssignments.stream().collect(Collectors.groupingBy(UserRoleAssignmentReportEntry::getUserId));
			
			// find added roles
			for (String userId : todayAssignmentsMap.keySet()) {
				List<UserRoleAssignmentReportEntry> todayAssignmentsForUser = todayAssignmentsMap.get(userId);
				List<UserRoleAssignmentReportEntry> yesterdayAssignmentsForUser = yesterdayAssignmentsMap.get(userId);
				if (yesterdayAssignmentsForUser == null) {
					yesterdayAssignmentsForUser = new ArrayList<>();
				}
				
				// filter duplicate assignments
				todayAssignmentsForUser = todayAssignmentsForUser.stream().filter(StreamExtensions.distinctByKey(a -> a.getRoleId())).collect(Collectors.toList());
				
				// compare with yesterday
				for (UserRoleAssignmentReportEntry assignment : todayAssignmentsForUser) {
					boolean existedYesterday = yesterdayAssignmentsForUser.stream().anyMatch(a -> a.getRoleId() == assignment.getRoleId());

					if (!existedYesterday) {
						log.info("role " + assignment.getRoleId() + " has been assigned to " + userId);
						
						UserRole userRole = userRoleMap.get(assignment.getRoleId());
						if (userRole == null) {
							log.warn("Unknown userRole: " + assignment.getRoleId());
							continue;
						}

						User user = userMap.get(userId);
						if (user == null) {
							log.warn("Unknown user: " + userId);
							continue;
						}

						List<UserRole> usersRoles = toAddMap.get(user);
						if (usersRoles == null) {
							usersRoles = new ArrayList<UserRole>();
							toAddMap.put(user, usersRoles);
						}
						
						usersRoles.add(userRole);
					}
				}
			}
			
			// find removed roles
			for (String userId : yesterdayAssignmentsMap.keySet()) {
				List<UserRoleAssignmentReportEntry> yesterdayAssignmentsForUser = yesterdayAssignmentsMap.get(userId);
				List<UserRoleAssignmentReportEntry> todayAssignmentsForUser = todayAssignmentsMap.get(userId);
				if (todayAssignmentsForUser == null) {
					todayAssignmentsForUser = new ArrayList<>();
				}
				
				// filter duplicate assignments
				yesterdayAssignmentsForUser = yesterdayAssignmentsForUser.stream().filter(StreamExtensions.distinctByKey(a -> a.getRoleId())).collect(Collectors.toList());
				
				// compare with today
				for (UserRoleAssignmentReportEntry assignment : yesterdayAssignmentsForUser) {
					boolean existsToday = todayAssignmentsForUser.stream().anyMatch(a -> a.getRoleId() == assignment.getRoleId());

					if (!existsToday) {
						log.info("role " + assignment.getRoleId() + " has been removed from " + userId);
						
						UserRole userRole = userRoleMap.get(assignment.getRoleId());
						if (userRole == null) {
							log.warn("Unknown userRole: " + assignment.getRoleId());
							continue;
						}

						User user = userMap.get(userId);
						if (user == null) {
							log.warn("Unknown user: " + userId);
							continue;
						}

						List<UserRole> usersRoles = toRemoveMap.get(user);
						if (usersRoles == null) {
							usersRoles = new ArrayList<UserRole>();
							toRemoveMap.put(user, usersRoles);
						}
						
						usersRoles.add(userRole);
					}
				}
			}

			if (toAddMap.size() > 0 || toRemoveMap.size() > 0) {
				StringBuilder usersAndRoles = new StringBuilder();

				for (User user : toAddMap.keySet()) {
					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { user.getName(), user.getUserId(), user.getExtUuid() }, Locale.ENGLISH));
					usersAndRoles.append("<ul>");
					List<UserRole> toAdd = toAddMap.get(user);
					List<UserRole> toRemove = toRemoveMap.get(user);
					
					for (UserRole userRole : toAdd) {
						usersAndRoles.append(messageSource.getMessage("html.email.manual.message.addRole", new Object[] { userRole.getName() }, Locale.ENGLISH));
					
						// handle manager action required email template
						if (userRole.isRequireManagerAction()) {
							notifyManagerActionRequired(user, userRole);
						}					
					}
					
					if (toRemove != null) {
						for (UserRole userRole : toRemove) {
							usersAndRoles.append(messageSource.getMessage("html.email.manual.message.removeRole", new Object[] { userRole.getName() }, Locale.ENGLISH));
						}
					}

					usersAndRoles.append("</ul>");
				}
				
				for (User user : toRemoveMap.keySet()) {
					List<UserRole> toAdd = toAddMap.get(user);
					if (toAdd != null) {
						continue; // already taken care of above
					}

					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { user.getName(), user.getUserId(), user.getExtUuid() }, Locale.ENGLISH));
					usersAndRoles.append("<ul>");

					List<UserRole> toRemove = toRemoveMap.get(user);
					for (UserRole userRole : toRemove) {
						usersAndRoles.append(messageSource.getMessage("html.email.manual.message.removeRole", new Object[] { userRole.getName() }, Locale.ENGLISH));
					}

					usersAndRoles.append("</ul>");
				}

				String title = messageSource.getMessage("html.email.manual.title", new Object[] { itSystem.getName() }, Locale.ENGLISH);
				String message = messageSource.getMessage("html.email.manual.message.format", new Object[] { itSystem.getName(), usersAndRoles.toString() }, Locale.ENGLISH);

				for (String email : emailAddresses) {
					try {
						emailService.sendMessage(email, title, message);
					}
					catch (Exception ex) {
						log.error("Exception occured while synchronizing manual ItSystem: " + itSystem + ". Could not send email to: " + email + ". Exception:" + ex.getMessage());

						// we just continue with the next one - someone has to fix this, and then perform a full sync
					}
				}
			}
		}
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
			
			emailService.sendMessage(recipientMails, title, message, inlineImages);
			
			// we need to set the original message again because for some reason the template is saved to the db
			template.setMessage(orgMessage);
		}
		else {
			log.warn("Could not notify manager on assignment of " + userRole.getId() + " to user " + user.getUserId() + " because no managers, substitutes or authorizationManagers where available or had email adresses");
		}
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
