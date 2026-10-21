package dk.digitalidentity.rc.test.integration.rolerequest;

import dk.digitalidentity.rc.rolerequest.dao.RoleRequestDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitNearestManager;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.RoleGroupUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.dao.model.enums.OrgUnitLevel;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.dao.model.enums.SystemRoleLinkType;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.rolerequest.model.permission.ApproverPermission;
import dk.digitalidentity.rc.rolerequest.service.permission.ApprovableRequestService;
import dk.digitalidentity.rc.rolerequest.service.permission.ApproverContext;
import dk.digitalidentity.rc.rolerequest.service.permission.ApproverContext.AuthorizedScope;
import dk.digitalidentity.rc.service.DomainService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.TestConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DisplayName("ApproverRule, applied to stored requests")
class ApproverRuleIntegrationTest extends BaseIntegrationTest {

	private final ApprovableRequestService approvableRequestService;
	private final DomainService domainService;
	private final RoleRequestDao roleRequestDao;

	private User requester;
	private User receiver;
	private User approver;
	private OrgUnit orgUnit;
	private ItSystem itSystem;
	private UserRole userRole;

	@BeforeEach
	void setUp() {
		requester = user("Anmoder");
		receiver = user("Modtager");
		approver = user("Godkender");
		orgUnit = orgUnit();
		position(receiver, orgUnit);
		itSystem = itSystem();
		userRole = userRole(itSystem);
	}

	@Nested
	@DisplayName("Order of the checks")
	class Order {

		@Test
		@DisplayName("an admin may approve a request they created for themselves")
		void adminApprovesOwnRequestForThemselves() {
			// ---- Given ---- //
			RoleRequest request = request(approver, approver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			ContextBuilder context = new ContextBuilder();
			context.admin = true;

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isTrue();
		}

		@Test
		@DisplayName("the receiver never approves, even an automatic request")
		void receiverNeverApproves() {
			// ---- Given ---- //
			RoleRequest request = request(requester, approver, userRole, null, ApproverPermission.automatic());

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isFalse();
		}

		@Test
		@DisplayName("the creator may not approve without the self-approval setting")
		void creatorBlockedWithoutSetting() {
			// ---- Given ---- //
			RoleRequest request = request(approver, receiver, userRole, null, ApproverPermission.automatic());

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isFalse();
		}

		@Test
		@DisplayName("the creator may approve with the self-approval setting")
		void creatorAllowedWithSetting() {
			// ---- Given ---- //
			RoleRequest request = request(approver, receiver, userRole, null, ApproverPermission.automatic());
			ContextBuilder context = new ContextBuilder();
			context.allowSelfApproval = true;

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isTrue();
		}

		@Test
		@DisplayName("an automatic request may be approved by anyone")
		void automaticForAnyone() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.automatic());

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isTrue();
		}

