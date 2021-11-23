package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentReportEntry;
import lombok.extern.slf4j.Slf4j;

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

	// TODO: we have this a few places - put it into a utility class so we can static import it in all places
	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Map<Object, Boolean> seen = new ConcurrentHashMap<>();
		return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}

	@Transactional
	public void notifyServicedesk() {
		Map<String, User> userMap = userService.getAll().stream().collect(Collectors.toMap(User::getUserId, Function.identity()));
		
		List<ItSystem> itSystems = itSystemService.getBySystemType(ItSystemType.MANUAL);
		for (ItSystem itSystem : itSystems) {
			log.info("Detecting role changes on " + itSystem.getName() + " / " + itSystem.getId());

			String emailAddress = itSystem.getEmail();
			if (StringUtils.isEmpty(emailAddress)) {
				log.info("No email address configured for " + itSystem.getName() + " / " + itSystem.getId());
				continue;
			}

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
				todayAssignmentsForUser = todayAssignmentsForUser.stream().filter(distinctByKey(a -> a.getRoleId())).collect(Collectors.toList());
				
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
				yesterdayAssignmentsForUser = yesterdayAssignmentsForUser.stream().filter(distinctByKey(a -> a.getRoleId())).collect(Collectors.toList());
				
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
					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { (user.getName() + " (" + user.getUserId() + ")") }, Locale.ENGLISH));
					usersAndRoles.append("<ul>");
					List<UserRole> toAdd = toAddMap.get(user);
					List<UserRole> toRemove = toRemoveMap.get(user);
					
					for (UserRole userRole : toAdd) {
						usersAndRoles.append(messageSource.getMessage("html.email.manual.message.addRole", new Object[] { userRole.getName() }, Locale.ENGLISH));
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

					usersAndRoles.append(messageSource.getMessage("html.email.manual.message.user", new Object[] { (user.getName() + " (" + user.getUserId() + ")") }, Locale.ENGLISH));
					usersAndRoles.append("<ul>");

					List<UserRole> toRemove = toRemoveMap.get(user);
					for (UserRole userRole : toRemove) {
						usersAndRoles.append(messageSource.getMessage("html.email.manual.message.removeRole", new Object[] { userRole.getName() }, Locale.ENGLISH));
					}

					usersAndRoles.append("</ul>");
				}

				String title = messageSource.getMessage("html.email.manual.title", new Object[] { itSystem.getName() }, Locale.ENGLISH);
				String message = messageSource.getMessage("html.email.manual.message.format", new Object[] { itSystem.getName(), usersAndRoles.toString() }, Locale.ENGLISH);

				try {
					emailService.sendMessage(emailAddress, title, message);
				}
				catch (Exception ex) {
					log.error("Exception occured while synchronizing manual ItSystem: " + itSystem + " Exception:" + ex.getMessage());

					// we just continue with the next one - someone has to fix this, and then perform a full sync
				}
			}
		}
	}
}
