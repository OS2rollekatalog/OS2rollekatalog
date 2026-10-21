package dk.digitalidentity.rc.rolerequest.service.scope;

import static dk.digitalidentity.rc.config.Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID;
import static dk.digitalidentity.rc.config.Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.projections.PostponedConstraintValue;
import dk.digitalidentity.rc.dao.projections.SystemRoleConstraint;
import dk.digitalidentity.rc.util.ConstraintValueUtil;
import dk.digitalidentity.rc.util.OrganisationConstraintUtil;
import dk.digitalidentity.rc.util.UuidUtil;
import lombok.RequiredArgsConstructor;

/**
 * Resolves a user's OU and it-system scope from the constraints on their roles containing a given
 * role catalogue system role.
 */
@Component
@RequiredArgsConstructor
public class SystemRoleScopeResolver {
	private final SystemRoleScopeInputsLoader scopeInputsLoader;
	private final OrganisationConstraintUtil organisationConstraintUtil;
	private final OrgUnitDao orgUnitDao;

	/**
	 * Each OU is in at most one scope, which includes the it-systems of the scope for all OUs, if any.
	 */
	public List<SystemRoleScope> scopesByOrgUnit(final User user, final String systemRoleIdentifier) {
		return groupByOrgUnit(pairedScopes(scopeInputsLoader.load(user, systemRoleIdentifier)));
	}

	/**
	 * The OUs covered by the system role, regardless of it-system.
	 */
	public LimitedToOrgUnits orgUnitScope(final User user, final String systemRoleIdentifier) {
		return orgUnitScope(scopeInputsLoader.load(user, systemRoleIdentifier));
	}