		@Test
		@DisplayName("a request without a stored approver setting may only be approved by admins")
		void emptySettingOnlyForAdmins() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.nobody());

			// ---- When / Then ---- //
			assertThat(mayApprove(request, grantingAllTypesExcept(null))).isFalse();
		}
	}

	@Nested
	@DisplayName("Approver types")
	class Types {

		@ParameterizedTest(name = "{0}")
		@EnumSource(value = ApprovableBy.class, names = { "SYSTEMRESPONSIBLE", "AUTHORIZED", "APPROVEAUTHORIZED", "MANAGERORSUBSTITUTE", "AUTHRESPONSIBLE" })
		@DisplayName("each type is allowed when granted")
		void typeAllowedWhenGranted(ApprovableBy type) {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(type)));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, granting(type))).isTrue();
		}

		@ParameterizedTest(name = "{0}")
		@EnumSource(value = ApprovableBy.class, names = { "SYSTEMRESPONSIBLE", "AUTHORIZED", "APPROVEAUTHORIZED", "MANAGERORSUBSTITUTE", "AUTHRESPONSIBLE" })
		@DisplayName("each type is not allowed when only the other types are granted")
		void typeNotAllowedWhenOthersGranted(ApprovableBy type) {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(type)));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, grantingAllTypesExcept(type))).isFalse();
		}

	}

	@Nested
	@DisplayName("Nearest manager")
	class NearestManager {
		private User nearestManager;
		private OrgUnit nextManagerOrgUnit;

		@BeforeEach
		void setUp() {
			nearestManager = user("Afdelingsleder");
			nextManagerOrgUnit = orgUnit();
		}

		@Test
		@DisplayName("a request for the nearest manager goes to the next manager")
		void receiverIsNearestManager() {
			// ---- Given ---- //
			RoleRequest request = request(requester, nearestManager, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			nearestManagers(nearestManager, approver, nextManagerOrgUnit);

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isTrue();
		}

		@Test
		@DisplayName("the next manager does not approve requests for anyone but the nearest manager")
		void nextManagerOnlyForNearestManager() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			nearestManagers(nearestManager, approver, nextManagerOrgUnit);

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isFalse();
		}

		@Test
		@DisplayName("a substitute for the nearest manager in the OU where the manager was found may approve")
		void substituteInManagersOrgUnit() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			nearestManagers(nearestManager, null, null);
			substitute(nearestManager, approver, orgUnit);

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isTrue();
		}

		@Test
		@DisplayName("a substitute for the nearest manager in another OU may not approve")
		void substituteInOtherOrgUnit() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			nearestManagers(nearestManager, null, null);
			substitute(nearestManager, approver, nextManagerOrgUnit);

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isFalse();
		}

		@Test
		@DisplayName("a substitute for the next manager may approve a request for the nearest manager")
		void substituteForNextManager() {
			// ---- Given ---- //
			User nextManager = user("Direktør");
			RoleRequest request = request(requester, nearestManager, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			nearestManagers(nearestManager, nextManager, nextManagerOrgUnit);
			substitute(nextManager, approver, nextManagerOrgUnit);

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isTrue();
		}
	}

	@Nested
	@DisplayName("Role groups")
	class RoleGroups {

		@Test
		@DisplayName("a system responsible for every contained IT system may approve")
		void systemResponsibleForAll() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, null, roleGroup(userRole), ApproverPermission.of(List.of(ApprovableBy.SYSTEMRESPONSIBLE)));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, granting(ApprovableBy.SYSTEMRESPONSIBLE))).isTrue();
		}

		@Test
		@DisplayName("a system responsible for only some contained IT systems may not approve")
		void systemResponsibleForSome() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, null, roleGroup(userRole, userRole(itSystem())),
				ApproverPermission.of(List.of(ApprovableBy.SYSTEMRESPONSIBLE)));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, granting(ApprovableBy.SYSTEMRESPONSIBLE))).isFalse();
		}

		@Test
		@DisplayName("an empty role group can be approved as authorized with access to all IT systems")
		void authorizedEmptyGroup() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, null, roleGroup(), ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, granting(ApprovableBy.AUTHORIZED))).isTrue();
		}
	}

	@Nested
	@DisplayName("Pending list")
	class PendingList {

		@Test
		@DisplayName("hides a request for a read-only role that can still be approved")
		void hidesReadOnlyRole() {
			// ---- Given ---- //
			userRole.setReadOnly(true);
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.automatic());
			flushAndClear();

			// ---- When ---- //
			List<Long> pendingIds = approvableRequestService.pendingApprovableRequests(approver).stream().map(RoleRequest::getId).toList();

			// ---- Then ---- //
			assertThat(pendingIds).doesNotContain(request.getId());
			assertThat(approvableRequestService.canApprove(request, approver)).isTrue();
		}
	}

	@Nested
	@DisplayName("Regressions")
	class Regressions {

		@Test
		@DisplayName("an authorization responsible for a different OU than the receiver's may not approve")
		void authResponsibleForOtherOrgUnit() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHRESPONSIBLE)));
			ContextBuilder context = new ContextBuilder();
			context.authorizationManagerOuUuids = Set.of(orgUnit().getUuid());

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isFalse();
		}

		@Test
		@DisplayName("an authorized user whose OU scope does not include the request's OU may not approve")
		void authorizedOutsideOrgUnitScope() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = granting(ApprovableBy.AUTHORIZED);
			context.authorizedScopes = List.of(new AuthorizedScope(Set.of(orgUnit().getUuid()), null));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isFalse();
		}

		@Test
		@DisplayName("an authorized user whose IT-system scope does not include the role's IT system may not approve")
		void authorizedOutsideItSystemScope() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = granting(ApprovableBy.AUTHORIZED);
			context.authorizedScopes = List.of(new AuthorizedScope(Set.of(orgUnit.getUuid()), Set.of(itSystem().getId())));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isFalse();
		}

		@Test
		@DisplayName("an authorized user may not combine the OU scope of one role with the IT-system scope of another")
		void authorizedScopesAreNotCrossProducted() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = new ContextBuilder();
			context.authorizedScopes = List.of(
				new AuthorizedScope(Set.of(orgUnit().getUuid()), null),
				new AuthorizedScope(null, Set.of(itSystem().getId())));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isFalse();
		}

		@Test
		@DisplayName("an authorized user may approve a role group whose IT systems are all allowed in the request's OU")
		void roleGroupCoveredByScopesForRequestOrgUnit() {
			// ---- Given ---- //
			ItSystem otherItSystem = itSystem();
			RoleRequest request = request(requester, receiver, null, roleGroup(userRole, userRole(otherItSystem)), ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = new ContextBuilder();
			context.authorizedScopes = List.of(
				new AuthorizedScope(Set.of(orgUnit.getUuid()), Set.of(itSystem.getId(), otherItSystem.getId())));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isTrue();
		}

		@Test
		@DisplayName("an authorized user may not approve a role group when a contained IT system is only covered for another OU")
		void roleGroupPartlyCoveredForOtherOrgUnit() {
			// ---- Given ---- //
			ItSystem otherItSystem = itSystem();
			RoleRequest request = request(requester, receiver, null, roleGroup(userRole, userRole(otherItSystem)), ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = new ContextBuilder();
			context.authorizedScopes = List.of(
				new AuthorizedScope(Set.of(orgUnit.getUuid()), Set.of(itSystem.getId())),
				new AuthorizedScope(Set.of(orgUnit().getUuid()), Set.of(otherItSystem.getId())));

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isFalse();
		}

		@Test
		@DisplayName("a deleted user never approves, even as admin")
		void deletedNeverApproves() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.automatic());
			ContextBuilder context = new ContextBuilder();
			context.admin = true;
			context.deleted = true;

			// ---- When / Then ---- //
			assertThat(mayApprove(request, context)).isFalse();
		}

		@Test
		@DisplayName("the setting stored on the request decides, not the role's current setting")
		void storedSettingDecides() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			userRole.setApproverPermission(ApproverPermission.automatic());

			// ---- When / Then ---- //
			assertThat(mayApprove(request, new ContextBuilder())).isFalse();
		}

		@Test
		@DisplayName("a request without an OU cannot be approved as nearest manager")
		void requestWithoutOrgUnit() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.MANAGERORSUBSTITUTE)));
			request.setOrgUnit(null);

			// ---- When / Then ---- //
			assertThat(mayApprove(request, granting(ApprovableBy.MANAGERORSUBSTITUTE))).isFalse();
		}

		@Test
		@DisplayName("a request without an OU does not break the pending list")
		void pendingListWithRequestWithoutOrgUnit() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.automatic());
			request.setOrgUnit(null);
			flushAndClear();

			// ---- When ---- //
			List<Long> pendingIds = approvableRequestService.pendingApprovableRequests(approver).stream().map(RoleRequest::getId).toList();

			// ---- Then ---- //
			assertThat(pendingIds).contains(request.getId());
		}
	}

	@Nested
	@DisplayName("Loading requests for approval emails")
	class ApprovalEmails {

		@Test
		@DisplayName("only loads pending requests that have not been emailed yet, unless asked for all")
		void notEmailedOnly() {
			// ---- Given ---- //
			RoleRequest emailed = request(requester, receiver, userRole, null, ApproverPermission.automatic());
			emailed.setEmailSent(true);
			RoleRequest notEmailed = request(requester, receiver, userRole, null, ApproverPermission.automatic());
			flushAndClear();

			// ---- When ---- //
			List<Long> notEmailedIds = roleRequestDao.findPendingForApprovalEmails(false).stream().map(RoleRequest::getId).toList();
			List<Long> allIds = roleRequestDao.findPendingForApprovalEmails(true).stream().map(RoleRequest::getId).toList();

			// ---- Then ---- //
			assertThat(notEmailedIds).contains(notEmailed.getId()).doesNotContain(emailed.getId());
			assertThat(allIds).contains(notEmailed.getId(), emailed.getId());
		}

		@Test
		@DisplayName("an admin outside their authorized IT-system scope is not emailed")
		void adminOutsideItSystemScopeNotEmailed() {
			// ---- Given ---- //
			RoleRequest request = request(requester, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = granting(ApprovableBy.AUTHORIZED);
			context.authorizedScopes = List.of(new AuthorizedScope(Set.of(orgUnit.getUuid()), Set.of(itSystem().getId())));
			context.admin = true;

			// ---- When / Then ---- //
			assertThat(isEmailRecipient(request, context)).isFalse();
		}

		@Test
		@DisplayName("an admin who created the request is not emailed without the self-approval setting")
		void adminCreatorNotEmailed() {
			// ---- Given ---- //
			RoleRequest request = request(approver, receiver, userRole, null, ApproverPermission.of(List.of(ApprovableBy.AUTHORIZED)));
			ContextBuilder context = granting(ApprovableBy.AUTHORIZED);
			context.admin = true;

			// ---- When / Then ---- //
			assertThat(isEmailRecipient(request, context)).isFalse();
		}
	}

	private final class ContextBuilder {
		boolean admin;
		boolean deleted;
		boolean allowSelfApproval;
		Set<String> authorizationManagerOuUuids = Set.of();
		Set<Long> systemResponsibleItSystemIds = Set.of();
		List<AuthorizedScope> authorizedScopes = List.of();
		List<AuthorizedScope> approveAuthorizedScopes = List.of();

		ApproverContext build() {
			return new ApproverContext(approver.getUuid(), admin, deleted, allowSelfApproval, authorizationManagerOuUuids,
				systemResponsibleItSystemIds, authorizedScopes, approveAuthorizedScopes);
		}
	}

	private ContextBuilder granting(ApprovableBy type) {
		ContextBuilder context = new ContextBuilder();
		switch (type) {
			case SYSTEMRESPONSIBLE -> context.systemResponsibleItSystemIds = Set.of(itSystem.getId());
			case AUTHORIZED -> context.authorizedScopes = List.of(new AuthorizedScope(Set.of(orgUnit.getUuid()), null));
			case APPROVEAUTHORIZED -> context.approveAuthorizedScopes = List.of(new AuthorizedScope(Set.of(orgUnit.getUuid()), null));
			case MANAGERORSUBSTITUTE -> nearestManagers(approver, null, null);
			case AUTHRESPONSIBLE -> context.authorizationManagerOuUuids = Set.of(orgUnit.getUuid());
			default -> throw new IllegalArgumentException(type.name());
		}
		return context;
	}

	private ContextBuilder grantingAllTypesExcept(ApprovableBy excluded) {
		ContextBuilder context = new ContextBuilder();
		EnumSet<ApprovableBy> types = EnumSet.of(ApprovableBy.SYSTEMRESPONSIBLE, ApprovableBy.AUTHORIZED, ApprovableBy.APPROVEAUTHORIZED, ApprovableBy.MANAGERORSUBSTITUTE, ApprovableBy.AUTHRESPONSIBLE);
		if (excluded != null) {
			types.remove(excluded);
		}
		for (ApprovableBy type : types) {
			ContextBuilder granting = granting(type);
			switch (type) {
				case SYSTEMRESPONSIBLE -> context.systemResponsibleItSystemIds = granting.systemResponsibleItSystemIds;
				case AUTHORIZED -> context.authorizedScopes = granting.authorizedScopes;
				case APPROVEAUTHORIZED -> context.approveAuthorizedScopes = granting.approveAuthorizedScopes;
				// granting() has already stored the nearest-manager row
				case MANAGERORSUBSTITUTE -> {
				}
				case AUTHRESPONSIBLE -> context.authorizationManagerOuUuids = granting.authorizationManagerOuUuids;
				default -> throw new IllegalArgumentException(type.name());
			}
		}
		return context;
	}

	private boolean mayApprove(RoleRequest request, ContextBuilder context) {
		return mayApprove(request, context.build());
	}

	private boolean isEmailRecipient(RoleRequest request, ContextBuilder context) {
		return mayApprove(request, context.build().withoutAdminBypass());
	}

	private boolean mayApprove(RoleRequest request, ApproverContext context) {
		flushAndClear();
		return roleRequestDao.findIds(approvableRequestService.approvable(context)
			.and((root, _, cb) -> cb.equal(root.get("id"), request.getId())))
			.contains(request.getId());
	}

	private RoleRequest request(User requestedBy, User requestedFor, UserRole role, RoleGroup roleGroup, ApproverPermission approverOption) {
		RoleRequest request = RoleRequest.builder()
			.requester(requestedBy)
			.receiver(requestedFor)
			.orgUnit(orgUnit)
			.userRole(role)
			.roleGroup(roleGroup)
			.status(RequestApproveStatus.REQUESTED)
			.requestAction(RequestAction.ADD)
			.requestGroupIdentifier(UUID.randomUUID().toString())
			.requestTimestamp(new Date())
			.statusTimestamp(new Date())
			.approverOption(approverOption)
			.build();
		entityManager.persist(request);
		return request;
	}

	private User user(String name) {
		User user = new User();
		user.setUuid(UUID.randomUUID().toString());
		user.setExtUuid(UUID.randomUUID().toString());
		user.setUserId("user-" + UUID.randomUUID());
		user.setName(name);
		user.setDomain(domainService.getPrimaryDomain());
		user.setPositions(new ArrayList<>());
		user.setManagerSubstitutes(new ArrayList<>());
		entityManager.persist(user);
		return user;
	}

	private void position(User user, OrgUnit positionOrgUnit) {
		Position position = new Position();
		position.setName("Sagsbehandler");
		position.setUser(user);
		position.setOrgUnit(positionOrgUnit);
		entityManager.persist(position);
		user.getPositions().add(position);
	}

	private OrgUnit orgUnit() {
		OrgUnit unit = new OrgUnit();
		unit.setUuid(UUID.randomUUID().toString());
		unit.setName("Økonomi");
		unit.setActive(true);
		unit.setLevel(OrgUnitLevel.NONE);
		unit.setChildren(new ArrayList<>());
		unit.setUserRoleAssignments(new ArrayList<>());
		unit.setRoleGroupAssignments(new ArrayList<>());
		unit.setKles(new ArrayList<>());
		unit.setTitles(new ArrayList<>());
		unit.setFunctionAssignments(new ArrayList<>());
		entityManager.persist(unit);
		return unit;
	}

	private void nearestManagers(User nearest, User next, OrgUnit nextOrgUnit) {
		entityManager.persist(new OrgUnitNearestManager(orgUnit.getUuid(), nearest.getUuid(), orgUnit.getUuid(),
			next == null ? null : next.getUuid(), nextOrgUnit == null ? null : nextOrgUnit.getUuid()));
	}

	private void substitute(User manager, User substitute, OrgUnit substituteOrgUnit) {
		ManagerSubstitute managerSubstitute = new ManagerSubstitute();
		managerSubstitute.setManager(manager);
		managerSubstitute.setSubstitute(substitute);
		managerSubstitute.setOrgUnit(substituteOrgUnit);
		entityManager.persist(managerSubstitute);
	}

	private ItSystem itSystem() {
		ItSystem system = new ItSystem();
		system.setUuid(UUID.randomUUID().toString());
		system.setName("Økonomisystem");
		system.setIdentifier("it-system-" + UUID.randomUUID());
		system.setSystemType(ItSystemType.MANUAL);
		system.setLastUpdated(new Date());
		system.setDomain(domainService.getPrimaryDomain());
		entityManager.persist(system);
		return system;
	}

	private UserRole userRole(ItSystem system) {
		UserRole role = new UserRole();
		role.setUuid(UUID.randomUUID().toString());
		role.setName("Bogholder");
		role.setIdentifier("role-" + UUID.randomUUID());
		role.setItSystem(system);
		role.setSystemRoleLinkType(SystemRoleLinkType.NONE);
		role.setSystemRoleAssignments(new ArrayList<>());
		role.setOrgUnitFilterOrgUnits(new ArrayList<>());
		role.setDescription("Bogføring");
		entityManager.persist(role);
		return role;
	}

	private RoleGroup roleGroup(UserRole... userRoles) {
		RoleGroup roleGroup = new RoleGroup();
		roleGroup.setName("Økonomi");
		roleGroup.setUserRoleAssignments(new ArrayList<>());
		roleGroup.setOrgUnitFilterOrgUnits(new ArrayList<>());
		entityManager.persist(roleGroup);
		for (UserRole role : userRoles) {
			RoleGroupUserRoleAssignment assignment = new RoleGroupUserRoleAssignment();
			assignment.setRoleGroup(roleGroup);
			assignment.setUserRole(role);
			assignment.setAssignedByUserId("test");
			assignment.setAssignedByName("Test");
			assignment.setAssignedTimestamp(new Date());
			entityManager.persist(assignment);
			roleGroup.getUserRoleAssignments().add(assignment);
		}
		return roleGroup;
	}
}
