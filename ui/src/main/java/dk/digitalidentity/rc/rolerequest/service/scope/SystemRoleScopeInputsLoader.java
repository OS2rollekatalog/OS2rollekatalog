package dk.digitalidentity.rc.rolerequest.service.scope;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.ManagerSubstituteDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.PositionDao;
import dk.digitalidentity.rc.dao.PostponedConstraintDao;
import dk.digitalidentity.rc.dao.assignment.CurrentAssignmentDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.projections.FunctionOrgUnit;
import dk.digitalidentity.rc.dao.projections.SystemRoleConstraint;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.util.ConstraintValueUtil;
import lombok.RequiredArgsConstructor;

/**
 * Queries only the person facts the user's constraints refer to.
 */
@Component
@RequiredArgsConstructor
public class SystemRoleScopeInputsLoader {
	private static final Set<ConstraintValueType> POSITION_TYPES = Set.of(
		ConstraintValueType.INHERITED, ConstraintValueType.EXTENDED_INHERITED);
	private static final Set<ConstraintValueType> MANAGER_TYPES = Set.of(
		ConstraintValueType.INHERITED_FROM_MANAGER_ROLE, ConstraintValueType.EXTENDED_INHERITED_FROM_MANAGER_ROLE);
	private static final Set<ConstraintValueType> FUNCTION_TYPES = Set.of(
		ConstraintValueType.INHERITED_FROM_FUNCTIONS, ConstraintValueType.EXTENDED_INHERITED_FROM_FUNCTIONS);

	private final CurrentAssignmentDao currentAssignmentDao;
	private final PostponedConstraintDao postponedConstraintDao;
	private final PositionDao positionDao;
	private final ManagerSubstituteDao managerSubstituteDao;
	private final OrgUnitDao orgUnitDao;

	/**
	 * The scope inputs of the user's roles containing the given system role, or none when the user does not have it.
	 */
	public SystemRoleScopeInputs load(final User user, final String systemRoleIdentifier) {
		if (SecurityUtil.isLoggedInUser(user) && !SecurityUtil.hasRole(systemRoleIdentifier)) {
			return SystemRoleScopeInputs.none();
		}
		final List<SystemRoleConstraint> constraints = currentAssignmentDao.findActiveSystemRoleConstraints(
			user.getUuid(), Constants.ROLE_CATALOGUE_IDENTIFIER, systemRoleIdentifier, LocalDate.now());
		if (constraints.isEmpty()) {
			return SystemRoleScopeInputs.none();
		}

		final Set<ConstraintValueType> types = constraints.stream()
			.map(SystemRoleConstraint::type)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		return new SystemRoleScopeInputs(
			constraints.stream().collect(Collectors.groupingBy(SystemRoleConstraint::userRoleId)),
			postponedConstraintDao.findValuesByUserAndSystemRole(user.getUuid(), systemRoleIdentifier),
			anyConstraintOfType(types, POSITION_TYPES) ? positionDao.findOrgUnitUuidsByUserUuid(user.getUuid()) : Set.of(),
			anyConstraintOfType(types, MANAGER_TYPES) ? managedOrgUnitUuids(user.getUuid()) : Set.of(),
			orgUnitUuidsByFunctionUuid(user.getUuid(), constraints)
		);
	}

	private static boolean anyConstraintOfType(final Set<ConstraintValueType> types, final Set<ConstraintValueType> factTypes) {
		return types.stream().anyMatch(factTypes::contains);
	}

	/**
	 * OUs the user manages, plus OUs where the user is a manager's substitute.
	 */
	private Set<String> managedOrgUnitUuids(final String userUuid) {
		return Stream.concat(
				orgUnitDao.findUuidsByManagerUuid(userUuid).stream(),
				managerSubstituteDao.findOrgUnitUuidsBySubstituteUuid(userUuid).stream())
			.collect(Collectors.toSet());
	}

	private Map<String, Set<String>> orgUnitUuidsByFunctionUuid(final String userUuid, final List<SystemRoleConstraint> constraints) {
		final Set<String> functionUuids = constraints.stream()
			.filter(c -> c.type() != null && FUNCTION_TYPES.contains(c.type()))
			.flatMap(c -> ConstraintValueUtil.parseFunctionUuids(c.value()).stream())
			.collect(Collectors.toSet());
		if (functionUuids.isEmpty()) {
			return Map.of();
		}
		return orgUnitDao.findFunctionOrgUnitsByUser(userUuid, functionUuids).stream()
			.collect(Collectors.groupingBy(FunctionOrgUnit::functionUuid,
				Collectors.mapping(FunctionOrgUnit::orgUnitUuid, Collectors.toSet())));
	}
}
