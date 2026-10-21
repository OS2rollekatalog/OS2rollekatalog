package dk.digitalidentity.rc.rolerequest.service.scope;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.projections.SystemRoleConstraint;
import dk.digitalidentity.rc.util.OrganisationConstraintUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemRoleScopeResolver.scopesByOrgUnit")
class SystemRoleScopeResolverTest {
	private static final String OKONOMI_UUID = "6a1d2c3e-0000-4000-8000-000000000001";
	private static final String LON_UUID = "6a1d2c3e-0000-4000-8000-000000000002";
	private static final String HR_UUID = "6a1d2c3e-0000-4000-8000-000000000003";
	private static final long OPUS_ID = 7L;

	@Mock private OrganisationConstraintUtil organisationConstraintUtil;
	@Mock private SystemRoleScopeInputsLoader scopeInputsLoader;
	@Mock private OrgUnitDao orgUnitDao;

	@InjectMocks
	private SystemRoleScopeResolver scopeResolver;

	@Test
	@DisplayName("each OU is in one scope with only the IT systems of the roles covering it")
	void groupsOrgUnitsByAllowedItSystems() {
		// ---- Given ---- //
		User user = new User();
		when(organisationConstraintUtil.getOrganisationConstraintUuids(anyString())).thenAnswer(invocation -> List.of(invocation.<String>getArgument(0)));
		when(scopeInputsLoader.load(user, Constants.ROLE_REQUESTAUTHORIZED)).thenReturn(new SystemRoleScopeInputs(
			Map.of(
				1L, List.of(ouConstraint(1L, OKONOMI_UUID + "," + LON_UUID)),
				2L, List.of(ouConstraint(2L, LON_UUID + "," + HR_UUID), itSystemConstraint(2L, String.valueOf(OPUS_ID)))),
			List.of(), Set.of(), Set.of(), Map.of()));

		// ---- When ---- //
		List<SystemRoleScope> scopes = scopeResolver.scopesByOrgUnit(user, Constants.ROLE_REQUESTAUTHORIZED);

		// ---- Then ---- //
		assertThat(scopes).containsExactlyInAnyOrder(
			new SystemRoleScope(new LimitedToOrgUnits(LimitedToType.CONSTRAINED, Set.of(OKONOMI_UUID, LON_UUID)), new LimitedToItSystems(LimitedToType.ALL, Set.of())),
			new SystemRoleScope(new LimitedToOrgUnits(LimitedToType.CONSTRAINED, Set.of(HR_UUID)), new LimitedToItSystems(LimitedToType.CONSTRAINED, Set.of(OPUS_ID))));
	}

	private static SystemRoleConstraint ouConstraint(long userRoleId, String ouUuids) {
		return new SystemRoleConstraint(userRoleId, ConstraintValueType.VALUE, Constants.INTERNAL_ORGUNIT_CONSTRAINT_ENTITY_ID, ouUuids);
	}

	private static SystemRoleConstraint itSystemConstraint(long userRoleId, String itSystemIds) {
		return new SystemRoleConstraint(userRoleId, ConstraintValueType.VALUE, Constants.INTERNAL_ITSYSTEM_CONSTRAINT_ENTITY_ID, itSystemIds);
	}
}
