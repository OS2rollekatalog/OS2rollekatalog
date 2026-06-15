package dk.digitalidentity.rc.service;

import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;

@ExtendWith(MockitoExtension.class)
class UserServiceEffectiveManagerTest {

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private ManagerSubstituteService managerSubstituteService;

	@InjectMocks
	private UserService userService;

	@Test
	@DisplayName("Chief is effective manager for a team leader positioned in the OU they manage themselves")
	void chiefIsEffectiveManagerForTeamLeader() {
		User hanne = createUser("hanne");
		User louise = createUser("louise");
		OrgUnit familie = createOrgUnit("familie", null, hanne);
		OrgUnit team1 = createOrgUnit("team1", familie, louise);
		positionAt(louise, team1);

		when(orgUnitService.getEffectiveApprover(team1, louise))
			.thenReturn(new OrgUnitService.EffectiveApprover(hanne, familie));

		assertThat(userService.isEffectiveManagerOrSubstituteFor(hanne, louise)).isTrue();
	}

	@Test
	@DisplayName("Chief's substitute in the resolved OU is also effective manager for the team leader")
	void substituteOfChiefIsEffectiveManager() {
		User hanne = createUser("hanne");
		User ole = createUser("ole");
		User louise = createUser("louise");
		OrgUnit familie = createOrgUnit("familie", null, hanne);
		OrgUnit team1 = createOrgUnit("team1", familie, louise);
		positionAt(louise, team1);

		when(orgUnitService.getEffectiveApprover(team1, louise))
			.thenReturn(new OrgUnitService.EffectiveApprover(hanne, familie));
		when(managerSubstituteService.isSubstituteforOrgUnit(ole, familie)).thenReturn(true);

		assertThat(userService.isEffectiveManagerOrSubstituteFor(ole, louise)).isTrue();
	}

	@Test
	@DisplayName("Unrelated user is not effective manager")
	void unrelatedUserIsNotEffectiveManager() {
		User hanne = createUser("hanne");
		User bob = createUser("bob");
		User louise = createUser("louise");
		OrgUnit familie = createOrgUnit("familie", null, hanne);
		OrgUnit team1 = createOrgUnit("team1", familie, louise);
		positionAt(louise, team1);

		when(orgUnitService.getEffectiveApprover(team1, louise))
			.thenReturn(new OrgUnitService.EffectiveApprover(hanne, familie));
		lenient().when(managerSubstituteService.isSubstituteforOrgUnit(bob, familie)).thenReturn(false);

		assertThat(userService.isEffectiveManagerOrSubstituteFor(bob, louise)).isFalse();
	}

	@Test
	@DisplayName("No effective manager found (top of tree) yields false")
	void topOfTreeYieldsFalse() {
		User hanne = createUser("hanne");
		User louise = createUser("louise");
		OrgUnit team1 = createOrgUnit("team1", null, louise);
		positionAt(louise, team1);

		when(orgUnitService.getEffectiveApprover(team1, louise)).thenReturn(null);

		assertThat(userService.isEffectiveManagerOrSubstituteFor(hanne, louise)).isFalse();
	}

	@Test
	@DisplayName("Single-OU check matches the chief for the team leader's own OU")
	void singleOrgUnitCheck() {
		User hanne = createUser("hanne");
		User bob = createUser("bob");
		User louise = createUser("louise");
		OrgUnit familie = createOrgUnit("familie", null, hanne);
		OrgUnit team1 = createOrgUnit("team1", familie, louise);

		when(orgUnitService.getEffectiveApprover(team1, louise))
			.thenReturn(new OrgUnitService.EffectiveApprover(hanne, familie));
		lenient().when(managerSubstituteService.isSubstituteforOrgUnit(bob, familie)).thenReturn(false);

		assertThat(userService.isEffectiveManagerOrSubstituteFor(hanne, louise, team1)).isTrue();
		assertThat(userService.isEffectiveManagerOrSubstituteFor(bob, louise, team1)).isFalse();
	}

	@Test
	@DisplayName("Subject without positions yields false")
	void noPositionsYieldsFalse() {
		User hanne = createUser("hanne");
		User louise = createUser("louise");
		louise.setPositions(null);

		assertThat(userService.isEffectiveManagerOrSubstituteFor(hanne, louise)).isFalse();
	}

	// ---- helpers ---- //

	private static void positionAt(User user, OrgUnit orgUnit) {
		Position position = new Position();
		position.setUser(user);
		position.setOrgUnit(orgUnit);
		user.getPositions().add(position);
	}
}
