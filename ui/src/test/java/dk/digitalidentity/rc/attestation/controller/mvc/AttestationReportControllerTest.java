package dk.digitalidentity.rc.attestation.controller.mvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttestationReportControllerTest {

	@Mock
	private UserService userService;

	@Mock
	private OrganisationAttestationService organisationAttestationService;

	@Mock
	private ItSystemService itSystemService;

	@InjectMocks
	private AttestationReportController controller;

	private MockedStatic<SecurityUtil> securityUtilMock;
	private User substitute;

	@SuppressWarnings("removal")
	@BeforeEach
	void setUp() {
		securityUtilMock = mockStatic(SecurityUtil.class);
		securityUtilMock.when(SecurityUtil::getUserId).thenReturn("substitute");
		securityUtilMock.when(SecurityUtil::isAttestationAdminOrAdmin).thenReturn(false);

		substitute = new User();
		substitute.setUuid("substitute-uuid");
		substitute.setSubstituteFor(new ArrayList<>());

		when(userService.getOptionalByUserId("substitute")).thenReturn(Optional.of(substitute));
		when(organisationAttestationService.hasAttestationWithinTheLastYear(any())).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		securityUtilMock.close();
	}

	@Test
	@DisplayName("stedfortræder ser enheden så længe den stadig har den leder, stedfortrædelsen blev oprettet for")
	void shouldListOrgUnitForCurrentManager() {
		User manager = user("manager-uuid");
		OrgUnit orgUnit = orgUnit("ou-uuid", "Enhed med samme leder", manager);
		substitute.getSubstituteFor().add(managerSubstitute(orgUnit, manager));

		Model model = new ExtendedModelMap();
		String view = controller.reports(model);

		assertThat(view).isEqualTo("attestationmodule/reports/reports");
		assertThat(orgUnitNames(model)).containsExactly("Enhed med samme leder");
	}

	@Test
	@DisplayName("stedfortræder ser ikke enheden efter den har fået en anden leder")
	void shouldNotListOrgUnitAfterManagerChanged() {
		OrgUnit orgUnit = orgUnit("ou-uuid", "Enhed med ny leder", user("current-manager-uuid"));
		substitute.getSubstituteFor().add(managerSubstitute(orgUnit, user("previous-manager-uuid")));

		Model model = new ExtendedModelMap();

		// ingen enheder og ingen it-systemer tilbage, så siden falder tilbage til fejlsiden
		assertThat(controller.reports(model)).isEqualTo("attestationmodule/error");
	}

	@SuppressWarnings("unchecked")
	private static List<String> orgUnitNames(Model model) {
		var orgUnits = (Set<AttestationReportController.AttestationOrgUnitReportListDTO>) model.getAttribute("orgUnits");
		return orgUnits.stream().map(AttestationReportController.AttestationOrgUnitReportListDTO::name).toList();
	}

	private static User user(String uuid) {
		User user = new User();
		user.setUuid(uuid);
		return user;
	}

	private static OrgUnit orgUnit(String uuid, String name, User manager) {
		OrgUnit orgUnit = new OrgUnit();
		orgUnit.setUuid(uuid);
		orgUnit.setName(name);
		orgUnit.setManager(manager);
		return orgUnit;
	}

	private static ManagerSubstitute managerSubstitute(OrgUnit orgUnit, User manager) {
		ManagerSubstitute managerSubstitute = new ManagerSubstitute();
		managerSubstitute.setOrgUnit(orgUnit);
		managerSubstitute.setManager(manager);
		return managerSubstitute;
	}
}
