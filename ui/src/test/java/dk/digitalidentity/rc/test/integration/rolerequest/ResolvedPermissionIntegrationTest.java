package dk.digitalidentity.rc.test.integration.rolerequest;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.service.permission.ResolvedPermissionService;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("Resolved approver permissions, read from the views")
class ResolvedPermissionIntegrationTest extends BaseIntegrationTest {

	private final ResolvedPermissionService resolvedPermissionService;
	private final SettingsService settingsService;
	private final DomainService domainService;

	@BeforeEach
	void setUp() {
		settingsService.setRolerequestApprover(ApproverPermission.global(List.of(ApprovableBy.AUTHRESPONSIBLE)));
	}

	@Test
	@DisplayName("a role that inherits uses its IT system's approver permission")
	void roleInheritsItSystem() {
		// ---- Given ---- //
		UserRole role = userRole(itSystem(ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE))), ApproverPermission.inherit());
		flushAndClear();

		// ---- When / Then ---- //
		assertThat(resolvedPermissionService.userRoleApproverPermission(role.getId()).getApprovableBy())
			.containsExactly(ApprovableBy.MANAGERORSUBSTITUTE);
	}

	@Test
	@DisplayName("a role and IT system that both inherit use the global approver permission")
	void roleAndItSystemInheritGlobal() {
		// ---- Given ---- //
		UserRole role = userRole(itSystem(ApproverPermission.inherit()), ApproverPermission.inherit());
		flushAndClear();

		// ---- When / Then ---- //
		assertThat(resolvedPermissionService.userRoleApproverPermission(role.getId()).getApprovableBy())
			.containsExactly(ApprovableBy.AUTHRESPONSIBLE);
	}

	@Test
	@DisplayName("an empty role approver permission means nobody and does not inherit")
	void emptyDoesNotInherit() {
		// ---- Given ---- //
		UserRole role = userRole(itSystem(ApproverPermission.automatic()), ApproverPermission.nobody());
		flushAndClear();

		// ---- When / Then ---- //
		assertThat(resolvedPermissionService.userRoleApproverPermission(role.getId()).isNobody()).isTrue();
	}

	@Test
	@DisplayName("a role that inherits AUTOMATIC is listed as automatic")
	void inheritedAutomaticIsListed() {
		// ---- Given ---- //
		UserRole role = userRole(itSystem(ApproverPermission.automatic()), ApproverPermission.inherit());
		flushAndClear();

		// ---- When / Then ---- //
		assertThat(resolvedPermissionService.automaticUserRoleIds()).contains(role.getId());
	}

	@Test
	@DisplayName("an explicit role approver permission is used instead of the IT system's")
	void explicitRoleBeatsItSystem() {
		// ---- Given ---- //
		UserRole role = userRole(itSystem(ApproverPermission.automatic()), ApproverPermission.of(List.of(ApprovableBy.SYSTEMRESPONSIBLE)));
		flushAndClear();

		// ---- When / Then ---- //
		assertThat(resolvedPermissionService.userRoleApproverPermission(role.getId()).getApprovableBy())
			.containsExactly(ApprovableBy.SYSTEMRESPONSIBLE);
	}

	@Test
	@DisplayName("a role group that inherits uses the global approver permission")
	void roleGroupInheritsGlobal() {
		// ---- Given ---- //
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName("Økonomi");
		roleGroup.setApproverPermission(ApproverPermission.inherit());
		entityManager.persist(roleGroup);
		flushAndClear();

		// ---- When / Then ---- //
		assertThat(resolvedPermissionService.roleGroupApproverPermission(roleGroup.getId()).getApprovableBy())
			.containsExactly(ApprovableBy.AUTHRESPONSIBLE);
	}

	private ItSystem itSystem(ApproverPermission approverPermission) {
		ItSystem system = new ItSystem();
		system.setUuid(UUID.randomUUID().toString());
		system.setName("Økonomisystem");
		system.setIdentifier("it-system-" + UUID.randomUUID());
		system.setSystemType(ItSystemType.MANUAL);
		system.setLastUpdated(new Date());
		system.setDomain(domainService.getPrimaryDomain());
		system.setApproverPermission(approverPermission);
		entityManager.persist(system);
		return system;
	}

	private UserRole userRole(ItSystem system, ApproverPermission approverPermission) {
		UserRole role = new UserRole();
		role.setUuid(UUID.randomUUID().toString());
		role.setName("Bogholder");
		role.setIdentifier("role-" + UUID.randomUUID());
		role.setItSystem(system);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);
		role.setSystemRoleAssignments(new ArrayList<>());
		role.setOrgUnitFilterOrgUnits(new ArrayList<>());
		role.setDescription("Bogføring");
		role.setApproverPermission(approverPermission);
		entityManager.persist(role);
		return role;
	}
}
