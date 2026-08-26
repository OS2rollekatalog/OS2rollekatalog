package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.SystemRoleDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRoleWithWeight;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Regression for issue #81: changing the weight on a system role must trigger an AD membership
 * re-sync for the whole it-system, since AD-sync is the only weight consumer driven by a trigger
 * (the read-API and UI compute weight live, and KSPCICS/KOMBIT/NemLogin/DMP do not use weight).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemRoleService.changeWeight")
class SystemRoleServiceChangeWeightTest {

	@Mock
	private SystemRoleDao systemRoleDao;
	@Mock
	private PendingADUpdateService pendingADUpdateService;

	@InjectMocks
	private SystemRoleService systemRoleService;

	private ItSystem itSystem;

	@BeforeEach
	void setup() {
		// pendingADUpdateService is a @Lazy @Autowired field (not a constructor arg), so @InjectMocks'
		// constructor injection does not populate it — set it explicitly.
		ReflectionTestUtils.setField(systemRoleService, "pendingADUpdateService", pendingADUpdateService);

		itSystem = createItSystem("it-system-uuid-123");
		itSystem.setId(1L);
	}

	@Test
	@DisplayName("should save and flag the it-system for AD re-sync when the weight changes")
	void shouldFlagItSystemOnWeightChange() {
		// ---- Given ---- //
		SystemRole systemRole = createSystemRoleWithWeight(1L, 1, itSystem);
		given(systemRoleDao.save(systemRole)).willReturn(systemRole);

		// ---- When ---- //
		systemRoleService.changeWeight(systemRole, 5);

		// ---- Then ---- //
		assertThat(systemRole.getWeight()).isEqualTo(5);
		verify(systemRoleDao).save(systemRole);
		verify(pendingADUpdateService).addItSystemToQueue(itSystem);
	}

	@Test
	@DisplayName("should do nothing when the weight is unchanged")
	void shouldDoNothingWhenWeightUnchanged() {
		// ---- Given ---- //
		SystemRole systemRole = createSystemRoleWithWeight(1L, 3, itSystem);

		// ---- When ---- //
		systemRoleService.changeWeight(systemRole, 3);

		// ---- Then ---- //
		assertThat(systemRole.getWeight()).isEqualTo(3);
		verifyNoInteractions(systemRoleDao, pendingADUpdateService);
	}
}
