package dk.digitalidentity.rc.util;

import static org.assertj.core.api.Assertions.assertThat;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManagerSubstituteUtilTest {

	@Test
	@DisplayName("er aktiv når rækkens leder stadig er leder for enheden")
	void activeWhenManagerStillManagesOrgUnit() {
		assertThat(ManagerSubstituteUtil.isActive(managerSubstitute("leder-uuid", "leder-uuid"))).isTrue();
	}

	@Test
	@DisplayName("er ikke aktiv når enheden har fået en anden leder")
	void inactiveWhenOrgUnitHasAnotherManager() {
		assertThat(ManagerSubstituteUtil.isActive(managerSubstitute("tidligere-leder", "nuvaerende-leder"))).isFalse();
	}

	@Test
	@DisplayName("er ikke aktiv når enheden står uden leder")
	void inactiveWhenOrgUnitHasNoManager() {
		assertThat(ManagerSubstituteUtil.isActive(managerSubstitute("leder-uuid", null))).isFalse();
	}

	private static ManagerSubstitute managerSubstitute(String rowManagerUuid, String orgUnitManagerUuid) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid("ou-uuid");
		if (orgUnitManagerUuid != null) {
			orgUnit.setManager(user(orgUnitManagerUuid));
		}

		ManagerSubstitute managerSubstitute = new ManagerSubstitute();
		managerSubstitute.setOrgUnit(orgUnit);
		managerSubstitute.setManager(user(rowManagerUuid));
		return managerSubstitute;
	}

	private static User user(String uuid) {
		User user = new User();
		user.setUuid(uuid);
		return user;
	}
}
