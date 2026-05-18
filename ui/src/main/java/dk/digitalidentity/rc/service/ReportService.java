package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.history.model.HistoryFunction;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricExceptedAssignment;
import dk.digitalidentity.rc.service.assignment.HistoricAssignmentService;
import dk.digitalidentity.rc.service.assignment.HistoricExceptedAssignmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dk.digitalidentity.rc.controller.mvc.viewmodel.ReportForm;
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
    private OrganisationConstraintUtil organisationConstraintUtil;

	@Autowired
	private HistoricAssignmentService historicAssignmentService;

	@Autowired
	private HistoricExceptedAssignmentService historicExceptedAssignmentService;

	@PersistenceContext
	private EntityManager entityManager;

	// Self-injection of the Spring proxy so @Transactional is honoured when called from non-Spring contexts (e.g. ReportXlsxView)
	@Autowired
	@org.springframework.context.annotation.Lazy
	private ReportService self;

	/**
	 * Streams user-role assignment report entries directly from the database, applying all filters inline.
	 * Avoids materialising the full assignment list in memory.
	 *
	 * @param queryDate       Date used for the DB query (capped at today)
	 * @param displayDate     Date used for start/stop-date filtering
	 * @param allowedUserUuids If non-null, only assignments for these user UUIDs are included (OU filter)
	 * @param allowedOuUuids  If non-null, only assignments with a responsibleOU in this set (or null responsibleOU) are included
	 * @param itSystemIds     If non-null/non-empty, only assignments for these IT systems are fetched from DB
	 */
	@Transactional(readOnly = true)
	public void streamUserRoleAssignmentReportEntries(
		LocalDate queryDate,
		LocalDate displayDate,
		Set<String> allowedUserUuids,
		Set<String> allowedOuUuids,
		Collection<Long> itSystemIds,
		Map<String, HistoryUser> users,
		Map<String, HistoryOU> orgUnits,
		List<HistoryItSystem> itSystems,
		Locale locale,
		boolean showInactiveUsers,
		boolean onlyResponsibleOU,
		Consumer<UserRoleAssignmentReportEntry> consumer) {

		Map<Long, Long> userRoleIdWeight = ReportSystemRoleWeightService.computeUserRoleWeights(itSystems);

		// Lightweight first pass: compute max weight per (userId, itSystemName) without loading full entities
		List<Object[]> weightTriples = (itSystemIds != null && !itSystemIds.isEmpty())
			? historicAssignmentService.getWeightTriplesForItSystems(queryDate, itSystemIds)
			: historicAssignmentService.getWeightTriples(queryDate);

		Map<String, Map<String, Long>> userItSystemMaxWeight = new HashMap<>();
		for (Object[] triple : weightTriples) {
			String userUuid = (String) triple[0];
			String itSystemName = (String) triple[1];
			Long userRoleId = (Long) triple[2];
			LocalDate startDate = (LocalDate) triple[3];
			LocalDate stopDate = (LocalDate) triple[4];
			if (!shouldIncludeByDate(displayDate, startDate, stopDate, "weight-triple", userRoleId)) continue;
			if (allowedUserUuids != null && !allowedUserUuids.contains(userUuid)) continue;
			HistoryUser user = users.get(userUuid);
			if (user == null || (!showInactiveUsers && !user.isUserActive())) continue;
			long weight = userRoleIdWeight.getOrDefault(userRoleId, 1L);
			userItSystemMaxWeight
				.computeIfAbsent(user.getUserUserId(), k -> new HashMap<>())
				.merge(itSystemName, weight, Math::max);
		}

		// Pre-load constraints in one bulk query — avoids N+1 lazy loads per streamed entity.
		Map<Long, List<Object[]>> constraintsByAssignmentId = (itSystemIds != null && !itSystemIds.isEmpty())
			? historicAssignmentService.getConstraintsForDateAndItSystems(queryDate, itSystemIds)
			: historicAssignmentService.getConstraintsForDate(queryDate);

		// Bundle all pre-computed lookups — keeps the stream forEach signature clean.
		StreamingReportContext ctx = StreamingReportContext.build(
			users, orgUnits, orgUnitService.getAllCached(),
			userRoleIdWeight, userItSystemMaxWeight, constraintsByAssignmentId);

		// Second pass: stream full entities from DB and emit report entries
		try (var stream = (itSystemIds != null && !itSystemIds.isEmpty())
				? historicAssignmentService.streamActiveAtDateAndItSystems(queryDate, itSystemIds)
				: historicAssignmentService.streamActiveAtDate(queryDate)) {

			stream.forEach(ha -> {
				try {
					if (!shouldIncludeByDate(displayDate, ha.getStartDate(), ha.getStopDate(), "HistoricAssignment", ha.getUserRoleId())) return;
					if (allowedUserUuids != null && !allowedUserUuids.contains(ha.getUserUuid())) return;
					if (allowedOuUuids != null && ha.getResponsibleOUUuid() != null && !allowedOuUuids.contains(ha.getResponsibleOUUuid())) return;

					HistoryUser user = ctx.users.get(ha.getUserUuid());
					if (user == null) {
						log.warn("User not found for assignment: {}", ha.getUserUuid());
						return;
					}
					if (!showInactiveUsers && !user.isUserActive()) return;

					if (onlyResponsibleOU) {
						streamReportEntry(ha, user, ctx, locale, consumer);
					} else {
						List<HistoryOU> positions = ctx.userPositionsMap.get(ha.getUserUuid());
						if (positions == null || positions.isEmpty()) {
							streamReportEntry(ha, user, ctx, locale, consumer);
						} else {
							for (HistoryOU position : positions) {
								streamReportEntry(ha, user, position, ctx, locale, consumer);
							}
						}
					}
				} finally {
					// Free this entity from the Hibernate L1 cache immediately after processing
					entityManager.detach(ha);
				}
			});
		}
	}

	/**
	 * Streams negative (excepted) user-role assignment report entries directly from the database.
	 */
	@Transactional(readOnly = true)
	public void streamNegativeUserRoleAssignmentReportEntries(
		LocalDate queryDate,
		LocalDate displayDate,
		Set<String> allowedUserUuids,
		Set<String> allowedOuUuids,
		Collection<Long> itSystemIds,
		Map<String, HistoryUser> users,
		Map<String, HistoryOU> orgUnits,
		Locale locale,
		boolean showInactiveUsers,
		Consumer<UserRoleAssignmentReportEntry> consumer) {

		try (var stream = (itSystemIds != null && !itSystemIds.isEmpty())
				? historicExceptedAssignmentService.streamActiveAtDateAndItSystems(queryDate, itSystemIds)
				: historicExceptedAssignmentService.streamActiveAtDate(queryDate)) {

			stream.forEach(hea -> {
				try {
					if (!shouldIncludeByDate(displayDate, hea.getStartDate(), hea.getStopDate(), "HistoricExceptedAssignment", hea.getExceptionUserRoleId())) return;
					if (allowedUserUuids != null && !allowedUserUuids.contains(hea.getExceptionUserUuid())) return;
					if (allowedOuUuids != null && hea.getResponsibleOUUuid() != null && !allowedOuUuids.contains(hea.getResponsibleOUUuid())) return;

					HistoryUser user = users.get(hea.getExceptionUserUuid());
					if (user == null) {
						log.warn("User not found for excepted assignment: {}", hea.getExceptionUserUuid());
						return;
					}
					if (!showInactiveUsers && !user.isUserActive()) return;

					String assignedThroughStr = buildExceptedAssignmentThroughString(hea, locale);
					String orgUnitName = hea.getResponsibleOUName();
					String orgUnitUUID = hea.getResponsibleOUUuid();
					if (hea.getResponsibleOUUuid() != null) {
						HistoryOU ou = orgUnits.get(hea.getResponsibleOUUuid());
						if (ou != null) {
							orgUnitName = ou.getOuName();
							orgUnitUUID = ou.getOuUuid();
						}
					}

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
					row.setPostponedConstraints("");
					row.setStartDate(hea.getStartDate());
					row.setStopDate(hea.getStopDate());
					consumer.accept(row);
				} finally {
					entityManager.detach(hea);
				}
			});
		}
	}

	/** Resolves the responsible OU from the assignment itself, then delegates. */
	private void streamReportEntry(
		HistoricAssignment ha,
		HistoryUser user,
		StreamingReportContext ctx,
		Locale locale,
		Consumer<UserRoleAssignmentReportEntry> consumer) {

		String orgUnitName = ha.getResponsibleOUName();
		String orgUnitUUID = ha.getResponsibleOUUuid();

		if (ha.getResponsibleOUUuid() != null) {
			HistoryOU ou = ctx.orgUnits.get(ha.getResponsibleOUUuid());
			if (ou != null) {
				orgUnitName = ou.getOuName();
				orgUnitUUID = ou.getOuUuid();
			}
		}

		streamReportEntryInternal(ha, user, orgUnitName, orgUnitUUID, ctx, locale, consumer);
	}

	/** Uses an explicit position OU (for users with multiple positions), then delegates. */
	private void streamReportEntry(
		HistoricAssignment ha,
		HistoryUser user,
		HistoryOU position,
		StreamingReportContext ctx,
		Locale locale,
		Consumer<UserRoleAssignmentReportEntry> consumer) {

		streamReportEntryInternal(ha, user, position.getOuName(), position.getOuUuid(), ctx, locale, consumer);
	}

	private void streamReportEntryInternal(
		HistoricAssignment historicAssignment,
		HistoryUser user,
		String orgUnitName,
		String orgUnitUUID,
		StreamingReportContext ctx,
		Locale locale,
		Consumer<UserRoleAssignmentReportEntry> consumer) {

		String assignedThroughStr = buildAssignedThroughString(historicAssignment, locale);
		String postponedConstraints = buildPostponedConstraintsString(historicAssignment.getId(), ctx);

		long roleWeight = ctx.userRoleIdWeight.getOrDefault(historicAssignment.getUserRoleId(), 1L);
		long itSystemResultWeight = ctx.userItSystemMaxWeight
			.getOrDefault(user.getUserUserId(), Map.of())
			.getOrDefault(historicAssignment.getItSystemName(), 1L);

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
		row.setSystemRoleWeight(roleWeight);
		row.setItSystemResultWeight(itSystemResultWeight);

		consumer.accept(row);
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

	private static final java.util.regex.Pattern UUID_PATTERN =
		java.util.regex.Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

	/**
	 * Builds a human-readable string of postponed constraints for a report row.
	 * Organisation constraint values (UUIDs) are resolved to display names via the context.
	 */
	@SuppressWarnings("unchecked")
	private String buildPostponedConstraintsString(Long assignmentId, StreamingReportContext ctx) {
		List<Object[]> constraints = ctx.constraintsByAssignmentId.get(assignmentId);
		if (constraints == null || constraints.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (Object[] row : constraints) {
			String constraintName = (String) row[1];
			List<String> constraintValues = (List<String>) row[2];

			if (constraintValues == null || constraintValues.isEmpty()) {
				sb.append(constraintName).append("\n");
				continue;
			}

			for (String constraintValue : constraintValues) {
				if (constraintValue != null && UUID_PATTERN.matcher(constraintValue).matches()) {
					// Organisation constraint: resolve UUID to display name
					String ouName = ctx.orgUnitNamesByUuid.get(constraintValue);
					sb.append(constraintName).append(": ")
					  .append(ouName != null ? ouName : constraintValue)
					  .append("\n");
				} else {
					sb.append(constraintName).append(": ").append(constraintValue).append("\n");
				}
			}
		}

		return sb.toString().trim();
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
		}
		else {
			ouRoleAssignments = historyService.getOURoleAssignments(localDate);
		}

		// add systemRoles based on itSystems after filter
		for (HistoryItSystem itSystem : itSystems) {
			systemRoles.addAll(itSystem.getHistorySystemRoles());
		}

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
		}

		Map<String, Object> model = new HashMap<>();
		model.put("queryDate", localDate);
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
		model.put("userKLEAssignments", userKleAssignments);
		model.put("reportForm", reportForm);
		model.put("reportService", self);
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
