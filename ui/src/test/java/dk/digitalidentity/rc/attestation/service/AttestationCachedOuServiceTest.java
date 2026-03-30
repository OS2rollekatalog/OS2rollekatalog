package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.OrgUnitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Cached OU Service Tests")
class AttestationCachedOuServiceTest {

	private static final String TEST_OU_UUID = "test-ou-uuid";
	private static final String TEST_MANAGER_UUID = "test-manager-uuid";
	private static final String DIFFERENT_MANAGER_UUID = "different-manager-uuid";

	@Mock
	private HistoryOUDao historyOUDao;

	@Mock
	private OrgUnitDao orgUnitDao;

	@Mock
	private OrgUnitService orgUnitService;

	@InjectMocks
	private AttestationCachedOuService attestationCachedOuService;

	@Nested
	@DisplayName("getOuManager() Tests")
	class GetOuManagerTests {

		@Test
		@DisplayName("Should return null when ouUuid is null")
		void getOuManager_WhenOuUuidIsNull_ShouldReturnNull() {
			// Arrange
			LocalDate when = LocalDate.now();

			// Act
			String result = attestationCachedOuService.getOuManager(when, null);

			// Assert
			assertNull(result);
		}

		@Test
		@DisplayName("Should return null when OU not found in history")
		void getOuManager_WhenOuNotFound_ShouldReturnNull() {
			// Arrange
			LocalDate when = LocalDate.now();
			when(historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(when, TEST_OU_UUID)).thenReturn(null);

			// Act
			String result = attestationCachedOuService.getOuManager(when, TEST_OU_UUID);

			// Assert
			assertNull(result);
		}

		@Test
		@DisplayName("Should return manager UUID when OU found")
		void getOuManager_WhenOuFound_ShouldReturnManagerUuid() {
			// Arrange
			LocalDate when = LocalDate.now();
			HistoryOU historyOU = new HistoryOU();
			historyOU.setOuManagerUuid(TEST_MANAGER_UUID);
			when(historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(when, TEST_OU_UUID)).thenReturn(historyOU);

			// Act
			String result = attestationCachedOuService.getOuManager(when, TEST_OU_UUID);

			// Assert
			assertEquals(TEST_MANAGER_UUID, result);
		}

		@Test
		@DisplayName("Should return null when OU found but has no manager")
		void getOuManager_WhenOuFoundButNoManager_ShouldReturnNull() {
			// Arrange
			LocalDate when = LocalDate.now();
			HistoryOU historyOU = new HistoryOU();
			historyOU.setOuManagerUuid(null);
			when(historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(when, TEST_OU_UUID)).thenReturn(historyOU);

			// Act
			String result = attestationCachedOuService.getOuManager(when, TEST_OU_UUID);

			// Assert
			assertNull(result);
		}
	}

	@Nested
	@DisplayName("findParentOuWithDifferentManager() Tests")
	class FindParentOuWithDifferentManagerTests {

		@Test
		@DisplayName("Should return null when OU not found")
		void findParentOuWithDifferentManager_WhenOuNotFound_ShouldReturnNull() {
			// Arrange
			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(null);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertNull(result);
		}

		@Test
		@DisplayName("Should return null when OU is not active and included")
		void findParentOuWithDifferentManager_WhenOuNotActiveAndIncluded_ShouldReturnNull() {
			// Arrange
			OrgUnit orgUnit = createOrgUnitWithManager(TEST_OU_UUID, TEST_MANAGER_UUID);
			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(orgUnit);
			when(orgUnitService.isActiveAndIncluded(orgUnit)).thenReturn(false);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertNull(result);
		}

		@Test
		@DisplayName("Should return current OU UUID when manager is different")
		void findParentOuWithDifferentManager_WhenManagerIsDifferent_ShouldReturnCurrentOuUuid() {
			// Arrange
			OrgUnit orgUnit = createOrgUnitWithManager(TEST_OU_UUID, DIFFERENT_MANAGER_UUID);
			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(orgUnit);
			when(orgUnitService.isActiveAndIncluded(orgUnit)).thenReturn(true);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertEquals(TEST_OU_UUID, result);
		}

		@Test
		@DisplayName("Should return current OU UUID when OU has no manager")
		void findParentOuWithDifferentManager_WhenOuHasNoManager_ShouldReturnCurrentOuUuid() {
			// Arrange
			OrgUnit orgUnit = createOrgUnitWithManager(TEST_OU_UUID, null);
			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(orgUnit);
			when(orgUnitService.isActiveAndIncluded(orgUnit)).thenReturn(true);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertEquals(TEST_OU_UUID, result);
		}

		@Test
		@DisplayName("Should traverse to parent when manager is the same")
		void findParentOuWithDifferentManager_WhenManagerIsSame_ShouldTraverseToParent() {
			// Arrange
			String parentOuUuid = "parent-ou-uuid";
			OrgUnit parentOrgUnit = createOrgUnitWithManager(parentOuUuid, DIFFERENT_MANAGER_UUID);
			OrgUnit childOrgUnit = createOrgUnitWithManager(TEST_OU_UUID, TEST_MANAGER_UUID);
			childOrgUnit.setParent(parentOrgUnit);

			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(childOrgUnit);
			when(orgUnitService.isActiveAndIncluded(childOrgUnit)).thenReturn(true);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertEquals(parentOuUuid, result);
		}

		@Test
		@DisplayName("Should return parent UUID when parent has no manager")
		void findParentOuWithDifferentManager_WhenParentHasNoManager_ShouldReturnParentUuid() {
			// Arrange
			String parentOuUuid = "parent-ou-uuid";
			OrgUnit parentOrgUnit = createOrgUnitWithManager(parentOuUuid, null);
			OrgUnit childOrgUnit = createOrgUnitWithManager(TEST_OU_UUID, TEST_MANAGER_UUID);
			childOrgUnit.setParent(parentOrgUnit);

			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(childOrgUnit);
			when(orgUnitService.isActiveAndIncluded(childOrgUnit)).thenReturn(true);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertEquals(parentOuUuid, result);
		}

		@Test
		@DisplayName("Should return null when reaching root without finding different manager")
		void findParentOuWithDifferentManager_WhenReachingRoot_ShouldReturnNull() {
			// Arrange
			OrgUnit orgUnit = createOrgUnitWithManager(TEST_OU_UUID, TEST_MANAGER_UUID);
			orgUnit.setParent(null);

			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(orgUnit);
			when(orgUnitService.isActiveAndIncluded(orgUnit)).thenReturn(true);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertNull(result);
		}

		@Test
		@DisplayName("Should return null when circular hierarchy detected")
		void findParentOuWithDifferentManager_WhenCircularHierarchy_ShouldReturnNull() {
			// Arrange - Create a chain of 12 OUs all with the same manager (exceeds the 10 iteration limit)
			OrgUnit currentOu = createOrgUnitWithManager(TEST_OU_UUID, TEST_MANAGER_UUID);
			OrgUnit previousOu = currentOu;

			for (int i = 0; i < 11; i++) {
				OrgUnit parentOu = createOrgUnitWithManager("ou-" + i, TEST_MANAGER_UUID);
				previousOu.setParent(parentOu);
				previousOu = parentOu;
			}

			when(orgUnitDao.findByUuidAndActiveTrue(TEST_OU_UUID)).thenReturn(currentOu);
			when(orgUnitService.isActiveAndIncluded(currentOu)).thenReturn(true);

			// Act
			String result = attestationCachedOuService.findParentOuWithDifferentManager(TEST_MANAGER_UUID, TEST_OU_UUID);

			// Assert
			assertNull(result);
		}
	}

}
