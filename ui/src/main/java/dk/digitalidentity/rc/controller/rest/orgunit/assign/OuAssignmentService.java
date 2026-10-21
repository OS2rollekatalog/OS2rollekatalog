package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.AssignmentException;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.AssignmentTarget;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeChoice;
import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeRule;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.OrgUnitAssignmentService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserRoleService;
import lombok.RequiredArgsConstructor;

/**
 * The one place the wizard's vocabulary meets the legacy assignment API, in both
 * directions. Callers pass and receive only what the UI speaks; the flat argument
 * lists the legacy methods take never leave this class.
 * <p>
 * Whether an assignment is valid at all, independent of who is requesting it - the
 * caller is responsible for having authorized the acting user first.
 */
@Service
@RequiredArgsConstructor
public class OuAssignmentService {

	private final UserRoleService userRoleService;
	private final OrgUnitService orgUnitService;
	private final OrgUnitAssignmentService orgUnitAssignmentService;
	private final MessageSource messageSource;

	/** Everything the wizard needs to pre-fill an edit, in the shape it submits back. */
	public record ExistingAssignment(
			ScopeChoice scopeChoice,
			ScopeFields scopeFields,
			LocalDate startDate,
			LocalDate stopDate,
			String caseNumber) {
	}

	/** The legacy argument list, assembled once and unpacked at the call site. */
	private record ResolvedScope(
			boolean inherit,
			boolean manager,
			boolean substitutes,
			boolean negativeTitles,
			Set<String> titleUuids,
			Set<String> functionUuids,
			Set<String> exceptedUserUuids,
			Set<String> exceptedOuUuids) {

		/** Excepted org units are persisted by their own legacy methods, not the general ones. */
		boolean hasExceptedOus() {
			return !exceptedOuUuids.isEmpty();
		}
	}

	@Transactional
	public void assign(OrgUnit ou, UserRole role, OrgUnitAssignmentRequest request) {
		ResolvedScope scope = resolve(request.scopeChoice(), request.scopeFields());

		validateUserRole(role);
		validateDates(request.startDate(), request.stopDate());

		if (scope.hasExceptedOus()) {
			orgUnitService.addUserRoleWithInheritAndExceptedOus(ou, role, request.startDate(), request.stopDate(),
					new ArrayList<>(scope.exceptedOuUuids()), request.caseNumber());
		}
		else {
			orgUnitService.addUserRole(ou, role, scope.inherit(), request.startDate(),
					request.stopDate(), scope.exceptedUserUuids(), scope.titleUuids(),
					scope.negativeTitles(), scope.manager(),
					scope.substitutes(), scope.functionUuids(), request.caseNumber());
		}

		orgUnitService.save(ou);
	}

	@Transactional
	public void assign(OrgUnit ou, RoleGroup roleGroup, OrgUnitAssignmentRequest request) {
		ResolvedScope scope = resolve(request.scopeChoice(), request.scopeFields());

		validateRoleGroup(roleGroup, scope.inherit());
		validateDates(request.startDate(), request.stopDate());

		if (scope.hasExceptedOus()) {
			orgUnitService.addRoleGroupWithInheritAndExceptedOus(ou, roleGroup, request.startDate(),
					request.stopDate(), new ArrayList<>(scope.exceptedOuUuids()), request.caseNumber());
		}
		else {
			orgUnitService.addRoleGroup(ou, roleGroup, scope.inherit(), request.startDate(),
					request.stopDate(), scope.exceptedUserUuids(), scope.titleUuids(),
					scope.negativeTitles(), scope.manager(),
					scope.substitutes(), scope.functionUuids(), request.caseNumber());
		}

		orgUnitService.save(ou);
	}

