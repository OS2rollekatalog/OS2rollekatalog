package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.rolerequest.model.enums.RequestableBy;
import dk.digitalidentity.rc.rolerequest.model.enums.RequesterOption;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.util.UuidUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.config.Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID;
import static dk.digitalidentity.rc.config.Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID;
import static dk.digitalidentity.rc.config.Constants.ROLE_REQUESTAUTHORIZED;
import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;

/**
 * Service that is responsible for operations in relation to the request authorized role
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestAuthorizedRoleService {
	private final UserRoleService userRoleService;
	private final SettingsService settingsService;
	private final ItSystemService itSystemService;
	private final UserService userService;

	public enum LimitedToType { NONE, ALL, CONSTRAINED }
	public record LimitedToOrgUnits(LimitedToType type, Set<String> orgUnits) {}
	public record LimitedToItSystems(LimitedToType type, Set<Long> itSystems) {}

	/**
	 * Will look through all roles and check if any is setup to can be requested by authorized role
	 */
	@Cacheable(value = CACHE_PREFIX + "AuthorizedRoleCanRequest")
	public boolean requestAuthorizedRoleCanRequest() {
		final RequesterOption globalPermission = settingsService.getRolerequestRequester();
		log.debug("Checking global permission");
		if (containsAuthorized(globalPermission)) {
			return true;
		}
		log.debug("Checking it-systems");
		if (itSystemService.getAll().stream()
			.filter(its -> its.getRequesterPermission() != null)
			.anyMatch(its -> containsAuthorized(its.getRequesterPermission()))) {
			return true;
		}
		log.debug("Checking user-roles");
		return userRoleService.getAll().stream()
			.filter(its -> its.getRequesterPermission() != null)
			.anyMatch(role -> containsAuthorized(role.getRequesterPermission()));
	}

	private static boolean containsAuthorized(final RequesterOption option) {
		log.debug("Options: {}", option.name());
		return option.getRequestPermissions().stream().anyMatch(r -> r.equals(RequestableBy.AUTHORIZED));
	}

	/**
	 * Returns all it-system id's that are accessible by the users request authorized role.
	 * If the user does not have the roles, no it-systems are returned.
	 * If the user has the authorized role, the method will check which it-systems are accessible based on constraints.
	 */
	public LimitedToItSystems accessibleItsSystems(final User user) {
		if (!SecurityUtil.getRoles().contains(ROLE_REQUESTAUTHORIZED)) {
			return new LimitedToItSystems(LimitedToType.NONE, Collections.emptySet());
		}
		// First the values from postponed constraints
		final LimitedToItSystems postponedConstraints = getPostponedItSystemConstraints(user.getUserRoleAssignments());
		if (postponedConstraints.type == LimitedToType.ALL) {
			return postponedConstraints;
		}
		// Now get all non "normal" constraints
		final List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		final List<UserRole> allRoleCatalogueRoles = userService.getAllUserRoles(user, itSystems);
		final List<LimitedToItSystems> constraints = allRoleCatalogueRoles.stream()
			.map(ur -> getItSystemConstraintsForUserRole(user, ur))
			.toList();
		// If any of the result are access to all return that
		final Optional<LimitedToItSystems> allConstraint = constraints.stream()
			.filter(c -> c.type == LimitedToType.ALL)
			.findFirst();
		if (allConstraint.isPresent()) {
			return allConstraint.get();
		}
		// Now combine all constrained it-system id's
		final Set<Long> constrainedTo = Stream.concat(constraints.stream(), Stream.of(postponedConstraints))
			.flatMap(c -> c.itSystems().stream())
			.collect(Collectors.toSet());
		return new LimitedToItSystems(constrainedTo.isEmpty() ? LimitedToType.ALL : LimitedToType.CONSTRAINED, constrainedTo);
	}

	/**
	 * Returns all org units uuids that are accessible by the users request authorized role.
	 * If the user does not have the roles, no OUs are returned.
	 * If the user has the authorized role, the method will check which OUs are accessible based on constraints.
	 */
	public LimitedToOrgUnits accessibleOrgUnits(final User user) {
		if (!SecurityUtil.getRoles().contains(ROLE_REQUESTAUTHORIZED)) {
			return new LimitedToOrgUnits(LimitedToType.NONE, Collections.emptySet());
		}
		// First the values from postponed constraints
		final LimitedToOrgUnits postponedConstraints = getPostponedOuConstraints(user.getUserRoleAssignments());
		if (postponedConstraints.type == LimitedToType.ALL) {
			return postponedConstraints;
		}
		// Now get all non "normal" constraints
		final List<ItSystem> itSystems = itSystemService.findByIdentifier(Constants.ROLE_CATALOGUE_IDENTIFIER);
		final List<UserRole> allRoleCatalogueRoles = userService.getAllUserRoles(user, itSystems);
		final List<LimitedToOrgUnits> constraints = allRoleCatalogueRoles.stream()
			.map(ur -> getOuConstraintsForUserRole(user, ur))
			.toList();
		// If any of the result are access to all return that
		final Optional<LimitedToOrgUnits> allConstraint = constraints.stream()
			.filter(c -> c.type == LimitedToType.ALL)
			.findFirst();
		if (allConstraint.isPresent()) {
			return allConstraint.get();
		}
		// Now combine all constrained ou uuid's
		final Set<String> constrainedTo = Stream.concat(constraints.stream(), Stream.of(postponedConstraints))
			.flatMap(c -> c.orgUnits().stream())
			.collect(Collectors.toSet());
		return new LimitedToOrgUnits(constrainedTo.isEmpty() ? LimitedToType.ALL : LimitedToType.CONSTRAINED, constrainedTo);
	}

	private LimitedToItSystems getPostponedItSystemConstraints(final List<UserUserRoleAssignment> userUserRoleAssignment) {
		final List<LimitedToItSystems> postponedConstraints = userUserRoleAssignment.stream()
			.map(this::getPostponedItSystemConstraints)
			.toList();
		// If any of the returned results have no constraints, we can return immediately
		final Optional<LimitedToItSystems> noConstraints = postponedConstraints.stream().filter(c -> c.type == LimitedToType.ALL).findFirst();
		if (noConstraints.isPresent()) {
			return noConstraints.get();
		}
		final Set<Long> constrainedTo = postponedConstraints.stream()
			.filter(c -> c.type == LimitedToType.CONSTRAINED)
			.flatMap(c -> c.itSystems.stream())
			.collect(Collectors.toSet());
		return new LimitedToItSystems(LimitedToType.CONSTRAINED, constrainedTo);
	}

	private LimitedToItSystems getPostponedItSystemConstraints(final UserUserRoleAssignment userUserRoleAssignment) {
		final Set<Long> itSystemIds = userUserRoleAssignment.getPostponedConstraints().stream()
			.filter(pc -> pc.getSystemRole().getIdentifier().equals(ROLE_REQUESTAUTHORIZED))
			.filter(pc -> INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID.equals(pc.getConstraintType().getEntityId()))
			.map(PostponedConstraint::getValue)
			.flatMap(c -> Arrays.stream(StringUtils.split(c, ",")))
			.map(Long::parseLong)
			.collect(Collectors.toSet());
		return new LimitedToItSystems(LimitedToType.CONSTRAINED, itSystemIds);
	}

	private LimitedToItSystems getItSystemConstraintsForUserRole(final User user, final UserRole userRole) {
		// We do not need to consider user-roles which does not have a system-role with the constraint in question
		boolean relevant = userRole.getSystemRoleAssignments().stream()
			.anyMatch(sra -> ROLE_REQUESTAUTHORIZED.equals(sra.getSystemRole().getIdentifier()));
		if (!relevant) {
			return new LimitedToItSystems(LimitedToType.NONE, Collections.emptySet());
		}
		final Set<Long> accessibleItSystemId = getItSystemConstraintsForSystemRoleAssignment(user, userRole.getSystemRoleAssignments());
		return new LimitedToItSystems(accessibleItSystemId.isEmpty() ? LimitedToType.ALL : LimitedToType.CONSTRAINED, accessibleItSystemId);
	}

	private Set<Long> getItSystemConstraintsForSystemRoleAssignment(final User user, final List<SystemRoleAssignment> systemRoleAssignment) {
		return systemRoleAssignment.stream()
			.filter(sra -> sra.getSystemRole().getIdentifier().equals(ROLE_REQUESTAUTHORIZED))
			.flatMap(sra -> sra.getConstraintValues().stream())
			.filter(cv -> INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID.equals(cv.getConstraintType().getEntityId()))
			.filter(cv -> cv.getConstraintValueType() != ConstraintValueType.POSTPONED)
			.map(cv -> valueForSystemRoleConstraintValue(user, cv))
			.flatMap(c -> Arrays.stream(Objects.requireNonNull(StringUtils.split(c, ","))))
			.map(Long::parseLong)
			.collect(Collectors.toSet());
	}

	private LimitedToOrgUnits getPostponedOuConstraints(final List<UserUserRoleAssignment> userUserRoleAssignment) {
		final List<LimitedToOrgUnits> postponedConstraints = userUserRoleAssignment.stream()
			.map(this::getPostponedOuConstraints)
			.toList();
		// If any of the returned results have no constraints, we can return immediately
		final Optional<LimitedToOrgUnits> noConstraints = postponedConstraints.stream().filter(c -> c.type == LimitedToType.ALL).findFirst();
		if (noConstraints.isPresent()) {
			return noConstraints.get();
		}
		final Set<String> constrainedTo = postponedConstraints.stream()
			.filter(c -> c.type == LimitedToType.CONSTRAINED)
			.flatMap(c -> c.orgUnits.stream())
			.collect(Collectors.toSet());
		return new LimitedToOrgUnits(LimitedToType.CONSTRAINED, constrainedTo);
	}

	private LimitedToOrgUnits getPostponedOuConstraints(final UserUserRoleAssignment userUserRoleAssignment) {
		final Set<String> ouUuid = userUserRoleAssignment.getPostponedConstraints().stream()
			.filter(pc -> pc.getSystemRole().getIdentifier().equals(ROLE_REQUESTAUTHORIZED))
			.filter(pc -> INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID.equals(pc.getConstraintType().getEntityId()))
			.map(PostponedConstraint::getValue)
			.flatMap(c -> Arrays.stream(StringUtils.split(c, ",")))
			.filter(UuidUtil::isUuid)
			.collect(Collectors.toSet());
		return new LimitedToOrgUnits(LimitedToType.CONSTRAINED, ouUuid);
	}

	private LimitedToOrgUnits getOuConstraintsForUserRole(final User user, final UserRole userRole) {
		// We do not need to consider user-roles which does not have a system-role with the constraint in question
		boolean relevant = userRole.getSystemRoleAssignments().stream()
			.anyMatch(sra -> ROLE_REQUESTAUTHORIZED.equals(sra.getSystemRole().getIdentifier()));
		if (!relevant) {
			return new LimitedToOrgUnits(LimitedToType.NONE, Collections.emptySet());
		}
		final Set<String> accessibleOrgUnitUuids = getOuConstraintsForSystemRoleAssignment(user, userRole.getSystemRoleAssignments());
		return new LimitedToOrgUnits(accessibleOrgUnitUuids.isEmpty() ? LimitedToType.ALL : LimitedToType.CONSTRAINED, accessibleOrgUnitUuids);
	}

	private Set<String> getOuConstraintsForSystemRoleAssignment(final User user, final List<SystemRoleAssignment> systemRoleAssignment) {
		return systemRoleAssignment.stream()
			.filter(sra -> sra.getSystemRole().getIdentifier().equals(ROLE_REQUESTAUTHORIZED))
			.flatMap(sra -> sra.getConstraintValues().stream())
			.filter(cv -> INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID.equals(cv.getConstraintType().getEntityId()))
			.filter(cv -> cv.getConstraintValueType() != ConstraintValueType.POSTPONED)
			.map(cv -> valueForSystemRoleConstraintValue(user, cv))
			.flatMap(c -> Arrays.stream(Objects.requireNonNull(StringUtils.split(c, ","))))
			.filter(UuidUtil::isUuid)
			.collect(Collectors.toSet());
	}

	private static String valueForSystemRoleConstraintValue(final User user, final SystemRoleAssignmentConstraintValue constraintValue) {
		return switch (constraintValue.getConstraintValueType()) {
			case INHERITED -> user.getPositions().stream().map(p -> p.getOrgUnit().getUuid()).collect(Collectors.joining(","));
			case EXTENDED_INHERITED -> user.getPositions().stream()
				.flatMap(p -> getRecursive(p.getOrgUnit(), new HashSet<>()).stream())
				.collect(Collectors.joining(","));
			case VALUE -> constraintValue.getConstraintValue();
			default -> throw new IllegalStateException("OU constraint can contain value of type " + constraintValue.getConstraintValueType());
		};
	}

	private static Set<String> getRecursive(final OrgUnit orgUnit, final Set<String> accumulator) {
		if (accumulator.contains(orgUnit.getUuid())) {
			log.error("Same OU encountered twice, must be cyclic ou uuid {}", orgUnit.getUuid());
			return accumulator;
		}
		accumulator.add(orgUnit.getUuid());
		orgUnit.getChildren().forEach(ou -> getRecursive(ou, accumulator));
		return accumulator;
	}

}
