package dk.digitalidentity.rc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dk.digitalidentity.rc.dao.model.AuthorizationManager;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;

class OrgUnitServiceTest {

	private final OrgUnitService service = new OrgUnitService();

	@Test
	@DisplayName("isAuthorizationManagerFor returns true when requester is authmanager on a receiver position OU")
	void authManagerForReceiverOu() {
		User requester = user("req");
		User receiver = userAtOu("rec", ouWithAuthManagers("ou-a", requester));

		assertThat(service.isAuthorizationManagerFor(requester, receiver)).isTrue();
	}

	@Test
	@DisplayName("isAuthorizationManagerFor returns false when requester only authmanages OUs where receiver has no position")
	void authManagerOnUnrelatedOu() {
		User requester = user("req");
		User other = user("other");
		// requester is authmanager on ou-a; receiver has a position only on ou-b
		ouWithAuthManagers("ou-a", requester);
		User receiver = userAtOu("rec", ouWithAuthManagers("ou-b", other));

		assertThat(service.isAuthorizationManagerFor(requester, receiver)).isFalse();
	}

	@Test
	@DisplayName("isAuthorizationManagerFor returns false when receiver's parent OU has requester as authmanager (no inheritance)")
	void noInheritanceFromParent() {
		User requester = user("req");
		OrgUnit parent = ouWithAuthManagers("parent", requester);
		OrgUnit child = new OrgUnit();
		child.setUuid("child");
		child.setParent(parent);
		child.setAuthorizationManagers(new ArrayList<>());
		User receiver = userAtOu("rec", child);

		assertThat(service.isAuthorizationManagerFor(requester, receiver)).isFalse();
	}

	@Test
	@DisplayName("isAuthorizationManagerFor returns true if any of receiver's multiple positions resolves")
	void anyMatchingPosition() {
		User requester = user("req");
		User other = user("other");
		OrgUnit ouA = ouWithAuthManagers("ou-a", other);
		OrgUnit ouB = ouWithAuthManagers("ou-b", requester);

		User receiver = user("rec");
		List<Position> positions = new ArrayList<>();
		positions.add(positionAt(receiver, ouA));
		positions.add(positionAt(receiver, ouB));
		receiver.setPositions(positions);

		assertThat(service.isAuthorizationManagerFor(requester, receiver)).isTrue();
	}

	@Test
	@DisplayName("isAuthorizationManagerFor tolerates null inputs and null positions")
	void nullSafety() {
		assertThat(service.isAuthorizationManagerFor(null, user("rec"))).isFalse();
		assertThat(service.isAuthorizationManagerFor(user("req"), null)).isFalse();

		User receiverWithoutPositions = user("rec");
		receiverWithoutPositions.setPositions(null);
		assertThat(service.isAuthorizationManagerFor(user("req"), receiverWithoutPositions)).isFalse();
	}

	@Test
	@DisplayName("isAuthorizationManagerFor skips AuthorizationManager rows where user is null")
	void authManagerWithNullUser() {
		User requester = user("req");
		OrgUnit ou = new OrgUnit();
		ou.setUuid("ou-a");
		AuthorizationManager orphan = new AuthorizationManager();
		orphan.setUser(null);
		orphan.setOrgUnit(ou);
		List<AuthorizationManager> managers = new ArrayList<>();
		managers.add(orphan);
		ou.setAuthorizationManagers(managers);
		User receiver = userAtOu("rec", ou);

		assertThat(service.isAuthorizationManagerFor(requester, receiver)).isFalse();
	}

	// ---- helpers ---- //

	private static User user(String uuid) {
		User u = new User();
		u.setUuid(uuid);
		u.setPositions(new ArrayList<>());
		return u;
	}

	private static User userAtOu(String uuid, OrgUnit orgUnit) {
		User u = user(uuid);
		u.getPositions().add(positionAt(u, orgUnit));
		return u;
	}

	private static Position positionAt(User user, OrgUnit orgUnit) {
		Position p = new Position();
		p.setUser(user);
		p.setOrgUnit(orgUnit);
		return p;
	}

	private static OrgUnit ouWithAuthManagers(String uuid, User... authManagers) {
		OrgUnit ou = new OrgUnit();
		ou.setUuid(uuid);
		List<AuthorizationManager> managers = new ArrayList<>();
		for (User u : authManagers) {
			AuthorizationManager am = new AuthorizationManager();
			am.setUser(u);
			am.setOrgUnit(ou);
			managers.add(am);
		}
		ou.setAuthorizationManagers(managers);
		return ou;
	}
}