	private List<SystemRoleScope> pairedScopes(final SystemRoleScopeInputs scopeInputs) {
		if (scopeInputs.isEmpty()) {
			return List.of();
		}

		final Map<Long, SystemRoleScope> roleScopesByUserRoleId = scopeInputs.constraintsByRole().entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> new SystemRoleScope(
				getOuConstraintsForUserRole(scopeInputs, entry.getValue()),
				getItSystemConstraintsForUserRole(scopeInputs, entry.getValue()))));

		final Stream<SystemRoleScope> assignmentScopes = scopeInputs.postponedConstraints().stream()
			.collect(Collectors.groupingBy(PostponedConstraintValue::assignmentId))
			.values().stream()
			.map(postponed -> assignmentScope(roleScopesByUserRoleId.get(postponed.getFirst().userRoleId()), postponed));

		return Stream.concat(roleScopesByUserRoleId.values().stream(), assignmentScopes)
			.filter(scope -> scope.orgUnits().type() != LimitedToType.NONE && scope.itSystems().type() != LimitedToType.NONE)
			.toList();
	}

	/**
	 * Per dimension, the role's own constraint if it has one, otherwise the value postponed to this assignment.
	 * @param roleScope {@code null} when the role is not currently assigned
	 */
	private static SystemRoleScope assignmentScope(final SystemRoleScope roleScope, final List<PostponedConstraintValue> postponed) {
		final boolean roleLimitsOrgUnits = roleScope != null && roleScope.orgUnits().type() != LimitedToType.NONE;
		final boolean roleLimitsItSystems = roleScope != null && roleScope.itSystems().type() != LimitedToType.NONE;
		return new SystemRoleScope(
			roleLimitsOrgUnits ? roleScope.orgUnits() : getPostponedOuConstraints(postponed),
			roleLimitsItSystems ? roleScope.itSystems() : getPostponedItSystemConstraints(postponed));
	}

	private static List<SystemRoleScope> groupByOrgUnit(final List<SystemRoleScope> pairedScopes) {
		final LimitedToItSystems everywhere = pairedScopes.stream()
			.filter(scope -> scope.orgUnits().type() == LimitedToType.ALL)
			.map(SystemRoleScope::itSystems)
			.reduce(new LimitedToItSystems(LimitedToType.NONE, Collections.emptySet()), SystemRoleScopeResolver::union);

		final Map<String, LimitedToItSystems> allowedByOrgUnitUuid = pairedScopes.stream()
			.filter(scope -> scope.orgUnits().type() == LimitedToType.CONSTRAINED)
			.flatMap(scope -> scope.orgUnits().orgUnits().stream().map(ouUuid -> Map.entry(ouUuid, scope.itSystems())))
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> union(everywhere, entry.getValue()), SystemRoleScopeResolver::union));

		final Stream<SystemRoleScope> orgUnitScopes = allowedByOrgUnitUuid.entrySet().stream()
			.filter(entry -> !entry.getValue().equals(everywhere))
			.collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.mapping(Map.Entry::getKey, Collectors.toSet())))
			.entrySet().stream()
			.map(entry -> new SystemRoleScope(new LimitedToOrgUnits(LimitedToType.CONSTRAINED, entry.getValue()), entry.getKey()));
		final Stream<SystemRoleScope> allOrgUnitsScope = everywhere.type() == LimitedToType.NONE
			? Stream.empty()
			: Stream.of(new SystemRoleScope(new LimitedToOrgUnits(LimitedToType.ALL, Collections.emptySet()), everywhere));
		return Stream.concat(orgUnitScopes, allOrgUnitsScope).toList();
	}

	private static LimitedToItSystems union(final LimitedToItSystems first, final LimitedToItSystems second) {
		if (first.type() == LimitedToType.ALL || second.type() == LimitedToType.ALL) {
			return new LimitedToItSystems(LimitedToType.ALL, Collections.emptySet());
		}
		if (first.type() == LimitedToType.NONE) {
			return second;
		}
		if (second.type() == LimitedToType.NONE) {
			return first;
		}
		return new LimitedToItSystems(LimitedToType.CONSTRAINED, Stream.concat(first.itSystems().stream(), second.itSystems().stream())
			.collect(Collectors.toSet()));
	}

	private LimitedToOrgUnits orgUnitScope(final SystemRoleScopeInputs scopeInputs) {
		if (scopeInputs.isEmpty()) {
			return new LimitedToOrgUnits(LimitedToType.NONE, Collections.emptySet());
		}
		// Resolve the OU constraint for each role. An empty constraint means "all OUs".
		final List<LimitedToOrgUnits> constraints = scopeInputs.constraintsByRole().values().stream()
			.map(roleConstraints -> getOuConstraintsForUserRole(scopeInputs, roleConstraints))
			.filter(c -> c.type() != LimitedToType.NONE)
			.toList();

		// If any role is unconstrained on OUs, the scope covers all of them.
		if (constraints.stream().anyMatch(c -> c.type() == LimitedToType.ALL)) {
			return new LimitedToOrgUnits(LimitedToType.ALL, Collections.emptySet());
		}

		// Otherwise the user is limited to the union of all VALUE and postponed OU uuid's.
		// Both channels are merged so a postponed constraint can never shadow a concrete VALUE one.
		final Set<String> constrainedTo = Stream.concat(constraints.stream(), Stream.of(getPostponedOuConstraints(scopeInputs.postponedConstraints())))
			.flatMap(c -> c.orgUnits().stream())
			.collect(Collectors.toSet());
		return new LimitedToOrgUnits(constrainedTo.isEmpty() ? LimitedToType.NONE : LimitedToType.CONSTRAINED, constrainedTo);
	}

	/**
	 * NONE when no it-system value is postponed.
	 */
	private static LimitedToItSystems getPostponedItSystemConstraints(final List<PostponedConstraintValue> postponedConstraints) {
		final Set<Long> itSystemIds = postponedValues(postponedConstraints, INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)
			.map(Long::parseLong)
			.collect(Collectors.toSet());
		return new LimitedToItSystems(itSystemIds.isEmpty() ? LimitedToType.NONE : LimitedToType.CONSTRAINED, itSystemIds);
	}

	/**
	 * NONE when no OU is postponed.
	 */
	private static LimitedToOrgUnits getPostponedOuConstraints(final List<PostponedConstraintValue> postponedConstraints) {
		final Set<String> ouUuids = postponedValues(postponedConstraints, INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)
			.filter(UuidUtil::isUuid)
			.collect(Collectors.toSet());
		return new LimitedToOrgUnits(ouUuids.isEmpty() ? LimitedToType.NONE : LimitedToType.CONSTRAINED, ouUuids);
	}

	private static Stream<String> postponedValues(final List<PostponedConstraintValue> postponedConstraints, final String entityId) {
		return postponedConstraints.stream()
			.filter(pc -> entityId.equals(pc.entityId()))
			.map(PostponedConstraintValue::value)
			.filter(StringUtils::isNotBlank)
			.flatMap(c -> Arrays.stream(StringUtils.split(c, ",")))
			.map(String::trim)
			.filter(StringUtils::isNotEmpty);
	}

	/**
	 * NONE when the role postpones the it-system constraint.
	 */
	private LimitedToItSystems getItSystemConstraintsForUserRole(final SystemRoleScopeInputs scopeInputs, final List<SystemRoleConstraint> roleConstraints) {
		if (postpones(roleConstraints, INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)) {
			return new LimitedToItSystems(LimitedToType.NONE, Collections.emptySet());
		}
		final Set<Long> accessibleItSystemIds = constraintValues(scopeInputs, roleConstraints, INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID)
			.map(Long::parseLong)
			.collect(Collectors.toSet());
		return new LimitedToItSystems(accessibleItSystemIds.isEmpty() ? LimitedToType.ALL : LimitedToType.CONSTRAINED, accessibleItSystemIds);
	}

	/**
	 * NONE when the role postpones the OU constraint.
	 */
	private LimitedToOrgUnits getOuConstraintsForUserRole(final SystemRoleScopeInputs scopeInputs, final List<SystemRoleConstraint> roleConstraints) {
		if (postpones(roleConstraints, INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)) {
			return new LimitedToOrgUnits(LimitedToType.NONE, Collections.emptySet());
		}
		final Set<String> accessibleOrgUnitUuids = constraintValues(scopeInputs, roleConstraints, INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID)
			.filter(UuidUtil::isUuid)
			.collect(Collectors.toSet());
		return new LimitedToOrgUnits(accessibleOrgUnitUuids.isEmpty() ? LimitedToType.ALL : LimitedToType.CONSTRAINED, accessibleOrgUnitUuids);
	}

	private static boolean postpones(final List<SystemRoleConstraint> roleConstraints, final String entityId) {
		return roleConstraints.stream()
			.anyMatch(c -> entityId.equals(c.entityId()) && c.type() == ConstraintValueType.POSTPONED);
	}

	private Stream<String> constraintValues(final SystemRoleScopeInputs scopeInputs, final List<SystemRoleConstraint> roleConstraints, final String entityId) {
		return roleConstraints.stream()
			.filter(c -> entityId.equals(c.entityId()))
			.filter(c -> c.type() != ConstraintValueType.POSTPONED)
			.map(c -> resolveConstraintValue(scopeInputs, c))
			.flatMap(c -> Arrays.stream(Objects.requireNonNull(StringUtils.split(c, ","))));
	}

	private String resolveConstraintValue(final SystemRoleScopeInputs scopeInputs, final SystemRoleConstraint constraint) {
		return switch (constraint.type()) {
			case INHERITED ->
				String.join(",", scopeInputs.positionOrgUnitUuids());
			case EXTENDED_INHERITED ->
				String.join(",", withDescendants(scopeInputs.positionOrgUnitUuids()));
			case VALUE, SELECTED_INHERITED ->
				String.join(",", organisationConstraintUtil.getOrganisationConstraintUuids(constraint.value()));
			case INHERITED_FROM_MANAGER_ROLE ->
				String.join(",", scopeInputs.managedOrgUnitUuids());
			case EXTENDED_INHERITED_FROM_MANAGER_ROLE ->
				String.join(",", withDescendants(scopeInputs.managedOrgUnitUuids()));
			case INHERITED_FROM_FUNCTIONS ->
				String.join(",", scopeInputs.functionOrgUnitUuids(ConstraintValueUtil.parseFunctionUuids(constraint.value())));
			case EXTENDED_INHERITED_FROM_FUNCTIONS ->
				String.join(",", withDescendants(scopeInputs.functionOrgUnitUuids(ConstraintValueUtil.parseFunctionUuids(constraint.value()))));
			default ->
				throw new IllegalStateException("OU constraint cannot contain value of type " + constraint.type());
		};
	}

	private Set<String> withDescendants(final Set<String> rootUuids) {
		return rootUuids.isEmpty() ? Set.of() : orgUnitDao.findOuUuidsIncludingDescendants(rootUuids);
	}
}