	@Transactional
	public void update(OrgUnitUserRoleAssignment assignment, OrgUnitAssignmentRequest request) {
		ResolvedScope scope = resolvePersisted(assignment, request.scopeFields());

		validateUserRole(assignment.getUserRole());
		validateDates(request.startDate(), request.stopDate());

		assignment.setCaseNumber(request.caseNumber());

		if (scope.hasExceptedOus()) {
			orgUnitService.saveUserRoleWithInheritAndExceptedOus(assignment, request.startDate(),
					request.stopDate(), new ArrayList<>(scope.exceptedOuUuids()));
			return;
		}

		orgUnitService.updateUserRoleAssignment(assignment.getOrgUnit(), assignment, scope.inherit(),
				request.startDate(), request.stopDate(), scope.exceptedUserUuids(), scope.titleUuids(),
				scope.negativeTitles(), scope.manager(), scope.substitutes(), scope.functionUuids());
	}

	@Transactional
	public void update(OrgUnitRoleGroupAssignment assignment, OrgUnitAssignmentRequest request) {
		ResolvedScope scope = resolvePersisted(assignment, request.scopeFields());

		validateRoleGroup(assignment.getRoleGroup(), scope.inherit());
		validateDates(request.startDate(), request.stopDate());

		assignment.setCaseNumber(request.caseNumber());

		if (scope.hasExceptedOus()) {
			orgUnitService.saveRoleGroupWithInheritAndExceptedOus(assignment, request.startDate(),
					request.stopDate(), new ArrayList<>(scope.exceptedOuUuids()));
			return;
		}

		orgUnitService.updateRoleGroupAssignment(assignment.getOrgUnit(), assignment, scope.inherit(),
				request.startDate(), request.stopDate(), scope.exceptedUserUuids(), scope.titleUuids(),
				scope.negativeTitles(), scope.manager(), scope.substitutes(), scope.functionUuids());
	}

	@Transactional(readOnly = true)
	public Optional<OrgUnitUserRoleAssignment> findUserRoleAssignment(String ouUuid, long roleId, long assignmentId) {
		return orgUnitAssignmentService.getOrgUnitUserRoleAssignment(assignmentId, ouUuid, roleId);
	}

