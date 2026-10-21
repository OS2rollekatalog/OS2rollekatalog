package dk.digitalidentity.rc.rolerequest.service.permission;

import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.service.scope.SystemRoleScopeResolver;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToItSystems;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToOrgUnits;
import dk.digitalidentity.rc.rolerequest.service.scope.LimitedToType;
import dk.digitalidentity.rc.rolerequest.service.scope.SystemRoleScope;
import dk.digitalidentity.rc.rolerequest.service.permission.ApproverContext.AuthorizedScope;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.config.Constants.ROLE_APPROVEAUTHORIZED;
import static dk.digitalidentity.rc.config.Constants.ROLE_REQUESTAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApproverContextFactory")
class ApproverContextFactoryTest {

	@Mock private SecurityUtil securityUtil;
	@Mock private SettingsService settingsService;
	@Mock private OrgUnitService orgUnitService;
	@Mock private ItSystemDao itSystemDao;
	@Mock private SystemRoleScopeResolver scopeResolver;

	@InjectMocks
	private ApproverContextFactory factory;

	private User approver;

	@BeforeEach
	void setUp() {
		approver = new User();
		approver.setUuid("approver-uuid");
		approver.setUserId("approver-user-id");
		lenient().when(scopeResolver.scopesByOrgUnit(approver, ROLE_REQUESTAUTHORIZED)).thenReturn(List.of());
		lenient().when(scopeResolver.scopesByOrgUnit(approver, ROLE_APPROVEAUTHORIZED)).thenReturn(List.of());
	}

	@Test
	@DisplayName("finds system responsibilities by the approver's user id")
	void systemResponsibleByUserId() {
		// ---- Given ---- //
		when(itSystemDao.findIdsByAttestationResponsibleUserId("approver-user-id")).thenReturn(Set.of(7L));

		// ---- When ---- //
		ApproverContext context = factory.of(approver);

		// ---- Then ---- //
		assertThat(context.systemResponsibleItSystemIds()).containsExactly(7L);
	}

	@Test
	@DisplayName("an approver without the request-authorized role has no authorized scopes")
	void noAuthorizedRoleIsEmptyScope() {
		// ---- When ---- //
		ApproverContext context = factory.of(approver);

		// ---- Then ---- //
		assertThat(context.authorizedScopes()).isEmpty();
	}

	@Test
	@DisplayName("maps each scope, with unlimited as null")
	void scopesStayPaired() {
		// ---- Given ---- //
		when(scopeResolver.scopesByOrgUnit(approver, ROLE_REQUESTAUTHORIZED)).thenReturn(List.of(
			new SystemRoleScope(new LimitedToOrgUnits(LimitedToType.CONSTRAINED, Set.of("ou-uuid")), new LimitedToItSystems(LimitedToType.ALL, Set.of())),
			new SystemRoleScope(new LimitedToOrgUnits(LimitedToType.ALL, Set.of()), new LimitedToItSystems(LimitedToType.CONSTRAINED, Set.of(7L)))));

		// ---- When ---- //
		ApproverContext context = factory.of(approver);

		// ---- Then ---- //
		assertThat(context.authorizedScopes()).containsExactly(
			new AuthorizedScope(Set.of("ou-uuid"), null),
			new AuthorizedScope(null, Set.of(7L)));
	}

	@Test
	@DisplayName("a deleted approver is marked as deleted")
	void deletedApprover() {
		// ---- Given ---- //
		approver.setDeleted(true);

		// ---- When ---- //
		ApproverContext context = factory.of(approver);

		// ---- Then ---- //
		assertThat(context.deleted()).isTrue();
	}
}
