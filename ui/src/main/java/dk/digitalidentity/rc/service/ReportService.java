package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.history.model.HistoryFunction;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;
import dk.digitalidentity.rc.service.assignment.HistoricAssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricExceptedAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.history.model.GenericRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistorySystemRole;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.model.UserRoleAssignmentReportEntry;
import dk.digitalidentity.rc.util.OrganisationConstraintUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReportService {

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private HistoryService historyService;

	@Autowired
	private ItSystemService itSytemService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private OrgUnitDao orgUnitDao;

	@Autowired
    private OrganisationConstraintUtil organisationConstraintUtil;

	@Autowired
	private HistoricAssignmentService historicAssignmentService;

	@Autowired
	private HistoricExceptedAssignmentService historicExceptedAssignmentService;

	public List<UserRoleAssignmentReportEntry> getUserRoleAssignmentReportEntries(
		Map<String, HistoryUser> users,
		Map<String, HistoryOU> orgUnits,
		List<HistoryItSystem> itSystems,
		List<HistoricAssignment> filteredHistoricAssignments,
		Locale locale,
		boolean showInactiveUsers,
		boolean onlyResponsibleOU) {

		List<UserRoleAssignmentReportEntry> result = new ArrayList<>();

		for (HistoricAssignment ha : filteredHistoricAssignments) {
			// Get user details
			HistoryUser user = users.get(ha.getUserUuid());
			if (user == null) {
				log.warn("User not found for assignment: {}", ha.getUserUuid());
				continue;
			}

			// Skip inactive users if showInactiveUsers = false
			if (!showInactiveUsers && !user.isUserActive()) {
				continue;
			}

			if (onlyResponsibleOU) {
				// Only show with responsible OU
				createReportEntry(ha, user, orgUnits, locale, result);
			} else {
				// Show assignment for each position the user has
				List<HistoryOU> positions = getPositions(orgUnits, user);

				if (positions.isEmpty()) {
					// User has no positions, still show with responsible OU
					createReportEntry(ha, user, orgUnits, locale, result);
				} else {
					// Create an entry for each position
					for (HistoryOU position : positions) {
						createReportEntry(ha, user, position, locale, result);
					}
				}
			}
		}

		ReportSystemRoleWeightService.addSystemRoleWeights(itSystems, result);
		return result;
	}

	private void createReportEntry(
		HistoricAssignment ha,
		HistoryUser user,
		Map<String, HistoryOU> orgUnits,
		Locale locale,
		List<UserRoleAssignmentReportEntry> result) {

		// Use responsible OU from the assignment
		String orgUnitName = ha.getResponsibleOUName();
		String orgUnitUUID = ha.getResponsibleOUUuid();

		if (ha.getResponsibleOUUuid() != null) {
			HistoryOU ou = orgUnits.get(ha.getResponsibleOUUuid());
			if (ou != null) {
				orgUnitName = ou.getOuName();
				orgUnitUUID = ou.getOuUuid();
			}
		}

		createReportEntryInternal(ha, user, orgUnitName, orgUnitUUID, locale, result);
	}

	private void createReportEntry(
		HistoricAssignment ha,
		HistoryUser user,
		HistoryOU position,
		Locale locale,
		List<UserRoleAssignmentReportEntry> result) {

		// Use the position OU instead of responsible OU
		createReportEntryInternal(ha, user, position.getOuName(), position.getOuUuid(), locale, result);
	}

	private void createReportEntryInternal(
		HistoricAssignment historicAssignment,
		HistoryUser user,
		String orgUnitName,
		String orgUnitUUID,
		Locale locale,
		List<UserRoleAssignmentReportEntry> result) {

		// Build assignedThrough string
		String assignedThroughStr = buildAssignedThroughString(historicAssignment, locale);

		// Build postponedConstraints string
		String postponedConstraints = buildPostponedConstraintsString(historicAssignment);

		// Create entry
		UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
		row.setDomainId(user.getDomainId());
		row.setUserName(user.getUserName());
		row.setUserId(user.getUserUserId());
		row.setEmployeeId(user.getUserExtUuid());
		row.setOrgUnitName(orgUnitName != null ? orgUnitName : "<ukendt enhed>");
		row.setOrgUnitUUID(orgUnitUUID != null ? orgUnitUUID : "");
		row.setUserActive(user.isUserActive());
		row.setRoleId(historicAssignment.getUserRoleId());
		row.setItSystem(historicAssignment.getItSystemName());
		row.setAssignedBy(historicAssignment.getAssignedBy());
		row.setAssignedWhen(historicAssignment.getValidFrom());
		row.setAssignedThrough(assignedThroughStr);
		row.setPostponedConstraints(postponedConstraints);
		row.setStartDate(historicAssignment.getStartDate());
		row.setStopDate(historicAssignment.getStopDate());

		result.add(row);
	}

	private String buildAssignedThroughString(HistoricAssignment historicAssignment, Locale locale) {
		String assignedThroughMessageCode = switch (historicAssignment.getAssignedThroughType()) {
			case DIRECT -> "xls.role.assigned.trough.type.direct";
			case ROLEGROUP -> "xls.role.assigned.trough.type.direct_group";
			case POSITION -> /*deprecated in future*/ "xls.role.assigned.trough.type.position";
			case ORGUNIT -> "xls.role.assigned.trough.type.orgunit";
			case TITLE -> "xls.role.assigned.trough.type.title";
		};

		String assignedThroughStr = messageSource.getMessage(assignedThroughMessageCode, null, locale);

		// Add the name of what it was assigned through
		String throughName = null;
		switch (historicAssignment.getAssignedThroughType()) {
			case ORGUNIT:
				throughName = historicAssignment.getAssignedThroughOUName();
				break;
			case TITLE:
				throughName = historicAssignment.getAssignedThroughTitleName();
				if (historicAssignment.getAssignedThroughOUName() != null) {
					throughName = historicAssignment.getAssignedThroughTitleName() + " (" + historicAssignment.getAssignedThroughOUName() + ")";
				}
				break;
			case ROLEGROUP:
				throughName = historicAssignment.getAssignedThroughRoleGroupName();
				break;
			case DIRECT: 
				// no assigned through name
				break;
			case POSITION:
				// not possible anymore
				break;
		}

		if (StringUtils.hasLength(throughName)) {
			assignedThroughStr += ": " + throughName;
		}

		return assignedThroughStr;
	}

	private String buildPostponedConstraintsString(HistoricAssignment ha) {
		if (ha.getConstraints() == null || ha.getConstraints().isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (HistoricAssignmentConstraint constraint : ha.getConstraints()) {
			String constraintName = constraint.getConstraintTypeName();
			List<String> constraintValues = constraint.getValue();

			if (constraintValues == null || constraintValues.isEmpty()) {
				// Constraint without value - just show name
				sb.append(constraintName).append("\n");
				continue;
			}

			for (String constraintValue : constraintValues) {
				// Check if it's an organisation constraint (UUID format)
				if (constraintValue != null &&
					constraintValue.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {

					OrgUnit orgUnit = orgUnitDao.findById(constraintValue).orElse(null);
					if (orgUnit != null) {
						sb.append(constraintName).append(": ").append(orgUnit.getName()).append("\n");
					} else {
						// If OU not found, use UUID as fallback
						sb.append(constraintName).append(": ").append(constraintValue).append("\n");
					}
				} else {
					// For non-organisation constraints, use value as-is
					sb.append(constraintName).append(": ").append(constraintValue).append("\n");
				}
			}
		}

		return sb.toString().trim();
	}

	public List<UserRoleAssignmentReportEntry> getNegativeUserRoleAssignmentReportEntries(
		Map<String, HistoryUser> users,
		Map<String, HistoryOU> orgUnits,
		List<HistoricExceptedAssignment> historicExceptedAssignments,
		Locale locale,
		boolean showInactiveUsers) {

		List<UserRoleAssignmentReportEntry> result = new ArrayList<>();

		for (HistoricExceptedAssignment hea : historicExceptedAssignments) {
			// Get user details
			HistoryUser user = users.get(hea.getExceptionUserUuid());
			if (user == null) {
				log.warn("User not found for excepted assignment: {}", hea.getExceptionUserUuid());
				continue;
			}

			// Skip inactive users if showInactiveUsers = false
			if (!showInactiveUsers && !user.isUserActive()) {
				continue;
			}

			// Build assigned through string
			String assignedThroughStr = buildExceptedAssignmentThroughString(hea, locale);

			// Get OU details
			String orgUnitName = hea.getResponsibleOUName();
			String orgUnitUUID = hea.getResponsibleOUUuid();

			if (hea.getResponsibleOUUuid() != null) {
				HistoryOU ou = orgUnits.get(hea.getResponsibleOUUuid());
				if (ou != null) {
					orgUnitName = ou.getOuName();
					orgUnitUUID = ou.getOuUuid();
				}
			}

			// Create entry
			UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
			row.setDomainId(user.getDomainId());
			row.setUserName(user.getUserName());
			row.setUserId(user.getUserUserId());
			row.setEmployeeId(user.getUserExtUuid());
			row.setOrgUnitName(orgUnitName != null ? orgUnitName : "<ukendt enhed>");
			row.setOrgUnitUUID(orgUnitUUID != null ? orgUnitUUID : "");
			row.setUserActive(user.isUserActive());
			row.setRoleId(hea.getExceptionUserRoleId());
			row.setItSystem(hea.getExceptionItSystemName());
			row.setAssignedBy(hea.getAssignedBy());
			row.setAssignedWhen(hea.getValidFrom());
			row.setAssignedThrough(assignedThroughStr);
			row.setPostponedConstraints(""); // excepted assignments have no constraints
			row.setStartDate(hea.getStartDate());
			row.setStopDate(hea.getStopDate());

			result.add(row);
		}

		return result;
	}

	private String buildExceptedAssignmentThroughString(HistoricExceptedAssignment historicExceptedAssignment, Locale locale) {
		if (StringUtils.hasLength(historicExceptedAssignment.getExceptionTitleName())) {
			// Negative title exception
			String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.negativetitle", null, locale);

			StringBuilder throughName = new StringBuilder();
			throughName.append(historicExceptedAssignment.getExceptionTitleName());

			if (StringUtils.hasLength(historicExceptedAssignment.getExceptionOuName())) {
				throughName.append(" (");
				throughName.append(historicExceptedAssignment.getExceptionOuName());
				throughName.append(")");
			}

			return assignedThroughStr + ": " + throughName;
		} else {
			// Excepted user exception
			String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.negativeUser", null, locale);

			String throughName = historicExceptedAssignment.getExceptionOuName();
			if (!StringUtils.hasLength(throughName)) {
				throughName = "<ukendt enhed>";
			}

			return assignedThroughStr + ": " + throughName;
		}
	}

	private List<HistoryOU> getPositions(Map<String, HistoryOU> orgUnits, HistoryUser user) {
		return orgUnits.values().stream().filter( ou -> ou.getUsers().stream().anyMatch(ouUser -> Objects.equals(ouUser.getUserUuid(), user.getUserUuid()))).collect(Collectors.toList());
	}

	public Map<String, Object> getReportModel(ReportForm reportForm, Locale loc) {
		LocalDate localDate = parseAndHandleFutureDate(reportForm.getDate());
		LocalDate displayDate = LocalDate.parse(reportForm.getDate());
		List<String> ouFilter = reportForm.getOrgUnits();
		List<Long> itSystemFilter = reportForm.getItSystems();
		String manager = reportForm.getManager();

		List<HistoryItSystem> itSystems = historyService.getItSystems(localDate);
		List<HistorySystemRole> systemRoles = new ArrayList<>();
		List<HistoryTitle> titles = historyService.getTitles(localDate);
		List<HistoryFunction> functions = historyService.getFunctions(localDate);
		Map<String, HistoryOU> orgUnits = historyService.getOUs(localDate);
		Map<String, HistoryOU> allOrgUnits = new HashMap<>(orgUnits);
		Map<String, HistoryUser> users = historyService.getUsers(localDate);
		Map<String, List<HistoryKleAssignment>> userKleAssignments = historyService.getKleAssignments(localDate);
		Map<String, List<HistoryOUKleAssignment>> ouKleAssignments = historyService.getOUKleAssignments(localDate);
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments;
		List<HistoricAssignment> historicAssignments;
		List<HistoricExceptedAssignment> historicExceptedAssignments;

		List<HistoryOUUser> historyOUUsers = orgUnits
			.entrySet()
			.stream()
			.flatMap(entry -> entry.getValue().getUsers().stream())
			.collect(Collectors.toList());

		// Filter on ItSystems if specified
		if (itSystemFilter != null && itSystemFilter.size() > 0) {
			itSystems = itSystems
				.stream()
				.filter(itSystem -> itSystemFilter.contains(itSystem.getItSystemId()))
				.collect(Collectors.toList());

			ouRoleAssignments = historyService.getOURoleAssignments(localDate, itSystemFilter);
			historicAssignments = historicAssignmentService.getActiveAtDateAndItSystems(localDate, itSystemFilter);
			historicExceptedAssignments = historicExceptedAssignmentService.getActiveAtDateAndItSystems(localDate, itSystemFilter);
		}
		else {
			ouRoleAssignments = historyService.getOURoleAssignments(localDate);
			historicAssignments = historicAssignmentService.getActiveAtDate(localDate);
			historicExceptedAssignments = historicExceptedAssignmentService.getActiveAtDate(localDate);
		}

		// add systemRoles based on itSystems after filter
		for (HistoryItSystem itSystem : itSystems) {
			systemRoles.addAll(itSystem.getHistorySystemRoles());
		}

		historicAssignments = filterHistoricAssignments(displayDate, historicAssignments);
		historicExceptedAssignments = filterHistoricExceptedAssignments(displayDate, historicExceptedAssignments);
		filterRoleAssignments(displayDate, ouRoleAssignments);

		// Filter on manager if specified
		if (StringUtils.hasLength(manager)) {
			ouFilter = orgUnits
				.entrySet()
				.stream()
				.filter(entry -> Objects.equals(manager, entry.getValue().getOuManagerUuid()))
				.map(entry -> entry.getKey())
				.collect(Collectors.toList());

			if (ouFilter.size() == 0) {
				log.warn("Unknown manager: " + manager);

				ouFilter = null;
				itSystems = new ArrayList<>();
				orgUnits = new HashMap<>();
				users = new HashMap<>();
				userKleAssignments = new HashMap<>();
				ouKleAssignments = new HashMap<>();
				ouRoleAssignments = new HashMap<>();
				historicAssignments = new ArrayList<>();
				historicExceptedAssignments = new ArrayList<>();
			}
		}
		
		// Filter OrgUnits if specified
		if (ouFilter != null && ouFilter.size() > 0) {
			List<String> finalOuFilter = ouFilter;

			// filter the retrieved role assignments and kle assignments
			orgUnits.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));
			ouRoleAssignments.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));

			// Find all users in those filtered orgUnits
			List<HistoryOUUser> ouUsers = orgUnits
				.entrySet()
				.stream()
				.flatMap(entry -> entry.getValue().getUsers().stream())
				.collect(Collectors.toList());

			List<String> userUUIDs = ouUsers
				.stream()
				.map(u -> u.getUserUuid())
				.collect(Collectors.toList());

			// Filter list of retrieved users
			users.keySet().removeIf(k -> !userUUIDs.contains(k));
			userKleAssignments.entrySet().removeIf(e -> !userUUIDs.contains(e.getKey()));

			// Filter historic assignments by user and responsible OU
			historicAssignments = historicAssignments.stream()
				.filter(ha -> userUUIDs.contains(ha.getUserUuid()))
				.filter(ha -> ha.getResponsibleOUUuid() == null || finalOuFilter.contains(ha.getResponsibleOUUuid()))
				.collect(Collectors.toList());

			// Filter historic excepted assignments by user and responsible OU
			historicExceptedAssignments = historicExceptedAssignments.stream()
				.filter(ha -> userUUIDs.contains(ha.getExceptionUserUuid()))
				.filter(ha -> ha.getResponsibleOUUuid() == null || finalOuFilter.contains(ha.getResponsibleOUUuid()))
				.collect(Collectors.toList());
		}

		Map<String, Object> model = new HashMap<>();
		model.put("filterDate", displayDate);
		model.put("itSystems", itSystems);
		model.put("systemRoles", systemRoles);

		model.put("orgUnits", orgUnits);
		model.put("allOrgUnits", allOrgUnits);
		model.put("ouRoleAssignments", ouRoleAssignments);
		model.put("ouKLEAssignments", ouKleAssignments);

		model.put("titles", titles);
		model.put("functions", functions);

		model.put("users", users);
		model.put("historicAssignments", historicAssignments);
		model.put("historicExceptedAssignments", historicExceptedAssignments);
		model.put("userKLEAssignments", userKleAssignments);
		model.put("reportForm", reportForm);
		model.put("reportService", this);
		model.put("itSystemService", itSytemService);
		model.put("orgUnitService", orgUnitService);
		model.put("organisationConstraintUtil", organisationConstraintUtil);

		model.put("ouUsers", historyOUUsers);

		// Locale specific text
		model.put("messagesBundle", messageSource);
		model.put("locale", loc);

		return model;
	}

    private <T> void filterRoleAssignments(LocalDate displayDate, Map<String, List<T>> userRoleAssignments) {
        for (Entry<String, List<T>> entry : userRoleAssignments.entrySet()) {
	        for (Iterator<T> itr = entry.getValue().iterator(); itr.hasNext();) {
	            GenericRoleAssignment historyRoleAssignment = (GenericRoleAssignment) itr.next();
                log.debug("UserRole: {} start: {} stop: {}", historyRoleAssignment.getRoleId(), historyRoleAssignment.getStartDate(), historyRoleAssignment.getStopDate());
                //If the startDate is null means the role is active
                if (historyRoleAssignment.getStartDate() != null) {
                    log.debug("StartDate {} is after SelectedDate: {} => {}", historyRoleAssignment.getStartDate(), displayDate, (historyRoleAssignment.getStartDate().isAfter(displayDate)));
                    if (historyRoleAssignment.getStartDate().isAfter(displayDate)) {
                        log.debug("Removing: {}", historyRoleAssignment.getRoleId() + " not active at that time.");
                        itr.remove();
                    }
                }
                //if the stopDate is null means it never expires
                if (historyRoleAssignment.getStopDate() != null) {
                    log.debug("SelectedDate {} is after StopDate {} => {}", displayDate, historyRoleAssignment.getStopDate(), (displayDate.isAfter(historyRoleAssignment.getStopDate())));
                    if (displayDate.isAfter(historyRoleAssignment.getStopDate())) {
                        log.debug("Removing: {}", historyRoleAssignment.getRoleId() + " expired at that time.");
                        itr.remove();
                    }
                }
            }
        }
    }

	private List<HistoricAssignment> filterHistoricAssignments(LocalDate displayDate, List<HistoricAssignment> historicAssignments) {
		List<HistoricAssignment> filteredAssignments = new ArrayList<>();

		for (HistoricAssignment ha : historicAssignments) {
			if (shouldIncludeByDate(displayDate, ha.getStartDate(), ha.getStopDate(), "HistoricAssignment", ha.getUserRoleId())) {
				filteredAssignments.add(ha);
			}
		}

		return filteredAssignments;
	}

	private List<HistoricExceptedAssignment> filterHistoricExceptedAssignments(LocalDate displayDate, List<HistoricExceptedAssignment> historicExceptedAssignments) {
		List<HistoricExceptedAssignment> filteredAssignments = new ArrayList<>();

		for (HistoricExceptedAssignment hea : historicExceptedAssignments) {
			if (shouldIncludeByDate(displayDate, hea.getStartDate(), hea.getStopDate(), "HistoricExceptedAssignment", hea.getExceptionUserRoleId())) {
				filteredAssignments.add(hea);
			}
		}

		return filteredAssignments;
	}

	private boolean shouldIncludeByDate(LocalDate displayDate, LocalDate startDate, LocalDate stopDate, String type, long roleId) {
		log.debug("{}: {} start: {} stop: {}", type, roleId, startDate, stopDate);

		// If the startDate is null means the assignment is active
		if (startDate != null) {
			log.debug("StartDate {} is after SelectedDate: {} => {}", startDate, displayDate, startDate.isAfter(displayDate));
			if (startDate.isAfter(displayDate)) {
				log.debug("Removing: {} not active at that time.", roleId);
				return false;
			}
		}

		// If the stopDate is null means it never expires
		if (stopDate != null) {
			log.debug("SelectedDate {} is after StopDate {} => {}", displayDate, stopDate, displayDate.isAfter(stopDate));
			if (displayDate.isAfter(stopDate)) {
				log.debug("Removing: {} expired at that time.", roleId);
				return false;
			}
		}

		return true;
	}

    private LocalDate parseAndHandleFutureDate(String reportFormDate) {
        if (reportFormDate != null && StringUtils.hasLength(reportFormDate)) {
            LocalDate parsedDate = LocalDate.parse(reportFormDate);
            if (parsedDate.isEqual(LocalDate.now()) || parsedDate.isBefore(LocalDate.now())) {
                return parsedDate;
            }
        }
        return LocalDate.now();
    }
}