	@Transactional(readOnly = true)
	public Optional<OrgUnitRoleGroupAssignment> findRoleGroupAssignment(String ouUuid, long roleId, long assignmentId) {
		return orgUnitAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId, ouUuid, roleId);
	}

	@Transactional(readOnly = true)
	public Optional<ExistingAssignment> findExisting(RoleType roleType, String ouUuid, long roleId, long assignmentId) {
		return switch (roleType) {
			case USERROLE -> findUserRoleAssignment(ouUuid, roleId, assignmentId)
					.flatMap(a -> toExistingAssignment(a, a.getStartDate(), a.getStopDate(), a.getCaseNumber()));
			case ROLEGROUP -> findRoleGroupAssignment(ouUuid, roleId, assignmentId)
					.flatMap(a -> toExistingAssignment(a, a.getStartDate(), a.getStopDate(), a.getCaseNumber()));
		};
	}

	@Transactional(readOnly = true)
	public Optional<ExistingAssignment> findExisting(RoleType roleType, long assignmentId) {
		return switch (roleType) {
			case USERROLE -> orgUnitAssignmentService.getOrgUnitUserRoleAssignment(assignmentId)
					.flatMap(a -> toExistingAssignment(a, a.getStartDate(), a.getStopDate(), a.getCaseNumber()));
			case ROLEGROUP -> orgUnitAssignmentService.getOrgUnitRoleGroupAssignment(assignmentId)
					.flatMap(a -> toExistingAssignment(a, a.getStartDate(), a.getStopDate(), a.getCaseNumber()));
		};
	}

	/**
	 * @return the resolved legacy scope, never null
	 * @throws AssignmentValidationException when the choice matches no rule, or a step the
	 * choice requires contributed no field - both mean the wizard submitted something it
	 * should not have
	 */
	private ResolvedScope resolve(ScopeChoice choice, ScopeFields scopeFields) {
		return Optional.ofNullable(scopeFields)
				.flatMap(fields -> ScopeRules.ruleFor(choice)
					.flatMap(rule -> scopeOf(choice, rule, fields)))
				.orElseThrow(() -> new AssignmentValidationException(
						message("html.page.ou.assign.error.unresolvableScope")));
	}

	/** The scope type of an existing assignment cannot be changed, so any submitted choice is ignored. */
	private ResolvedScope resolvePersisted(OrgUnitAssignment assignment, ScopeFields scopeFields) {
		ScopeChoice choice = choiceOf(assignment)
				.orElseThrow(() -> new AssignmentValidationException(
						message("html.page.ou.assign.error.unresolvableScope")));

		return resolve(choice, scopeFields);
	}

	/**
	 * Empty when a set the choice reads was never submitted. A set the choice ignores is
	 * replaced wholesale, so a step the user never reached may legitimately send null.
	 */
	private static Optional<ResolvedScope> scopeOf(ScopeChoice choice, ScopeRule rule, ScopeFields fields) {
		Set<String> titles = ScopeRules.titleModeFor(choice).isPresent() ? fields.titleUuids() : Set.of();
		Set<String> functions = choice.target() == AssignmentTarget.FUNCTION ? fields.functionUuids() : Set.of();
		Set<String> exceptedUsers = choice.exception() == AssignmentException.USERS ? fields.exceptedUserUuids() : Set.of();
		Set<String> exceptedOus = choice.exception() == AssignmentException.OUS ? fields.exceptedOuUuids() : Set.of();

		if (titles == null || functions == null || exceptedUsers == null || exceptedOus == null) {
			return Optional.empty();
		}

		return Optional.of(new ResolvedScope(
			rule.inherit().appliedTo(choice.inherit()),
			choice.target() == AssignmentTarget.MANAGER,
			choice.target() == AssignmentTarget.MANAGER && fields.includeSubstitutes(),
			choice.exception() == AssignmentException.TITLES,
			titles,
			functions,
			exceptedUsers,
			exceptedOus));
	}

	/**
	 * The choice a persisted assignment corresponds to - the inverse of {@link #scopeOf}.
	 * <p>
	 * Empty when the persisted flags match no rule. The flags are independent of each other
	 * in the database while the rules are not, so a combination outside them is possible in
	 * principle; approximating it to the nearest rule would silently drop part of the scope.
	 */
	private static Optional<ScopeChoice> choiceOf(OrgUnitAssignment assignment) {
		ResolvedScope persisted = persistedScopeOf(assignment);

		return ScopeRules.all().stream()
			.map(rule -> new ScopeChoice(rule.target(), rule.exception(), rule.inherit().appliedTo(persisted.inherit())))
			.filter(choice -> describesSameScope(choice, persisted))
			.findFirst();
	}

	/** Re-resolves a candidate choice and compares, so the two directions cannot drift apart. */
	private static boolean describesSameScope(ScopeChoice choice, ResolvedScope persisted) {
		return ScopeRules.ruleFor(choice)
			.flatMap(rule -> scopeOf(choice, rule, fieldsOf(persisted)))
			.filter(persisted::equals)
			.isPresent();
	}

	private static ScopeFields fieldsOf(ResolvedScope scope) {
		return new ScopeFields(scope.exceptedOuUuids(), scope.exceptedUserUuids(), scope.titleUuids(),
			scope.functionUuids(), scope.substitutes());
	}

	private static ResolvedScope persistedScopeOf(OrgUnitAssignment assignment) {
		return new ResolvedScope(
			assignment.isInherit(),
			assignment.isManager(),
			assignment.isSubstitutes(),
			assignment.getContainsTitles() == ContainsTitles.NEGATIVE,
			assignment.getContainsTitles() == ContainsTitles.NO ? Set.of() : uuidsOfTitles(assignment.getTitles()),
			assignment.isContainsFunctions() ? uuidsOfFunctions(assignment.getFunctions()) : Set.of(),
			assignment.isContainsExceptedUsers() ? uuidsOfUsers(assignment.getExceptedUsers()) : Set.of(),
			assignment.isContainsExceptedOus() ? uuidsOfOus(assignment.getExceptedOus()) : Set.of());
	}

	/**
	 * The pre-fill shows every persisted uuid, including sets whose contains-flag is false,
	 * so reopening the wizard does not silently drop a selection the user made earlier.
	 */
	private static Optional<ExistingAssignment> toExistingAssignment(OrgUnitAssignment assignment,
			LocalDate startDate, LocalDate stopDate, String caseNumber) {
		return choiceOf(assignment)
			.map(scopeChoice -> new ExistingAssignment(
				scopeChoice,
				new ScopeFields(
					uuidsOfOus(assignment.getExceptedOus()),
					uuidsOfUsers(assignment.getExceptedUsers()),
					uuidsOfTitles(assignment.getTitles()),
					uuidsOfFunctions(assignment.getFunctions()),
					assignment.isSubstitutes()),
				startDate,
				stopDate,
				caseNumber));
	}

	private static Set<String> uuidsOfTitles(List<Title> items) {
		return items == null ? Set.of() : items.stream().map(Title::getUuid).collect(Collectors.toUnmodifiableSet());
	}

	private static Set<String> uuidsOfFunctions(List<Function> items) {
		return items == null ? Set.of() : items.stream().map(Function::getUuid).collect(Collectors.toUnmodifiableSet());
	}

	private static Set<String> uuidsOfUsers(List<User> items) {
		return items == null ? Set.of() : items.stream().map(User::getUuid).collect(Collectors.toUnmodifiableSet());
	}

	private static Set<String> uuidsOfOus(List<OrgUnit> items) {
		return items == null ? Set.of() : items.stream().map(OrgUnit::getUuid).collect(Collectors.toUnmodifiableSet());
	}

	private void validateUserRole(UserRole role) {
		getBlockedReasonFor(role).ifPresent(reason -> {
			throw new AssignmentValidationException(reason);
		});

		if (role.isUserOnly()) {
			throw new AssignmentValidationException(message("html.page.ou.assign.error.userOnly"));
		}

		if (role.isAllowPostponing()) {
			throw new AssignmentValidationException(message("html.page.ou.assign.error.allowPostponing"));
		}
	}

	private void validateRoleGroup(RoleGroup roleGroup, boolean inherit) {
		List<UserRole> containedRoles = containedRoles(roleGroup);

		containedRoles.stream()
				.map(this::getBlockedReasonFor)
				.flatMap(Optional::stream)
				.findFirst()
				.ifPresent(reason -> {
					throw new AssignmentValidationException(reason);
				});

		if (roleGroup.isUserOnly()) {
			throw new AssignmentValidationException(message("html.page.ou.assign.error.userOnly"));
		}

		if (inherit && containsAdOrReadOnlyRole(containedRoles)) {
			throw new AssignmentValidationException(message("html.page.ou.assign.error.roleGroup.inheritAdRole"));
		}
	}

	private Optional<String> getBlockedReasonFor(UserRole role) {
		return userRoleService.assignmentBlockedReason(role);
	}

	private static List<UserRole> containedRoles(RoleGroup roleGroup) {
		return roleGroup.getUserRoleAssignments().stream()
				.map(RoleGroupUserRoleAssignment::getUserRole)
				.toList();
	}

	private static boolean containsAdOrReadOnlyRole(List<UserRole> roles) {
		return roles.stream()
				.map(UserRole::getItSystem)
				.anyMatch(itSystem -> itSystem != null
						&& (itSystem.getSystemType() == ItSystemType.AD || itSystem.isReadonly()));
	}

	private void validateDates(LocalDate startDate, LocalDate stopDate) {
		if (startDate != null && stopDate != null && !startDate.isBefore(stopDate)) {
			throw new AssignmentValidationException(message("html.page.ou.assign.receipt.stopDate.beforeStartDate"));
		}
	}

	private String message(String key) {
		return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
	}
}
