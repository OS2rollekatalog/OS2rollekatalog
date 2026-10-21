package dk.digitalidentity.rc.test.integration.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

import dk.digitalidentity.rc.dao.ManualAssignmentNotificationMapDao;
import dk.digitalidentity.rc.dao.model.ManualAssignmentNotificationMap;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.UserUserRoleAssignment;
import dk.digitalidentity.rc.event.AssignmentChangeEventHandlerService;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ManualAssignmentNotificationMapRemovedByIntegrationTest extends BaseIntegrationTest {

	private final BasicTestDataFactory testDataFactory;
	private final ManualAssignmentNotificationMapDao manualAssignmentNotificationMapDao;
	private final AssignmentChangeEventHandlerService assignmentChangeEventHandlerService;

	@Test
	@DisplayName("Recalculation stamps the person on the roles the user lost, not on roles still held")
	void recalculationStampsRemoverOnLostRolesOnly() {
		BasicTestDataFactory.BasicTestData testData = testDataFactory.createBasicTestData();
		User user = testData.user();
		ManualAssignmentNotificationMap lost = manualAssignmentNotificationMapDao.save(mapRow(user, testData.urDirectlyAssigned()));
		ManualAssignmentNotificationMap kept = manualAssignmentNotificationMapDao.save(mapRow(user, testData.urViaChildOU()));

		UserUserRoleAssignment direct = user.getUserRoleAssignments().stream()
			.filter(a -> a.getUserRole().equals(testData.urDirectlyAssigned()))
			.findFirst()
			.orElseThrow();
		user.getUserRoleAssignments().remove(direct);
		entityManager.remove(direct);
		flushAndClear();

		assignmentChangeEventHandlerService.updateUsers(Map.of(user.getUuid(), "Admin Person (admin)"));
		flushAndClear();

		assertThat(manualAssignmentNotificationMapDao.findById(lost.getId()).orElseThrow().getRemovedBy()).isEqualTo("Admin Person (admin)");
		assertThat(manualAssignmentNotificationMapDao.findById(kept.getId()).orElseThrow().getRemovedBy()).isNull();
	}

	private static ManualAssignmentNotificationMap mapRow(User user, UserRole userRole) {
		ManualAssignmentNotificationMap row = new ManualAssignmentNotificationMap();
		row.setDomainId(user.getDomain().getId());
		row.setUserUserId(user.getUserId());
		row.setUserRoleId(userRole.getId());
		return row;
	}
}
