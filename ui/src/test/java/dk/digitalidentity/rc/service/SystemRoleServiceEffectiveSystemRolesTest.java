package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRoleWithWeight;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemRoleService.getEffectiveSystemRoles")
class SystemRoleServiceEffectiveSystemRolesTest {

	@Mock
	private SystemRoleDao systemRoleDao;
	@Mock
	private UserRoleService userRoleService;

	@InjectMocks
	private SystemRoleService systemRoleService;

	private User testUser;
	private ItSystem itSystem;

	@BeforeEach
	void setup() {
		testUser = createUser("user-uuid-123");
		itSystem = createItSystem("it-system-uuid-123");
		itSystem.setId(1L);
	}

	@Nested
	@DisplayName("basic system role inclusion")
	class BasicInclusion {

		@Test
		@DisplayName("should return system roles assigned to the user")
		void shouldReturnSystemRolesAssignedToUser() {
			// ---- Given ---- //
			SystemRole sr1 = createSystemRoleWithWeight(1L, 1, itSystem);
			SystemRole sr2 = createSystemRoleWithWeight(2L, 1, itSystem);

			given(systemRoleDao.findDistinctByUserUuid(testUser.getUuid())).willReturn(List.of(sr1, sr2));

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser);

			// ---- Then ---- //
			assertThat(result).containsExactlyInAnyOrder(sr1, sr2);
		}

		@Test
		@DisplayName("should return empty list when user has no assignments")
		void shouldReturnEmptyWhenNoAssignments() {
			// ---- Given ---- //
			given(systemRoleDao.findDistinctByUserUuid(testUser.getUuid())).willReturn(List.of());

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser);

			// ---- Then ---- //
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("it-system filter overload")
	class ItSystemFilter {

		@Test
		@DisplayName("should pass the correct it-system ids to the dao")
		void shouldPassCorrectItSystemIdsToDao() {
			// ---- Given ---- //
			ItSystem itSystem2 = createItSystem("it-system-uuid-456");
			itSystem2.setId(2L);
			given(systemRoleDao.findDistinctByUserUuidAndItSystemIdIn(testUser.getUuid(), Set.of(1L, 2L)))
				.willReturn(List.of());

			// ---- When ---- //
			systemRoleService.getEffectiveSystemRoles(testUser, List.of(itSystem, itSystem2));

			// ---- Then ---- //
			verify(systemRoleDao).findDistinctByUserUuidAndItSystemIdIn(testUser.getUuid(), Set.of(1L, 2L));
		}

		@Test
		@DisplayName("should apply weight filtering on the it-system filtered result")
		void shouldApplyWeightFilteringOnFilteredResult() {
			// ---- Given ---- //
			SystemRole lowWeight = createSystemRoleWithWeight(1L, 1, itSystem);
			SystemRole highWeight = createSystemRoleWithWeight(2L, 5, itSystem);

			given(systemRoleDao.findDistinctByUserUuidAndItSystemIdIn(testUser.getUuid(), Set.of(1L)))
				.willReturn(List.of(lowWeight, highWeight));

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser, List.of(itSystem));

			// ---- Then ---- //
			assertThat(result)
				.containsExactly(highWeight)
				.doesNotContain(lowWeight);
		}

		@Test
		@DisplayName("should return empty list when no it-systems are provided")
		void shouldReturnEmptyWhenNoItSystemsProvided() {
			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser, List.of());

			// ---- Then ---- //
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("weight-based filtering")
	class WeightFiltering {

		@Test
		@DisplayName("should exclude lower weight system roles and include only the highest weight within the same IT system")
		void shouldKeepOnlyHighestWeightInSameItSystem() {
			// ---- Given ---- //
			SystemRole lowWeight = createSystemRoleWithWeight(1L, 1, itSystem);
			SystemRole highWeight = createSystemRoleWithWeight(2L, 5, itSystem);

			given(systemRoleDao.findDistinctByUserUuid(testUser.getUuid())).willReturn(List.of(lowWeight, highWeight));

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser);

			// ---- Then ---- //
			assertThat(result)
				.containsExactly(highWeight)
				.doesNotContain(lowWeight);
		}

		@Test
		@DisplayName("should include all system roles when they share the same weight in the same IT system")
		void shouldIncludeAllWhenSameWeight() {
			// ---- Given ---- //
			SystemRole sr1 = createSystemRoleWithWeight(1L, 3, itSystem);
			SystemRole sr2 = createSystemRoleWithWeight(2L, 3, itSystem);

			given(systemRoleDao.findDistinctByUserUuid(testUser.getUuid())).willReturn(List.of(sr1, sr2));

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser);

			// ---- Then ---- //
			assertThat(result).containsExactlyInAnyOrder(sr1, sr2);
		}

		@Test
		@DisplayName("should not exclude a lower-weight role in system1 because of a higher-weight role in a different IT system")
		void shouldNotApplyWeightFilteringAcrossItSystems() {
			// ---- Given ---- //
			ItSystem otherSystem = createItSystem("other-system-uuid");
			otherSystem.setId(2L);

			// srInSystem1 has weight 2 — lower than srInSystem2's weight 8, but they are in different systems
			SystemRole srInSystem1 = createSystemRoleWithWeight(1L, 2, itSystem);
			SystemRole srInSystem2 = createSystemRoleWithWeight(2L, 8, otherSystem);

			given(systemRoleDao.findDistinctByUserUuid(testUser.getUuid())).willReturn(List.of(srInSystem1, srInSystem2));

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser);

			// ---- Then ---- //
			// srInSystem1 must NOT be excluded even though srInSystem2 has a higher weight
			assertThat(result).containsExactlyInAnyOrder(srInSystem1, srInSystem2);
		}

		@Test
		@DisplayName("should exclude multiple lower weight roles when one highest weight role exists")
		void shouldExcludeAllLowerWeightRolesWhenOneHighestExists() {
			// ---- Given ---- //
			SystemRole weight1 = createSystemRoleWithWeight(1L, 1, itSystem);
			SystemRole weight2 = createSystemRoleWithWeight(2L, 2, itSystem);
			SystemRole weight5 = createSystemRoleWithWeight(3L, 5, itSystem);

			given(systemRoleDao.findDistinctByUserUuid(testUser.getUuid())).willReturn(List.of(weight1, weight2, weight5));

			// ---- When ---- //
			List<SystemRole> result = systemRoleService.getEffectiveSystemRoles(testUser);

			// ---- Then ---- //
			assertThat(result)
				.containsExactly(weight5)
				.doesNotContain(weight1, weight2);
		}
	}
}
