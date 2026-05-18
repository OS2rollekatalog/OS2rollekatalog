package dk.digitalidentity.rc.service.listener;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createManagerSubstitute;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;
import jakarta.persistence.EntityManagerFactory;

@ExtendWith(MockitoExtension.class)
class OrgUnitManagerChangeListenerTest {

	@Mock
	private EntityManagerFactory entityManagerFactory;

	@Mock
	private UserService userService;

	private OrgUnitManagerChangeListener listener;

	@BeforeEach
	void setUp() {
		listener = new OrgUnitManagerChangeListener(entityManagerFactory, userService);
	}

	@Test
	@DisplayName("Manager added (null -> U) queues new manager and their substitutes for the OU")
	void newManagerAdded() {
		OrgUnit ou = createOrgUnit("ou-1", null);
		User newManager = createUser("u-new");
		User substitute = createUser("u-sub");
		newManager.setManagerSubstitutes(List.of(createManagerSubstitute(newManager, substitute, ou)));

		listener.handleManagerChange(ou, null, newManager);

		assertThat(captureQueuedUuids()).containsExactlyInAnyOrder("u-new", "u-sub");
	}

	@Test
	@DisplayName("Manager removed (U -> null) queues old manager and their substitutes")
	void managerRemoved() {
		OrgUnit ou = createOrgUnit("ou-1", null);
		User oldManager = createUser("u-old");
		User substitute = createUser("u-sub");
		oldManager.setManagerSubstitutes(List.of(createManagerSubstitute(oldManager, substitute, ou)));

		listener.handleManagerChange(ou, oldManager, null);

		assertThat(captureQueuedUuids()).containsExactlyInAnyOrder("u-old", "u-sub");
	}

	@Test
	@DisplayName("Manager swap (A -> B) queues both managers and both their OU-scoped substitutes")
	void managerSwapped() {
		OrgUnit ou = createOrgUnit("ou-1", null);
		User oldManager = createUser("u-old");
		User newManager = createUser("u-new");
		User oldSub = createUser("u-old-sub");
		User newSub = createUser("u-new-sub");
		oldManager.setManagerSubstitutes(List.of(createManagerSubstitute(oldManager, oldSub, ou)));
		newManager.setManagerSubstitutes(List.of(createManagerSubstitute(newManager, newSub, ou)));

		listener.handleManagerChange(ou, oldManager, newManager);

		assertThat(captureQueuedUuids())
			.containsExactlyInAnyOrder("u-old", "u-new", "u-old-sub", "u-new-sub");
	}

	@Test
	@DisplayName("Substitutes for other OUs are not queued")
	void substitutesForOtherOusIgnored() {
		OrgUnit ou = createOrgUnit("ou-1", null);
		OrgUnit unrelatedOu = createOrgUnit("ou-other", null);
		User newManager = createUser("u-new");
		User subForOu = createUser("u-sub-this");
		User subForOther = createUser("u-sub-other");
		newManager.setManagerSubstitutes(List.of(
			createManagerSubstitute(newManager, subForOu, ou),
			createManagerSubstitute(newManager, subForOther, unrelatedOu)
		));

		listener.handleManagerChange(ou, null, newManager);

		assertThat(captureQueuedUuids()).containsExactlyInAnyOrder("u-new", "u-sub-this");
	}

	@Test
	@DisplayName("Manager with null substitutes list is handled safely")
	void nullSubstitutesList() {
		OrgUnit ou = createOrgUnit("ou-1", null);
		User newManager = new User();
		newManager.setUuid("u-new");

		listener.handleManagerChange(ou, null, newManager);

		assertThat(captureQueuedUuids()).containsExactlyInAnyOrder("u-new");
	}

	@Test
	@DisplayName("No-op when both managers are null")
	void bothNullDoesNothing() {
		OrgUnit ou = createOrgUnit("ou-1", null);

		listener.handleManagerChange(ou, null, null);

		verify(userService, never()).queueMultipleForRecalculation(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("Substitute relations with null orgUnit reference are skipped without NPE")
	void nullOrgUnitOnSubstituteRelation() {
		OrgUnit ou = createOrgUnit("ou-1", null);
		User newManager = createUser("u-new");
		User goodSub = createUser("u-good-sub");
		User strayRelationSub = createUser("u-stray");
		var goodRelation = createManagerSubstitute(newManager, goodSub, ou);
		var strayRelation = createManagerSubstitute(newManager, strayRelationSub, null);
		newManager.setManagerSubstitutes(List.of(strayRelation, goodRelation));

		listener.handleManagerChange(ou, null, newManager);

		assertThat(captureQueuedUuids()).containsExactlyInAnyOrder("u-new", "u-good-sub");
	}

	private Set<String> captureQueuedUuids() {
		ArgumentCaptor<Set<String>> captor = uuidsCaptor();
		verify(userService).queueMultipleForRecalculation(captor.capture());
		return captor.getValue();
	}

	@SuppressWarnings("unchecked")
	private static ArgumentCaptor<Set<String>> uuidsCaptor() {
		return ArgumentCaptor.forClass(Set.class);
	}
}
