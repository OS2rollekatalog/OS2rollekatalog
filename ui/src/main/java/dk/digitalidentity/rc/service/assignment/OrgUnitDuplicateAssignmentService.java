package dk.digitalidentity.rc.service.assignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.attestation.service.AttestationLockService;
import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.projections.OrgUnitParentAndManager;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds and removes assignments on orgUnits that have no effect, because another assignment of the
 * same user role / role group already hits exactly the same users - either on the same orgUnit, or
 * inherited from an orgUnit above it. Two assignments are only considered duplicates when their filters are identical.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgUnitDuplicateAssignmentService {
	private static final long SLOW_WARNING_MS = 2000;
	private static final String CLEANUP_LOCK = "CLEANUP_DUPLICATE_ORGUNIT_ASSIGNMENTS";
	// the lock expires after 3 minutes, so a long clean-up refreshes it well before that
	private static final long CLEANUP_LOCK_REFRESH_MS = 60_000;

	private final OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	private final OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	private final OrgUnitDao orgUnitDao;
	private final OrgUnitService orgUnitService;
	private final UserService userService;
	private final AccessConstraintService accessConstraintService;
	private final MessageSource messageSource;
	private final AttestationLockService lockService;

	@PersistenceContext
	private EntityManager entityManager;

	/** An assignment that can be removed, and the assignment it is a duplicate of. */
	public record Duplicate<T extends OrgUnitAssignment>(T redundant, T duplicateOf) {
	}

	public List<Duplicate<OrgUnitUserRoleAssignment>> findDuplicateUserRoleAssignments() {
		long start = System.currentTimeMillis();
		Map<String, String> parentByOrgUnit = getParentByOrgUnit();
		Set<String> visibleOus = getVisibleOus();

		Map<Long, Map<String, List<OrgUnitUserRoleAssignment>>> assignmentsByUserRoleAndOrgUnit = orgUnitUserRoleAssignmentDao.findActiveWithoutStopDate().stream()
			.filter(a -> visibleOus.contains(a.getOrgUnit().getUuid()))
			.collect(Collectors.groupingBy(a -> a.getUserRole().getId(), Collectors.groupingBy(a -> a.getOrgUnit().getUuid())));

		// load the filter lists of all the assignments up front, instead of one query per assignment when they are compared
		orgUnitUserRoleAssignmentDao.fetchTitles();
		orgUnitUserRoleAssignmentDao.fetchFunctions();
		orgUnitUserRoleAssignmentDao.fetchExceptedUsers();
		orgUnitUserRoleAssignmentDao.fetchExceptedOus();

		List<Duplicate<OrgUnitUserRoleAssignment>> result = new ArrayList<>();
		for (Map<String, List<OrgUnitUserRoleAssignment>> assignmentsByOrgUnit : assignmentsByUserRoleAndOrgUnit.values()) {
			for (OrgUnitUserRoleAssignment assignment : assignmentsByOrgUnit.values().stream().flatMap(List::stream).toList()) {
				OrgUnitUserRoleAssignment duplicateOf = findDuplicateAssignment(assignment, assignmentsByOrgUnit, parentByOrgUnit);
				if (duplicateOf != null) {
					result.add(new Duplicate<>(assignment, duplicateOf));
				}
			}
		}

		long duration = System.currentTimeMillis() - start;
		if (duration > SLOW_WARNING_MS) {
			log.warn("Finding duplicate userRoleAssignments on orgUnits took {} ms ({} found)", duration, result.size());
		}

		return result;
	}

	public List<Duplicate<OrgUnitRoleGroupAssignment>> findDuplicateRoleGroupAssignments() {
		long start = System.currentTimeMillis();
		Map<String, String> parentByOrgUnit = getParentByOrgUnit();
		Set<String> visibleOus = getVisibleOus();

		Map<Long, Map<String, List<OrgUnitRoleGroupAssignment>>> assignmentsByRoleGroupAndOrgUnit = orgUnitRoleGroupAssignmentDao.findActiveWithoutStopDate().stream()
			.filter(a -> visibleOus.contains(a.getOrgUnit().getUuid()))
			.collect(Collectors.groupingBy(a -> a.getRoleGroup().getId(), Collectors.groupingBy(a -> a.getOrgUnit().getUuid())));

		// load the filter lists of all the assignments up front, instead of one query per assignment when they are compared
		orgUnitRoleGroupAssignmentDao.fetchTitles();
		orgUnitRoleGroupAssignmentDao.fetchFunctions();
		orgUnitRoleGroupAssignmentDao.fetchExceptedUsers();
		orgUnitRoleGroupAssignmentDao.fetchExceptedOus();

		List<Duplicate<OrgUnitRoleGroupAssignment>> result = new ArrayList<>();
		for (Map<String, List<OrgUnitRoleGroupAssignment>> assignmentsByOrgUnit : assignmentsByRoleGroupAndOrgUnit.values()) {
			for (OrgUnitRoleGroupAssignment assignment : assignmentsByOrgUnit.values().stream().flatMap(List::stream).toList()) {
				OrgUnitRoleGroupAssignment duplicateOf = findDuplicateAssignment(assignment, assignmentsByOrgUnit, parentByOrgUnit);
				if (duplicateOf != null) {
					result.add(new Duplicate<>(assignment, duplicateOf));
				}
			}
		}

		long duration = System.currentTimeMillis() - start;
		if (duration > SLOW_WARNING_MS) {
			log.warn("Finding duplicate roleGroupAssignments on orgUnits took {} ms ({} found)", duration, result.size());
		}

		return result;
	}

	/**
	 * Removes every duplicate user role assignment the logged-in user is allowed to remove.
	 */
	public void removeDuplicateUserRoleAssignments() {
		lockService.acquireLock(CLEANUP_LOCK);
		try {
			long start = System.currentTimeMillis();

			Map<String, List<Long>> duplicateIdsByOrgUnit = findDuplicateUserRoleAssignments().stream()
				.collect(Collectors.groupingBy(o -> o.redundant().getOrgUnit().getUuid(), Collectors.mapping(o -> o.redundant().getId(), Collectors.toList())));

			// only the ids are needed from here on - without this, every save below checks all the assignments loaded to find the duplicates for changes
			entityManager.clear();

			boolean isAdmin = SecurityUtil.getRoles().contains(Constants.ROLE_ADMINISTRATOR) || SecurityUtil.getRoles().contains(Constants.ROLE_SYSTEM);

			long[] lockRefreshedAt = { System.currentTimeMillis() };
			userService.runWithBatchedRecalculation(() -> {
				for (Map.Entry<String, List<Long>> duplicateIds : duplicateIdsByOrgUnit.entrySet()) {
					OrgUnit ou = orgUnitService.getByUuid(duplicateIds.getKey());
					if (ou == null) {
						continue;
					}

					for (long id : duplicateIds.getValue()) {
						OrgUnitUserRoleAssignment assignment = ou.getUserRoleAssignments().stream().filter(a -> a.getId() == id).findAny().orElse(null);
						if (assignment == null || assignment.getUserRole().isReadOnly() || !accessConstraintService.isUserRoleAssignmentAllowed(ou, id)) {
							continue;
						}

						// only administrators may remove Rollekatalog roles
						if (!isAdmin && Constants.ROLE_CATALOGUE_IDENTIFIER.equals(assignment.getUserRole().getItSystem().getIdentifier())) {
							continue;
						}

						orgUnitService.removeUserRoleAssignment(ou, id);
					}

					orgUnitService.save(ou);
					entityManager.clear();

					if (System.currentTimeMillis() - lockRefreshedAt[0] > CLEANUP_LOCK_REFRESH_MS) {
						lockService.refreshLock(CLEANUP_LOCK);
						lockRefreshedAt[0] = System.currentTimeMillis();
					}
				}
			});

			long duration = System.currentTimeMillis() - start;
			if (duration > SLOW_WARNING_MS) {
				log.warn("Removing {} duplicate userRoleAssignments on orgUnits took {} ms", duplicateIdsByOrgUnit.values().stream().mapToInt(List::size).sum(), duration);
			}
		}
		finally {
			lockService.releaseLock(CLEANUP_LOCK);
		}
	}

	/**
	 * Removes every duplicate role group assignment the logged-in user is allowed to remove. The list is
	 * recomputed here, so nothing from the client is trusted.
	 */
	public void removeDuplicateRoleGroupAssignments() {
		lockService.acquireLock(CLEANUP_LOCK);
		try {
			long start = System.currentTimeMillis();

			Map<String, List<Long>> duplicateIdsByOrgUnit = findDuplicateRoleGroupAssignments().stream()
				.collect(Collectors.groupingBy(o -> o.redundant().getOrgUnit().getUuid(), Collectors.mapping(o -> o.redundant().getId(), Collectors.toList())));

			// only the ids are needed from here on - without this, every save below checks all the assignments loaded to find the duplicates for changes
			entityManager.clear();

			long[] lockRefreshedAt = { System.currentTimeMillis() };
			userService.runWithBatchedRecalculation(() -> {
				for (Map.Entry<String, List<Long>> duplicateIds : duplicateIdsByOrgUnit.entrySet()) {
					OrgUnit ou = orgUnitService.getByUuid(duplicateIds.getKey());
					if (ou == null) {
						continue;
					}

					for (long id : duplicateIds.getValue()) {
						boolean stillThere = ou.getRoleGroupAssignments().stream().anyMatch(a -> a.getId() == id);
						if (!stillThere || !accessConstraintService.isUserRoleGroupAssignmentAllowed(ou, id)) {
							continue;
						}

						orgUnitService.removeRoleGroupAssignment(ou, id);
					}

					orgUnitService.save(ou);
					entityManager.clear();

					if (System.currentTimeMillis() - lockRefreshedAt[0] > CLEANUP_LOCK_REFRESH_MS) {
						lockService.refreshLock(CLEANUP_LOCK);
						lockRefreshedAt[0] = System.currentTimeMillis();
					}
				}
			});

			long duration = System.currentTimeMillis() - start;
			if (duration > SLOW_WARNING_MS) {
				log.warn("Removing {} duplicate roleGroupAssignments on orgUnits took {} ms", duplicateIdsByOrgUnit.values().stream().mapToInt(List::size).sum(), duration);
			}
		}
		finally {
			lockService.releaseLock(CLEANUP_LOCK);
		}
	}

	/** Human readable summary of the filters on an assignment, for the report. */
	public String describeFilters(OrgUnitAssignment assignment, Locale locale) {
		List<String> parts = new ArrayList<>();

		if (assignment.getContainsTitles() == ContainsTitles.POSITIVE) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.titles.positive", new Object[] { assignment.getTitles().stream().map(Title::getName).sorted().collect(Collectors.joining(", ")) }, locale));
		}
		if (assignment.getContainsTitles() == ContainsTitles.NEGATIVE) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.titles.negative", new Object[] { assignment.getTitles().stream().map(Title::getName).sorted().collect(Collectors.joining(", ")) }, locale));
		}
		if (assignment.isManager() && assignment.isSubstitutes()) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.managerAndSubstitutes", null, locale));
		}
		else if (assignment.isManager()) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.manager", null, locale));
		}
		if (assignment.isContainsFunctions()) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.functions", new Object[] { assignment.getFunctions().stream().map(Function::getName).sorted().collect(Collectors.joining(", ")) }, locale));
		}
		if (assignment.isContainsExceptedUsers()) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.exceptedUsers", new Object[] { assignment.getExceptedUsers().stream().map(User::getName).sorted().collect(Collectors.joining(", ")) }, locale));
		}
		if (assignment.isContainsExceptedOus()) {
			parts.add(messageSource.getMessage("html.page.report.ouduplicate.filter.exceptedOus", new Object[] { assignment.getExceptedOus().stream().map(OrgUnit::getName).sorted().collect(Collectors.joining(", ")) }, locale));
		}

		if (parts.isEmpty()) {
			return messageSource.getMessage("html.page.report.ouduplicate.filter.none", null, locale);
		}

		return String.join("; ", parts);
	}

	/** Describes the assignment the redundant one is a duplicate of */
	public String describeDuplicateOf(OrgUnit redundantOu, OrgUnit duplicateOfOu, Locale locale) {
		if (Objects.equals(redundantOu.getUuid(), duplicateOfOu.getUuid())) {
			return messageSource.getMessage("html.page.report.ouduplicate.duplicateOf.sameOu", new Object[] { duplicateOfOu.getName() }, locale);
		}

		return messageSource.getMessage("html.page.report.ouduplicate.duplicateOf.inherited", new Object[] { duplicateOfOu.getName() }, locale);
	}

	/** Active orgUnits that are not hidden by the excluded orgUnits setting. */
	private Set<String> getVisibleOus() {
		return orgUnitService.getAllCached().stream().map(OrgUnit::getUuid).collect(Collectors.toSet());
	}

	private Map<String, String> getParentByOrgUnit() {
		Map<String, String> parentByOrgUnit = new HashMap<>();
		for (OrgUnitParentAndManager ou : orgUnitDao.findAllParentAndManager()) {
			parentByOrgUnit.put(ou.uuid(), ou.parentUuid());
		}

		return parentByOrgUnit;
	}

	/**
	 * Returns the assignment that makes this one unnecessary, or null if there is none. The other assignments of
	 * the same role are given grouped by orgUnit uuid. An assignment is
	 * unnecessary when an assignment of the same role with identical filters already reaches the same users:
	 * <ol>
	 * <li>an inherited assignment on an orgUnit above it, or</li>
	 * <li>another assignment on the same orgUnit</li>
	 * </ol>
	 */
	static <T extends OrgUnitAssignment> T findDuplicateAssignment(T assignment, Map<String, List<T>> assignmentsByOrgUnit, Map<String, String> parentByOrgUnit) {
		T inheritedDuplicate = findInheritedDuplicateAssignment(assignment, assignmentsByOrgUnit, parentByOrgUnit);
		if (inheritedDuplicate != null) {
			return inheritedDuplicate;
		}

		return findDuplicateAssignmentOnSameOrgUnit(assignment, assignmentsByOrgUnit);
	}

	/**
	 * Walks up from the assignment's orgUnit and returns the topmost inherited assignment with identical filters.
	 * The filters are identical, so the inherited assignment has the same excepted orgUnits as this one - if one of
	 * them is passed on the way up, the inheritance does not reach this assignment's orgUnit.
	 */
	private static <T extends OrgUnitAssignment> T findInheritedDuplicateAssignment(T assignment, Map<String, List<T>> assignmentsByOrgUnit, Map<String, String> parentByOrgUnit) {
		Set<String> exceptedOus = assignment.isContainsExceptedOus()
			? assignment.getExceptedOus().stream().map(OrgUnit::getUuid).collect(Collectors.toSet())
			: Set.of();
		T topmost = null;

		String child = assignment.getOrgUnit().getUuid();
		String parent = parentByOrgUnit.get(child);
		while (parent != null && !exceptedOus.contains(child)) {
			for (T other : assignmentsByOrgUnit.getOrDefault(parent, List.of())) {
				if (other.isInherit() && sameFilters(other, assignment)) {
					topmost = other;
				}
			}

			child = parent;
			parent = parentByOrgUnit.get(parent);
		}

		return topmost;
	}

	/**
	 * Returns another assignment on the same orgUnit with identical filters that should be kept instead of this
	 * one: an inherited assignment is kept over a non-inherited one, otherwise the one assigned first is kept.
	 */
	private static <T extends OrgUnitAssignment> T findDuplicateAssignmentOnSameOrgUnit(T assignment, Map<String, List<T>> assignmentsByOrgUnit) {
		for (T other : assignmentsByOrgUnit.get(assignment.getOrgUnit().getUuid())) {
			if (other == assignment || !sameFilters(other, assignment)) {
				continue;
			}

			boolean otherInheritsAndThisDoesNot = other.isInherit() && !assignment.isInherit();
			boolean bothInheritTheSameAndOtherIsOlder = other.isInherit() == assignment.isInherit() && isAssignedBefore(other, assignment);
			if (otherInheritsAndThisDoesNot || bothInheritTheSameAndOtherIsOlder) {
				return other;
			}
		}

		return null;
	}

	/**
	 * True when the first assignment was assigned before the second. Assignments with the same timestamp (e.g. created
	 * in bulk) are ordered by id, so one of two identical assignments is always kept.
	 */
	private static boolean isAssignedBefore(OrgUnitAssignment first, OrgUnitAssignment second) {
		int byTimestamp = first.getAssignedTimestamp().compareTo(second.getAssignedTimestamp());
		if (byTimestamp != 0) {
			return byTimestamp < 0;
		}

		return first.getId() < second.getId();
	}

	/** The lists only count when their flag is set - that is also how the assignment rules read them. */
	private static boolean sameFilters(OrgUnitAssignment a, OrgUnitAssignment b) {
		ContainsTitles aTitles = a.getContainsTitles() != null ? a.getContainsTitles() : ContainsTitles.NO;
		ContainsTitles bTitles = b.getContainsTitles() != null ? b.getContainsTitles() : ContainsTitles.NO;

		boolean sameFlags = aTitles == bTitles
			&& a.isManager() == b.isManager()
			&& a.isSubstitutes() == b.isSubstitutes()
			&& a.isContainsFunctions() == b.isContainsFunctions()
			&& a.isContainsExceptedUsers() == b.isContainsExceptedUsers()
			&& a.isContainsExceptedOus() == b.isContainsExceptedOus();
		if (!sameFlags) {
			return false;
		}

		// compared as sets of uuids, as the lists come from the database in no particular order
		if (aTitles != ContainsTitles.NO) {
			Set<String> aTitleUuids = a.getTitles().stream().map(Title::getUuid).collect(Collectors.toSet());
			Set<String> bTitleUuids = b.getTitles().stream().map(Title::getUuid).collect(Collectors.toSet());
			if (!aTitleUuids.equals(bTitleUuids)) {
				return false;
			}
		}

		if (a.isContainsFunctions()) {
			Set<String> aFunctionUuids = a.getFunctions().stream().map(Function::getUuid).collect(Collectors.toSet());
			Set<String> bFunctionUuids = b.getFunctions().stream().map(Function::getUuid).collect(Collectors.toSet());
			if (!aFunctionUuids.equals(bFunctionUuids)) {
				return false;
			}
		}

		if (a.isContainsExceptedUsers()) {
			Set<String> aUserUuids = a.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toSet());
			Set<String> bUserUuids = b.getExceptedUsers().stream().map(User::getUuid).collect(Collectors.toSet());
			if (!aUserUuids.equals(bUserUuids)) {
				return false;
			}
		}

		if (a.isContainsExceptedOus()) {
			Set<String> aOrgUnitUuids = a.getExceptedOus().stream().map(OrgUnit::getUuid).collect(Collectors.toSet());
			Set<String> bOrgUnitUuids = b.getExceptedOus().stream().map(OrgUnit::getUuid).collect(Collectors.toSet());
			if (!aOrgUnitUuids.equals(bOrgUnitUuids)) {
				return false;
			}
		}

		return true;
	}
}
