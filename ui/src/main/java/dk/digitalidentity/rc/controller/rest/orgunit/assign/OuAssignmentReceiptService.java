package dk.digitalidentity.rc.controller.rest.orgunit.assign;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.controller.rest.orgunit.assign.ScopeRules.ScopeChoice;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.projections.OrgUnitUuidAndName;
import dk.digitalidentity.rc.dao.projections.UserRoleNameAndItSystemName;
import dk.digitalidentity.rc.dao.projections.UserUuidNameAndUserId;
import dk.digitalidentity.rc.service.FunctionService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.TitleService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;

/**
 * Builds the read model behind the wizard's receipt step
 */
@Service
@RequiredArgsConstructor
public class OuAssignmentReceiptService {

	private static final Comparator<String> BY_NAME = Comparator.nullsLast(String::compareToIgnoreCase);

	private final OrgUnitService orgUnitService;
	private final UserRoleService userRoleService;
	private final RoleGroupService roleGroupService;
	private final FunctionService functionService;
	private final TitleService titleService;
	private final UserService userService;

	public record ReceiptSummary(
		String roleName,
		Optional<String> itSystemName,
		String ouName,
		ScopeChoice scope,
		boolean includeSubstitutes,
		List<String> titles,
		List<String> functions,
		List<String> exceptedUsers,
		List<String> exceptedOus
	) {
		public boolean hasItSystem() {
			return itSystemName.isPresent();
		}
	}

	private record RoleNames(String roleName, Optional<String> itSystemName) {

		static RoleNames of(UserRoleNameAndItSystemName role) {
			return new RoleNames(role.name(), Optional.ofNullable(role.itSystemName()));
		}

		static RoleNames of(Named roleGroup) {
			return new RoleNames(roleGroup.name(), Optional.empty());
		}
	}

	private record Named(String name) {
	}

	/**
	 * @return the receipt's read model, or empty when the scope choice is not one the assignment rules allow
	 */
	@Transactional(readOnly = true)
	public Optional<ReceiptSummary> summarise(
		String ouUuid,
		RoleType roleType,
		Long roleId,
		ScopeChoice scopeChoice,
		ScopeFields fields
	) {
		if (fields == null) {
			return Optional.empty();
		}

		return ScopeRules.validated(scopeChoice)
			.flatMap(scope -> roleNamesOf(roleType, roleId).flatMap(
				role -> ouNameOf(ouUuid).map(ouName -> build(role, ouName, scope, fields))));
	}

	private ReceiptSummary build(RoleNames role, String ouName, ScopeChoice scope, ScopeFields fields) {
		return new ReceiptSummary(
			role.roleName(),
			role.itSystemName(),
			ouName,
			scope,
			fields.includeSubstitutes(),
			namesOf(fields.titleUuids(), uuids -> titleService.getByUuidIn(uuids).stream().map(Title::getName)),
			namesOf(fields.functionUuids(), uuids -> functionService.findByUuidInAndActiveTrue(uuids)
				.stream()
				.map(Function::getName)),
			namesOf(fields.exceptedUserUuids(), uuids -> userService
				.findProjectionByUuidIn(uuids, UserUuidNameAndUserId.class)
				.stream()
				.map(UserUuidNameAndUserId::name)),
			namesOf(fields.exceptedOuUuids(), uuids -> orgUnitService
				.findProjectionByUuidIn(uuids, OrgUnitUuidAndName.class)
				.stream()
				.map(OrgUnitUuidAndName::getName))
		);
	}

	private Optional<RoleNames> roleNamesOf(RoleType roleType, Long roleId) {
		return switch (roleType) {
			case USERROLE -> userRoleService.findNameAndItSystemNameById(roleId).map(RoleNames::of);
			case ROLEGROUP -> roleGroupService.findProjectionById(roleId, Named.class).map(RoleNames::of);
		};
	}

	private Optional<String> ouNameOf(String ouUuid) {
		return orgUnitService.findProjectionByUuid(ouUuid, Named.class).stream().findAny().map(Named::name);
	}

	/** Resolves a selection's uuids to names; the domain Function entity shadows java.util.function. */
	@FunctionalInterface
	private interface NameLookup {
		Stream<String> apply(Set<String> uuids);
	}

	/**
	 * Convenience helper for looking up a list of names, returning them sorted
	 */
	private static List<String> namesOf(Set<String> uuids, NameLookup lookup) {
		return uuids == null || uuids.isEmpty() ? List.of() : lookup.apply(uuids).sorted(BY_NAME).toList();
	}

}
