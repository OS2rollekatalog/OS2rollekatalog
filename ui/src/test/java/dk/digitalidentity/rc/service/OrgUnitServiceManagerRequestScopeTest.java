package dk.digitalidentity.rc.service;

import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;

@ExtendWith(MockitoExtension.class)
class OrgUnitServiceManagerRequestScopeTest {

	@Mock
	private OrgUnitDao orgUnitDao;

	@Mock
	private UserService userService;

	@Mock
	private SettingsService settingsService;

	@InjectMocks
	private OrgUnitService service;

	@BeforeEach
	void setUp() {
		lenient().when(settingsService.getExcludedOUs()).thenReturn(Collections.emptySet());
	}

	@Test
	@DisplayName("Scope contains managed OU, manager-less descendants and sub-OU managers — but not sub-managers' OUs")
	void departmentChiefScope() {
		User hanne = createUser("hanne");
		User louise = createUser("louise");
		User marlene = createUser("marlene");

		OrgUnit familie = activeOu("familie", null, hanne);
		OrgUnit team1 = activeOu("team1", familie, louise);
		OrgUnit team2 = activeOu("team2", familie, marlene);
		OrgUnit stab = activeOu("stab", familie, null);
		OrgUnit stabSub = activeOu("stab-sub", stab, null);
		OrgUnit team1Sub = activeOu("team1-sub", team1, null);
		OrgUnit inactive = activeOu("inactive", familie, null);
		inactive.setActive(false);

		familie.setChildren(List.of(team1, team2, stab, inactive));
		stab.setChildren(List.of(stabSub));
		team1.setChildren(List.of(team1Sub));

		when(orgUnitDao.findByManager(hanne)).thenReturn(new ArrayList<>(List.of(familie)));
		when(userService.getSubstitutesManager(hanne)).thenReturn(Collections.emptyList());

		OrgUnitService.ManagerRequestScope scope = service.getManagerRequestScope(hanne);

		assertThat(uuids(scope.orgUnits())).containsExactlyInAnyOrder("familie", "stab", "stab-sub");
		assertThat(scope.subManagerUserUuids()).containsExactlyInAnyOrder("louise", "marlene");
	}

	@Test
	@DisplayName("Substitute for the chief gets the same scope as the chief")
	void substituteScope() {
		User hanne = createUser("hanne");
		User louise = createUser("louise");
		User ole = createUser("ole");

		OrgUnit familie = activeOu("familie", null, hanne);
		OrgUnit team1 = activeOu("team1", familie, louise);
		familie.setChildren(List.of(team1));

		when(orgUnitDao.findByManager(ole)).thenReturn(new ArrayList<>());
		when(orgUnitDao.findByManager(hanne)).thenReturn(new ArrayList<>(List.of(familie)));
		when(userService.getSubstitutesManager(ole)).thenReturn(List.of(hanne));

		OrgUnitService.ManagerRequestScope scope = service.getManagerRequestScope(ole);

		assertThat(uuids(scope.orgUnits())).containsExactlyInAnyOrder("familie");
		assertThat(scope.subManagerUserUuids()).containsExactlyInAnyOrder("louise");
	}

	@Test
	@DisplayName("Descendant OU managed by the chief themselves is part of the OU scope, not a sub-manager")
	void selfManagedDescendant() {
		User hanne = createUser("hanne");

		OrgUnit familie = activeOu("familie", null, hanne);
		OrgUnit mellem = activeOu("mellem", familie, null);
		OrgUnit dyb = activeOu("dyb", mellem, hanne);
		familie.setChildren(List.of(mellem));
		mellem.setChildren(List.of(dyb));

		when(orgUnitDao.findByManager(hanne)).thenReturn(new ArrayList<>(List.of(familie, dyb)));
		when(userService.getSubstitutesManager(hanne)).thenReturn(Collections.emptyList());

		OrgUnitService.ManagerRequestScope scope = service.getManagerRequestScope(hanne);

		assertThat(uuids(scope.orgUnits())).containsExactlyInAnyOrder("familie", "mellem", "dyb");
		assertThat(scope.subManagerUserUuids()).isEmpty();
	}

	// ---- helpers ---- //

	private static OrgUnit activeOu(String uuid, OrgUnit parent, User manager) {
		OrgUnit ou = createOrgUnit(uuid, parent, manager);
		ou.setActive(true);
		ou.setChildren(new ArrayList<>());
		return ou;
	}

	private static Set<String> uuids(Set<OrgUnit> orgUnits) {
		return orgUnits.stream().map(OrgUnit::getUuid).collect(Collectors.toSet());
	}
}
