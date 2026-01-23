package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.history.model.HistoryFunction;
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
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentExclusion;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.dao.history.model.HistoryOUUser;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
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

	// used to be part of ReportXlsView code, but moved here because our ManualRolesService also
	// needs these computations
	public List<UserRoleAssignmentReportEntry> getUserRoleAssignmentReportEntries(
			Map<String, HistoryUser> users,
			Map<String, HistoryOU> orgUnits,
			List<HistoryItSystem> itSystems,
			Map<String, List<HistoryRoleAssignment>> userRoleAssignments,
			Map<String, List<HistoryOURoleAssignment>> allOuRoleAssignments,
			Map<Long, String> itSystemNameMapping,
			Locale locale,
			boolean showInactiveUsers,
			boolean onlyResponsibleOU) {
		List<UserRoleAssignmentReportEntry> result = new ArrayList<>();
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignments = new HashMap<>();
		Map<String, List<HistoryOURoleAssignment>> ouRoleAssignmentsWithExceptions = new HashMap<>();
		Map<String, List<HistoryOURoleAssignment>> negativeTitleOuRoleAssignments = new HashMap<>();
		Map<String, List<HistoryOURoleAssignment>> titleRoleAssignments = new HashMap<>();
		Map<String, List<HistoryOURoleAssignment>> functionRoleAssignments = new HashMap<>();

		// Split ouRoleAssignments
		for (Entry<String, List<HistoryOURoleAssignment>> entry : allOuRoleAssignments.entrySet()) {
			for (HistoryOURoleAssignment userRoleAssignmentReportEntry : entry.getValue()) {
				List<HistoryOURoleAssignmentExclusion> exclusions = userRoleAssignmentReportEntry.getExclusions();
				if (exclusions.isEmpty()) {
				    ouRoleAssignments.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(userRoleAssignmentReportEntry);
				} else if (exclusions.stream().allMatch(ex -> ex.getExclusionType() == ExclusionType.excepted_users)) {
				    ouRoleAssignmentsWithExceptions.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(userRoleAssignmentReportEntry);
				} else if (
				    exclusions.stream().allMatch(ex -> {
				        var t = ex.getExclusionType();
				        return t == ExclusionType.titles || t == ExclusionType.excepted_users;
				    })
				    && exclusions.stream().anyMatch(ex -> ex.getExclusionType() == ExclusionType.titles)
				) {
				    titleRoleAssignments.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(userRoleAssignmentReportEntry);
				} else if (exclusions.stream().allMatch(ex -> ex.getExclusionType() == ExclusionType.negative_titles)) {
				    negativeTitleOuRoleAssignments.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(userRoleAssignmentReportEntry);
				}  else if (exclusions.stream().allMatch(ex -> ex.getExclusionType() == ExclusionType.functions)) {
					functionRoleAssignments.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(userRoleAssignmentReportEntry);
				}
				// else: mixed negative_titles/functions with others -> currently unbucketed
			}
		}

		// Add all assignment based on negative title ou assignmnets
		result.addAll(getNegativeUserRoleAssignmentReportEntries(users, orgUnits, itSystems, negativeTitleOuRoleAssignments, locale, showInactiveUsers, false));

		for (Map.Entry<String, List<HistoryRoleAssignment>> entry : userRoleAssignments.entrySet()) {
			HistoryUser user = users.get(entry.getKey());

			// Skip inactive users if showInactive users = false
			if (!showInactiveUsers && !user.isUserActive()) {
				continue;
			}

			List<HistoryRoleAssignment> roleAssignments = entry.getValue();

			for (HistoryRoleAssignment roleAssignment : roleAssignments) {
				if (onlyResponsibleOU && roleAssignment.getOrgUnitUuid() != null) {
					HistoryOU ou = orgUnits.get(roleAssignment.getOrgUnitUuid());
					addRow(itSystems, locale, result, user, roleAssignment, ou);
				}
				else {
					List<HistoryOU> positions = getPositions(orgUnits, user);

					for (HistoryOU position : positions) {
						addRow(itSystems, locale, result, user, roleAssignment, position);
					}
				}
			}
		}

		for (HistoryOU ou : orgUnits.values()) {
			if (ou.getUsers() == null) {
				continue;
			}


			List<HistoryOURoleAssignment> ouAssignments = ouRoleAssignments.get(ou.getOuUuid());
			for (HistoryOUUser historyOuUser : ou.getUsers()) {
				HistoryUser user = users.get(historyOuUser.getUserUuid());
				if (user == null) {
					continue;
				}

				// Skip inactive users if showInactive users = false
				if (!showInactiveUsers && !user.isUserActive()) {
					continue;
				}

				// The users position has doNotInherit set, so the user does not get any roles from this OU.
				if (historyOuUser.getDoNotInherit()) {
					continue;
				}

				boolean hasPosition = Boolean.TRUE.equals(historyOuUser.getHasPosition());

				List<HistoryOURoleAssignment> userTitleAssignments = null;

				// Filter on positive titles and excluded users if both selected
				String titleUuid = historyOuUser.getTitleUuid();
				if (titleUuid != null) {
					List<HistoryOURoleAssignment> assignments = titleRoleAssignments.get(ou.getOuUuid());
					if (assignments != null && !assignments.isEmpty()) {
						final String userUuid = historyOuUser.getUserUuid();

						userTitleAssignments = assignments.stream().filter(t -> {
							List<HistoryOURoleAssignmentExclusion> exs = Optional.ofNullable(t.getExclusions()).orElseGet(Collections::emptyList);

							// Titles must include the user's title UUID (exact, CSV-safe)
							boolean titleMatches = exs.stream()
								.filter(ex -> ex.getExclusionType() == ExclusionType.titles)
								.map(HistoryOURoleAssignmentExclusion::getTitleUuids)
								.filter(Objects::nonNull)
								.flatMap(s -> Arrays.stream(s.split(",")))
								.map(String::trim)
								.anyMatch(titleUuid::equals);

							if (!titleMatches) return false;

							// User must NOT be in excepted users (exact, CSV-safe)
							boolean userIsExcepted = exs.stream()
								.filter(ex -> ex.getExclusionType() == ExclusionType.excepted_users)
								.map(HistoryOURoleAssignmentExclusion::getUserUuids)
								.filter(Objects::nonNull)
								.flatMap(s -> Arrays.stream(s.split(",")))
								.map(String::trim)
								.anyMatch(userUuid::equals);

							return !userIsExcepted;
						}).collect(Collectors.toList());
					}
				}

				List<HistoryOURoleAssignment> userFunctionAssignments = null;

				// Filter on functions
				String userFunctions = historyOuUser.getFunctionUuids();
				if (userFunctions != null && !userFunctions.isBlank()) {
					List<HistoryOURoleAssignment> assignments = functionRoleAssignments.get(ou.getOuUuid());
					if (assignments != null && !assignments.isEmpty()) {
						// Split user's functions into a Set
						Set<String> userFunctionSet = Arrays.stream(userFunctions.split(","))
								.map(String::trim)
								.collect(Collectors.toSet());

						userFunctionAssignments = assignments.stream().filter(t -> {
							List<HistoryOURoleAssignmentExclusion> exs = Optional.ofNullable(t.getExclusions())
									.orElseGet(Collections::emptyList);

							// Check if user has at least one required function (exact, CSV-safe)
							boolean functionMatches = exs.stream()
									.filter(ex -> ex.getExclusionType() == ExclusionType.functions)
									.map(HistoryOURoleAssignmentExclusion::getFunctionUuids)
									.filter(Objects::nonNull)
									.flatMap(s -> Arrays.stream(s.split(",")))
									.map(String::trim)
									.anyMatch(userFunctionSet::contains);

							return functionMatches;
						}).collect(Collectors.toList());
					}
				}

				// ok, time to generate records


				long domainId = user.getDomainId();
				String userName = user.getUserName();
				String userId = user.getUserUserId();
				String employeeId = user.getUserExtUuid();
				boolean userActive = user.isUserActive();

				// OU-wide assignments only apply to users with positions
				if (ouAssignments != null && hasPosition) {
					for (HistoryOURoleAssignment roleAssignment : ouAssignments) {

						// Get ItSystem by id
						Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> Objects.equals(itSystem.getItSystemId(), roleAssignment.getRoleItSystemId())).findFirst();
						String itSystem = "";
						if (first.isPresent()) {
							HistoryItSystem historyItSystem = first.get();
							itSystem = historyItSystem.getItSystemName();
						}

						if (Boolean.TRUE.equals(roleAssignment.getManager())) {
							boolean isManager = Objects.equals(ou.getOuManagerUuid(), user.getUserUuid());
							if (Boolean.TRUE.equals(roleAssignment.getSubstitutes())) {
								boolean isSubstitute = false;
								if (StringUtils.hasLength(ou.getOuSubstituteUuids())) {
									Set<String> substituteUuids = Set.of(ou.getOuSubstituteUuids().split(","));
									isSubstitute = substituteUuids.contains(user.getUserUuid());
								}

								if (!isManager && !isSubstitute) {
									continue;
								}
							} else {
								if (!isManager) {
									continue;
								}
							}
						}

						String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";
						String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.orgunit", null, locale) + ": " + ((roleAssignment.getAssignedThroughName() != null) ? roleAssignment.getAssignedThroughName() : ou.getOuName());

						UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
						row.setUserName(userName);
						row.setUserId(userId);
						row.setEmployeeId(employeeId);
						row.setOrgUnitName(ou.getOuName());
						row.setOrgUnitUUID(ou.getOuUuid());
						row.setUserActive(userActive);
						row.setRoleId(roleAssignment.getRoleId());
						row.setItSystem(itSystem);
						row.setAssignedBy(assignedBy);
						row.setAssignedWhen(roleAssignment.getAssignedWhen());
						row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
						result.add(row);
					}
				}

				// Title assignments only apply to users with positions
				if (userTitleAssignments != null && hasPosition) {
					for (HistoryOURoleAssignment roleAssignment : userTitleAssignments) {

						// Get ItSystem by id
						Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> Objects.equals(itSystem.getItSystemId(), roleAssignment.getRoleItSystemId())).findFirst();
						String itSystem = "";
						if (first.isPresent()) {
							HistoryItSystem historyItSystem = first.get();
							itSystem = historyItSystem.getItSystemName();
						}

						String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";

						String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.title", null, locale) + ": " + ((roleAssignment.getAssignedThroughName() != null) ? roleAssignment.getAssignedThroughName() : ou.getOuName());

						UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
						row.setUserName(userName);
						row.setUserId(userId);
						row.setEmployeeId(employeeId);
						row.setOrgUnitName(ou.getOuName());
						row.setOrgUnitUUID(ou.getOuUuid());
						row.setUserActive(userActive);
						row.setRoleId(roleAssignment.getRoleId());
						row.setItSystem(itSystem);
						row.setAssignedBy(assignedBy);
						row.setAssignedWhen(roleAssignment.getAssignedWhen());
						row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
						result.add(row);
					}
				}

				if (userFunctionAssignments != null) {
					for (HistoryOURoleAssignment roleAssignment : userFunctionAssignments) {

						// Get ItSystem by id
						Optional<HistoryItSystem> first = itSystems.stream()
								.filter(itSystem -> Objects.equals(itSystem.getItSystemId(), roleAssignment.getRoleItSystemId()))
								.findFirst();
						String itSystem = "";
						if (first.isPresent()) {
							HistoryItSystem historyItSystem = first.get();
							itSystem = historyItSystem.getItSystemName();
						}

						String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";
						String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.function", null, locale) +
								": " + ((roleAssignment.getAssignedThroughName() != null) ?
								roleAssignment.getAssignedThroughName() : ou.getOuName());

						UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
						row.setUserName(userName);
						row.setUserId(userId);
						row.setEmployeeId(employeeId);
						row.setOrgUnitName(ou.getOuName());
						row.setOrgUnitUUID(ou.getOuUuid());
						row.setUserActive(userActive);
						row.setRoleId(roleAssignment.getRoleId());
						row.setItSystem(itSystem);
						row.setAssignedBy(assignedBy);
						row.setAssignedWhen(roleAssignment.getAssignedWhen());
						row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
						result.add(row);
					}
				}
			}

			// Process managers and substitutes without position in this OU
			if (ouAssignments != null) {
				for (HistoryOURoleAssignment roleAssignment : ouAssignments) {
					if (Boolean.TRUE.equals(roleAssignment.getManager())) {
						// Process this OU
						processManagerAndSubstitutesWithoutPosition(ou, roleAssignment, users,
								itSystems, locale, showInactiveUsers, result);

					}
				}
			}
		}

		// Add assignments from OUs, with exceptions
		for (Entry<String, List<HistoryOURoleAssignment>> entry : ouRoleAssignmentsWithExceptions.entrySet()) {
			List<HistoryOURoleAssignment> ouRoleAssignmentWithExceptions = entry.getValue();
			HistoryOU orgUnit = orgUnits.get(entry.getKey());


			for (HistoryOURoleAssignment ouRoleAssignment : ouRoleAssignmentWithExceptions) {

				// Get ItSystem by id
				Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> Objects.equals(itSystem.getItSystemId(), ouRoleAssignment.getRoleItSystemId())).findFirst();
				String itSystem = "";
				if (first.isPresent()) {
					HistoryItSystem historyItSystem = first.get();
					itSystem = historyItSystem.getItSystemName();
				}

				String assignedBy = ouRoleAssignment.getAssignedByName() + " (" + ouRoleAssignment.getAssignedByUserId() + ")";
				String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.orgunit", null, locale) + ": " + orgUnit.getOuName();

				for (HistoryOUUser user : orgUnit.getUsers()) {
					// The users position has doNotInherit set, so the user does not get any roles from this OU.
					if (user.getDoNotInherit()) {
						continue;
					}

					// OU-wide assignments with exceptions only apply to users with positions
					if (!Boolean.TRUE.equals(user.getHasPosition())) {
						continue;
					}

					if (ouRoleAssignment.getExclusions().stream().anyMatch(ex -> ex.getUserUuids().contains(user.getUserUuid()))) {
						continue;
					}

					if (!users.containsKey(user.getUserUuid())) {
						log.warn("Excepted user with uuid=" + user.getUserUuid() + " does not exist");
						continue;
					}

					HistoryUser actualUser = users.get(user.getUserUuid());

					UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
					row.setDomainId(actualUser.getDomainId());
					row.setUserName(actualUser.getUserName());
					row.setUserId(actualUser.getUserUserId());
					row.setEmployeeId(actualUser.getUserExtUuid());
					row.setOrgUnitName(orgUnit.getOuName());
					row.setOrgUnitUUID(orgUnit.getOuUuid());
					row.setUserActive(actualUser.isUserActive());
					row.setRoleId(ouRoleAssignment.getRoleId());
					row.setItSystem(itSystem);
					row.setAssignedBy(assignedBy);
					row.setAssignedWhen(ouRoleAssignment.getAssignedWhen());
					row.setAssignedThrough(assignedThroughStr);
					row.setStartDate(ouRoleAssignment.getStartDate());
					row.setStopDate(ouRoleAssignment.getStopDate());
					result.add(row);
				}
			}
		}
		ReportSystemRoleWeightService.addSystemRoleWeights(itSystems, result);
		return result;
	}

	private void processManagerAndSubstitutesWithoutPosition(
			HistoryOU ou,
			HistoryOURoleAssignment roleAssignment,
			Map<String, HistoryUser> users,
			List<HistoryItSystem> itSystems,
			Locale locale,
			boolean showInactiveUsers,
			List<UserRoleAssignmentReportEntry> result) {

		// Find manager
		String managerUuid = ou.getOuManagerUuid();
		if (managerUuid == null) {
			return;
		}

		HistoryUser manager = users.get(managerUuid);
		if (manager == null || (!showInactiveUsers && !manager.isUserActive())) {
			return;
		}

		// Check if manager already has position in this OU
		boolean hasPosition = ou.getUsers().stream()
			.anyMatch(u -> u.getUserUuid().equals(managerUuid)
				&& Boolean.TRUE.equals(u.getHasPosition()));

		if (hasPosition) {
			return; // Already processed in main loop
		}

		// Add assignment for manager without position
		addOuAssignmentRow(ou, roleAssignment, manager, itSystems, locale, result);

		// Process substitutes if enabled
		if (Boolean.TRUE.equals(roleAssignment.getSubstitutes()) &&
				StringUtils.hasLength(ou.getOuSubstituteUuids())) {

			Set<String> substituteUuids = Set.of(ou.getOuSubstituteUuids().split(","));
			for (String substituteUuid : substituteUuids) {
				HistoryUser substitute = users.get(substituteUuid);
				if (substitute == null || (!showInactiveUsers && !substitute.isUserActive())) {
					continue;
				}

				// Check if substitute already processed
				boolean subHasPosition = ou.getUsers().stream()
					.anyMatch(u -> u.getUserUuid().equals(substituteUuid)
						&& Boolean.TRUE.equals(u.getHasPosition()));

				if (!subHasPosition && !substituteUuid.equals(managerUuid)) {
					addOuAssignmentRow(ou, roleAssignment, substitute, itSystems, locale, result);
				}
			}
		}
	}

	private void addOuAssignmentRow(
			HistoryOU ou,
			HistoryOURoleAssignment roleAssignment,
			HistoryUser user,
			List<HistoryItSystem> itSystems,
			Locale locale,
			List<UserRoleAssignmentReportEntry> result) {

		Optional<HistoryItSystem> first = itSystems.stream()
				.filter(itSystem -> Objects.equals(itSystem.getItSystemId(), roleAssignment.getRoleItSystemId()))
				.findFirst();

		String itSystem = first.map(HistoryItSystem::getItSystemName).orElse("");
		String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";
		String assignedThroughStr = messageSource.getMessage("xls.role.assigned.trough.type.orgunit", null, locale) +
				": " + ((roleAssignment.getAssignedThroughName() != null) ?
				roleAssignment.getAssignedThroughName() : ou.getOuName());

		UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
		row.setDomainId(user.getDomainId());
		row.setUserName(user.getUserName());
		row.setUserId(user.getUserUserId());
		row.setEmployeeId(user.getUserExtUuid());
		row.setOrgUnitName(ou.getOuName());
		row.setOrgUnitUUID(ou.getOuUuid());
		row.setUserActive(user.isUserActive());
		row.setRoleId(roleAssignment.getRoleId());
		row.setItSystem(itSystem);
		row.setAssignedBy(assignedBy);
		row.setAssignedWhen(roleAssignment.getAssignedWhen());
		row.setAssignedThrough(assignedThroughStr);
		row.setStartDate(roleAssignment.getStartDate());
		row.setStopDate(roleAssignment.getStopDate());
		result.add(row);
	}

	public List<UserRoleAssignmentReportEntry> getNegativeUserRoleAssignmentReportEntries(
			Map<String, HistoryUser> users,
			Map<String, HistoryOU> orgUnits,
			List<HistoryItSystem> itSystems,
			Map<String, List<HistoryOURoleAssignment>> negativeTitleRoleAssignments,
			Locale locale,
			boolean showInactiveUsers,
			boolean notAssignedRows) {

		List<UserRoleAssignmentReportEntry> result = new ArrayList<>();

		List<HistoryOURoleAssignment> negativeAssignments = negativeTitleRoleAssignments.values().stream().flatMap(List::stream).toList();

		for (HistoryOU ou : orgUnits.values()) {
			if (ou.getUsers() == null || ou.getUsers().isEmpty()) {
				continue;
			}

			List<HistoryOURoleAssignment> negativeAssignmentsForOU = negativeAssignments.stream().filter(nt -> nt.getOuUuid().equals(ou.getOuUuid())).toList();
			if (negativeAssignmentsForOU.isEmpty()) {
				continue;
			}

			for (HistoryOUUser historyOuUser : ou.getUsers()) {
				HistoryUser user = users.get(historyOuUser.getUserUuid());
				if (user == null) {
					continue;
				}

				// Skip inactive users if showInactive users = false
				if (!showInactiveUsers && !user.isUserActive()) {
					continue;
				}

				// The users position has doNotInherit set, so the user does not get any roles from this OU.
				if (historyOuUser.getDoNotInherit()) {
					continue;
				}

				// Negative title assignments are OU-wide, so only apply to users with positions
				if (!Boolean.TRUE.equals(historyOuUser.getHasPosition())) {
					continue;
				}

				List<HistoryOURoleAssignment> userNegativeTitleAssignments = null;
				String titleUuid = historyOuUser.getTitleUuid();
				if (titleUuid != null) {
					userNegativeTitleAssignments = negativeAssignmentsForOU.stream()
							.filter(t -> {
								boolean contained = t.getExclusions().stream().anyMatch(ex -> ex.getTitleUuids().contains(historyOuUser.getTitleUuid()));
								if (notAssignedRows) {
									return contained;
								} else
									return !contained;
							})
							.toList();
				}

				// ok, time to generate records
				long domainId = user.getDomainId();
				String userName = user.getUserName();
				String userId = user.getUserUserId();
				String employeeId = user.getUserExtUuid();
				boolean userActive = user.isUserActive();

				if (userNegativeTitleAssignments != null) {
					for (HistoryOURoleAssignment roleAssignment : userNegativeTitleAssignments) {

						// Get ItSystem by id
						Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> Objects.equals(itSystem.getItSystemId(), roleAssignment.getRoleItSystemId())).findFirst();
						String itSystem = "";
						if (first.isPresent()) {
							HistoryItSystem historyItSystem = first.get();
							itSystem = historyItSystem.getItSystemName();
						}

						String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";
						String assignedThroughStr = notAssignedRows ?
								messageSource.getMessage("xls.role.assigned.trough.type.negativetitle", null, locale) + ": " + (ou.getOuName()) :
								messageSource.getMessage("xls.role.assigned.trough.type.title", null, locale) + ": " + (ou.getOuName());

						UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
						row.setDomainId(domainId);
						row.setUserName(userName);
						row.setUserId(userId);
						row.setEmployeeId(employeeId);
						row.setOrgUnitName(ou.getOuName());
						row.setOrgUnitUUID(ou.getOuUuid());
						row.setUserActive(userActive);
						row.setRoleId(roleAssignment.getRoleId());
						row.setItSystem(itSystem);
						row.setAssignedBy(assignedBy);
						row.setAssignedWhen(roleAssignment.getAssignedWhen());
						row.setAssignedThrough(assignedThroughStr);
						row.setStartDate(roleAssignment.getStartDate());
						row.setStopDate(roleAssignment.getStopDate());
						result.add(row);
					}
				}

			}
		}
		return result;
	}

	private void addRow(List<HistoryItSystem> itSystems, Locale locale, List<UserRoleAssignmentReportEntry> result,
						HistoryUser user, HistoryRoleAssignment roleAssignment, HistoryOU ou) {
		// Get ItSystem by id
		Optional<HistoryItSystem> first = itSystems.stream().filter(itSystem -> Objects.equals(itSystem.getItSystemId(), roleAssignment.getRoleItSystemId())).findFirst();
		String itSystem = "";
		if (first.isPresent()) {
			HistoryItSystem historyItSystem = first.get();
			itSystem = historyItSystem.getItSystemName();
		}

		String assignedBy = roleAssignment.getAssignedByName() + " (" + roleAssignment.getAssignedByUserId() + ")";

		// Creating assigned Through string
		String assignedThroughMessageCode = switch (roleAssignment.getAssignedThroughType()) {
			case DIRECT -> "xls.role.assigned.trough.type.direct";
			case ROLEGROUP -> "xls.role.assigned.trough.type.direct_group";
			case POSITION -> "xls.role.assigned.trough.type.position";
			case ORGUNIT -> "xls.role.assigned.trough.type.orgunit";
			case TITLE -> "xls.role.assigned.trough.type.title";
		};
		String assignedThroughStr = messageSource.getMessage(assignedThroughMessageCode, null, locale);

		if (StringUtils.hasLength(roleAssignment.getAssignedThroughName())) {
			assignedThroughStr += ": " + roleAssignment.getAssignedThroughName();
		}

		String postponedConstraints = null;

		if (roleAssignment.getPostponedConstraints() != null) {
			StringBuilder sb = new StringBuilder();
			String regex = "Organisation: ([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})";
			Pattern pattern = Pattern.compile(regex);
			for (String line : roleAssignment.getPostponedConstraints().split("\\n")) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.matches()) {
					String orgUuid = matcher.group(1);
					OrgUnit orgUnit = orgUnitDao.findById(orgUuid).orElse(null);
					if (orgUnit != null) {
						sb.append("Organisation: " + orgUnit.getName() + "\n");
					}
				} else {
					sb.append(line + "\n");
				}
			}
			postponedConstraints = sb.toString();
		}

		UserRoleAssignmentReportEntry row = new UserRoleAssignmentReportEntry();
		row.setDomainId(user.getDomainId());
		row.setUserName(user.getUserName());
		row.setUserId(user.getUserUserId());
		row.setEmployeeId(user.getUserExtUuid());
		row.setOrgUnitName((ou != null) ? ou.getOuName() : "<ukendt enhed>");
		row.setOrgUnitUUID((ou != null) ? ou.getOuUuid() : "");
		row.setUserActive(user.isUserActive());
		row.setRoleId(roleAssignment.getRoleId());
		row.setItSystem(itSystem);
		row.setAssignedBy(assignedBy);
		row.setAssignedWhen(roleAssignment.getAssignedWhen().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime());
		row.setAssignedThrough(assignedThroughStr);
		row.setPostponedConstraints(postponedConstraints);
		row.setStartDate(roleAssignment.getStartDate());
		row.setStopDate(roleAssignment.getStopDate());
		result.add(row);
	}

	private List<HistoryOU> getPositions(Map<String, HistoryOU> orgUnits, HistoryUser user) {
		return orgUnits.values().stream()
			.filter(ou -> ou.getUsers().stream()
				.anyMatch(ouUser -> Objects.equals(ouUser.getUserUuid(), user.getUserUuid())
					&& Boolean.TRUE.equals(ouUser.getHasPosition()))) // Only actual positions
			.collect(Collectors.toList());
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
		Map<String, List<HistoryRoleAssignment>> userRoleAssignments;

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
			userRoleAssignments = historyService.getRoleAssignments(localDate, itSystemFilter);
		}
		else {
			ouRoleAssignments = historyService.getOURoleAssignments(localDate);
			userRoleAssignments = historyService.getRoleAssignments(localDate);
		}

		// add systemRoles based on itSystems after filter
		for (HistoryItSystem itSystem : itSystems) {
			systemRoles.addAll(itSystem.getHistorySystemRoles());
		}

		//Now that we store also the inactive roles we need filter them out
	    filterRoleAssignments(displayDate, ouRoleAssignments);
	    filterRoleAssignments(displayDate, userRoleAssignments);

		// TODO: det er uheldigt at vi filtrerer OU'ere væk... vi skal bruge dem alle sammen, så måske sende både ALLE OU'ere og de filtrerede med rundt (hvis det er relevant),
		//       da vi nu skal nedarve fra dem... og det er lige så fjollet hvis vi fjerner rettigheder, da vi også skal bruge dem... hmmm

		// Filter on manager if specified
		if (StringUtils.hasLength(manager)) {
			ouFilter = orgUnits
					.entrySet()
					.stream()
					.filter(entry -> java.util.Objects.equals(manager, entry.getValue().getOuManagerUuid()))
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
				userRoleAssignments = new HashMap<>();
			}
		}

		// Filter OrgUnits if specified
		if (ouFilter != null && ouFilter.size() > 0) {
			List<String> finalOuFilter = ouFilter;

			// filter the retrieved role assignments and kle assignments
			orgUnits.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));
			ouRoleAssignments.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));
			// doubt we need any filtering actually - but filtering here breaks the report, so lets not do that
//			ouKleAssignments.entrySet().removeIf(e -> !finalOuFilter.contains(e.getKey()));

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
			userRoleAssignments.entrySet().removeIf(e -> !userUUIDs.contains(e.getKey()));
			userKleAssignments.entrySet().removeIf(e -> !userUUIDs.contains(e.getKey()));

			// filter out role assignments where responsible ou is not in ouFilter
			for (Map.Entry<String, List<HistoryRoleAssignment>> entry : userRoleAssignments.entrySet()) {
				entry.getValue().removeIf(v -> !finalOuFilter.contains(v.getOrgUnitUuid()));
			}
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
		model.put("userRoleAssignments", userRoleAssignments);
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
