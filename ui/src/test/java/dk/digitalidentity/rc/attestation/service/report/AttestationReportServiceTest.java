package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.ITSystemRoleBuildAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
import dk.digitalidentity.rc.attestation.service.AttestationConstraintService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Report Service Tests")
class AttestationReportServiceTest {

	@Mock
	private AttestationUserRoleAssignmentDao attestationUserRoleAssignmentDao;

	@Mock
	private AttestationCachedUserService cachedUserService;

	@Mock
	private AttestationReportContextService attestationReportContextService;

	@Mock
	private AttestationConstraintService attestationConstraintService;

	@Mock
	private ItSystemService itSystemService;

	@Mock
	private UserRoleService userRoleService;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private AttestationReportService attestationReportService;

	@Nested
	@DisplayName("getUserRoleRows() Tests")
	class GetUserRoleRowsTests {

		@BeforeEach
		void setUp() {
			when(attestationReportContextService.createContext(any(LocalDate.class)))
					.thenReturn(AttestationReportContextService.AttestationReportContext.builder()
							.itSystemUserAttestations(Collections.emptyList())
							.itSystemRolesAttestations(Collections.emptyList())
							.organisationRolesAttestations(Collections.emptyList())
							.build());
		}

		@Test
		@DisplayName("Should return empty list when no row IDs provided")
		void getUserRoleRows_WhenNoRowIds_ShouldReturnEmptyList() {
			// Arrange
			LocalDate since = LocalDate.now().minusMonths(1);
			LocalDate when = LocalDate.now();
			List<Long> emptyRowIds = Collections.emptyList();

			// Act
			List<RoleAssignmentReportRowDTO> result = attestationReportService.getUserRoleRows(since, when, emptyRowIds);

			// Assert
			assertTrue(result.isEmpty());
			verify(attestationUserRoleAssignmentDao, never()).findByIdIn(anyList());
		}

		@Test
		@DisplayName("Should return rows with correct status for active assignments")
		void getUserRoleRows_WithActiveAssignment_ShouldReturnActiveStatus() {
			// Arrange
			LocalDate since = LocalDate.now().minusMonths(1);
			LocalDate when = LocalDate.now();
			List<Long> rowIds = List.of(1L);

			AttestationUserRoleAssignmentDto assignment = createAssignment(1L, null); // null validTo means active

			when(attestationUserRoleAssignmentDao.findByIdIn(rowIds)).thenReturn(List.of(assignment));
			when(cachedUserService.getUserPositionsCached(any(), any())).thenReturn("Test Position");

			// Act
			List<RoleAssignmentReportRowDTO> result = attestationReportService.getUserRoleRows(since, when, rowIds);

			// Assert
			assertEquals(1, result.size());
			assertEquals(RoleStatus.ACTIVE, result.get(0).getStatus());
		}

		@Test
		@DisplayName("Should return rows with correct status for inactive assignments")
		void getUserRoleRows_WithInactiveAssignment_ShouldReturnInactiveStatus() {
			// Arrange
			LocalDate since = LocalDate.now().minusMonths(1);
			LocalDate when = LocalDate.now();
			List<Long> rowIds = List.of(1L);

			AttestationUserRoleAssignmentDto assignment = createAssignment(1L, LocalDate.now().minusDays(1)); // past validTo means inactive

			when(attestationUserRoleAssignmentDao.findByIdIn(rowIds)).thenReturn(List.of(assignment));
			when(cachedUserService.getUserPositionsCached(any(), any())).thenReturn("Test Position");

			// Act
			List<RoleAssignmentReportRowDTO> result = attestationReportService.getUserRoleRows(since, when, rowIds);

			// Assert
			assertEquals(1, result.size());
			assertEquals(RoleStatus.INACTIVE, result.get(0).getStatus());
		}

		@Test
		@DisplayName("Should map assignment fields correctly to report row")
		void getUserRoleRows_ShouldMapFieldsCorrectly() {
			// Arrange
			LocalDate since = LocalDate.now().minusMonths(1);
			LocalDate when = LocalDate.now();
			List<Long> rowIds = List.of(1L);

			AttestationUserRoleAssignmentDto assignment = new AttestationUserRoleAssignmentDto(
					LocalDate.now().minusDays(30), // validFrom
					null,                          // validTo
					LocalDate.now(),               // updatedAt
					"hash",                        // recordHash
					"user-uuid",                   // userUuid
					"testuser",                    // userId
					"Test User",                   // userName
					1L,                            // userRoleId
					"Test Role",                   // userRoleName
					"Role Description",            // userRoleDescription
					null,                          // roleGroupId
					null,                          // roleGroupName
					null,                          // roleGroupDescription
					100L,                          // itSystemId
					"Test IT System",              // itSystemName
					null,                          // responsibleUserUuid
					"Responsible OU",              // responsibleOuName
					"role-ou-uuid",                // roleOuUuid - rollens enhed, kan ligge højere i hierarkiet
					"Test OU",                     // roleOuName
					"ou-uuid",                     // responsibleOuUuid - brugerens egen enhed
					AssignedThroughType.DIRECT,    // assignedThroughType
					"Direct Assignment",           // assignedThroughName
					null,                          // assignedThroughUuid
					false,                         // inherited
					false,                         // sensitiveRole
					false,							// extra sensitive role
					LocalDate.now().minusDays(30), // assignedFrom
					null                           // postponedConstraints
			);

			when(attestationUserRoleAssignmentDao.findByIdIn(rowIds)).thenReturn(List.of(assignment));
			// Opslag i den ansvarlige enhed, ikke i rollens - ellers er kolonnen tom for nedarvede roller.
			when(cachedUserService.getUserPositionsCached("user-uuid", "ou-uuid")).thenReturn("Developer");

			// Act
			List<RoleAssignmentReportRowDTO> result = attestationReportService.getUserRoleRows(since, when, rowIds);

			// Assert
			assertEquals(1, result.size());
			RoleAssignmentReportRowDTO row = result.get(0);
			assertEquals("Test IT System", row.getItSystemName());
			assertEquals(100L, row.getItSystemId());
			assertEquals("Test Role", row.getUserRoleName());
			assertEquals("Test User", row.getUserName());
			assertEquals("testuser", row.getUserUserId());
			assertEquals("Test OU", row.getOrgUnit());
			assertEquals("Direkte", row.getAssignedThroughType());
			assertEquals("Developer", row.getPosition());
			assertFalse(row.isInherited());
		}

		@Test
		@DisplayName("Should use default since date when null provided")
		void getUserRoleRows_WhenSinceIsNull_ShouldUseDefaultDate() {
			// Arrange
			LocalDate when = LocalDate.now();
			List<Long> rowIds = List.of(1L);

			AttestationUserRoleAssignmentDto assignment = createAssignment(1L, null);

			when(attestationUserRoleAssignmentDao.findByIdIn(rowIds)).thenReturn(List.of(assignment));
			when(cachedUserService.getUserPositionsCached(any(), any())).thenReturn("Test Position");

			// Act
			List<RoleAssignmentReportRowDTO> result = attestationReportService.getUserRoleRows(null, when, rowIds);

			// Assert
			assertEquals(1, result.size());
			// Verify context was created with date one year before 'when'
			verify(attestationReportContextService).createContext(when.minusYears(1));
		}
	}

	@Nested
	@DisplayName("getAllRolesReportModel() Tests")
	class GetAllRolesReportModelTests {

		@Test
		@DisplayName("Should return fully populated row when it-system has no attestation in the period")
		void getAllRolesReportModel_WhenNoAttestation_ShouldStillPopulateRow() {
			// Arrange
			ItSystem itSystem = itSystem(100L, "KMD Opus");
			UserRole userRole = userRole(1L, "Lønmedarbejder");
			when(itSystemService.findAllForAttestation()).thenReturn(List.of(itSystem));
			when(attestationDao.findItSystemRoleAttestations(eq(100L), any(), any())).thenReturn(Collections.emptyList());
			when(userRoleService.getByItSystem(itSystem)).thenReturn(List.of(userRole));

			// Act
			List<ITSystemRoleBuildAttestationDTO> rows = allRolesRows();

			// Assert - the xls view joins these lists and reads the status, so they must never be null
			assertEquals(1, rows.size());
			ITSystemRoleBuildAttestationDTO row = rows.get(0);
			assertNotNull(row.getResponsibleUserNames());
			assertTrue(row.getResponsibleUserNames().isEmpty());
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
		}

		@Test
		@DisplayName("Should resolve responsible users to names, not uuids")
		void getAllRolesReportModel_WithResponsibleCollection_ShouldReturnUserNames() {
			// Arrange
			ItSystem itSystem = itSystem(100L, "KMD Opus");
			UserRole userRole = userRole(1L, "Lønmedarbejder");
			Attestation attestation = new Attestation();
			attestation.setResponsibleCollectionId(7L);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection();
			collection.setUsersUuid(List.of("uuid-1"));

			when(itSystemService.findAllForAttestation()).thenReturn(List.of(itSystem));
			when(attestationDao.findItSystemRoleAttestations(eq(100L), any(), any())).thenReturn(List.of(attestation));
			when(userRoleService.getByItSystem(itSystem)).thenReturn(List.of(userRole));
			when(attestationResponsibleCollectionDao.findById(7L)).thenReturn(Optional.of(collection));
			when(cachedUserService.userNameFromUuidCached("uuid-1")).thenReturn("Camilla Kronborg Sode");

			// Act
			List<ITSystemRoleBuildAttestationDTO> rows = allRolesRows();

			// Assert
			assertEquals(1, rows.size());
			assertEquals(List.of("Camilla Kronborg Sode"), rows.get(0).getResponsibleUserNames());
		}

		@Test
		@DisplayName("Should return no responsible users when the attestation has no responsible collection")
		void getAllRolesReportModel_WithoutResponsibleCollection_ShouldReturnEmptyList() {
			// Arrange
			ItSystem itSystem = itSystem(100L, "KMD Opus");
			UserRole userRole = userRole(1L, "Lønmedarbejder");
			Attestation attestation = new Attestation();
			attestation.setResponsibleCollectionId(null);

			when(itSystemService.findAllForAttestation()).thenReturn(List.of(itSystem));
			when(attestationDao.findItSystemRoleAttestations(eq(100L), any(), any())).thenReturn(List.of(attestation));
			when(userRoleService.getByItSystem(itSystem)).thenReturn(List.of(userRole));

			// Act
			List<ITSystemRoleBuildAttestationDTO> rows = allRolesRows();

			// Assert
			assertEquals(1, rows.size());
			assertNotNull(rows.get(0).getResponsibleUserNames());
			assertTrue(rows.get(0).getResponsibleUserNames().isEmpty());
		}

		@SuppressWarnings("unchecked")
		private List<ITSystemRoleBuildAttestationDTO> allRolesRows() {
			LocalDate when = LocalDate.now();
			Map<String, Object> model = attestationReportService.getAllRolesReportModel(Locale.of("da"), when.minusYears(1), when);
			return (List<ITSystemRoleBuildAttestationDTO>) model.get("itSystemRoleAttestationDTO");
		}

		private ItSystem itSystem(long id, String name) {
			ItSystem itSystem = new ItSystem();
			itSystem.setId(id);
			itSystem.setName(name);
			return itSystem;
		}

		private UserRole userRole(long id, String name) {
			UserRole userRole = new UserRole();
			userRole.setId(id);
			userRole.setName(name);
			userRole.setSystemRoleAssignments(Collections.emptyList());
			return userRole;
		}
	}

	@Nested
	@DisplayName("getItSystemRowIds() Tests")
	class GetItSystemRowIdsTests {

		@Test
		@DisplayName("Should call dao with correct parameters")
		void getItSystemRowIds_ShouldCallDaoWithCorrectParameters() {
			// Arrange
			LocalDate since = LocalDate.of(2024, 1, 1);
			LocalDate when = LocalDate.of(2024, 6, 1);
			dk.digitalidentity.rc.dao.model.ItSystem itSystem = new dk.digitalidentity.rc.dao.model.ItSystem();
			itSystem.setId(100L);

			when(attestationUserRoleAssignmentDao.listAssignmentValidBetweenForItSystem(eq(100L), any(), any()))
					.thenReturn(List.of(1L, 2L, 3L));

			// Act
			List<Long> result = attestationReportService.getItSystemRowIds(since, when, itSystem);

			// Assert
			assertEquals(3, result.size());
			verify(attestationUserRoleAssignmentDao).listAssignmentValidBetweenForItSystem(
					eq(100L),
					eq(since),
					eq(when.plusDays(1))
			);
		}

		@Test
		@DisplayName("Should use default since date when null")
		void getItSystemRowIds_WhenSinceNull_ShouldUseDefaultDate() {
			// Arrange
			LocalDate when = LocalDate.of(2024, 6, 1);
			dk.digitalidentity.rc.dao.model.ItSystem itSystem = new dk.digitalidentity.rc.dao.model.ItSystem();
			itSystem.setId(100L);

			when(attestationUserRoleAssignmentDao.listAssignmentValidBetweenForItSystem(anyLong(), any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			attestationReportService.getItSystemRowIds(null, when, itSystem);

			// Assert
			verify(attestationUserRoleAssignmentDao).listAssignmentValidBetweenForItSystem(
					eq(100L),
					eq(when.minusYears(1)),
					eq(when.plusDays(1))
			);
		}
	}

	private AttestationUserRoleAssignmentDto createAssignment(Long id, LocalDate validTo) {
		return new AttestationUserRoleAssignmentDto(
				LocalDate.now().minusDays(30), // validFrom
				validTo,                       // validTo
				LocalDate.now(),               // updatedAt
				"hash",                        // recordHash
				"user-uuid-" + id,             // userUuid
				"testuser",                    // userId
				"Test User",                   // userName
				1L,                            // userRoleId
				"Test Role",                   // userRoleName
				"Role Description",            // userRoleDescription
				null,                          // roleGroupId
				null,                          // roleGroupName
				null,                          // roleGroupDescription
				1L,                            // itSystemId
				"Test System",                 // itSystemName
				null,                          // responsibleUserUuid
				"Responsible OU",              // responsibleOuName
				"ou-uuid",                     // roleOuUuid
				"Test OU",                     // roleOuName
				"ou-uuid",                     // responsibleOuUuid
				AssignedThroughType.DIRECT,    // assignedThroughType
				"Direct Assignment",           // assignedThroughName
				null,                          // assignedThroughUuid
				false,                         // inherited
				false,                         // sensitiveRole
				false,                         // extraSensitiveRole
				LocalDate.now().minusDays(30), // assignedFrom
				null                           // postponedConstraints
		);
	}

	private Attestation createAttestation(Long id, ZonedDateTime verifiedAt) {
		Attestation attestation = new Attestation();
		attestation.setId(id);
		attestation.setCreatedAt(LocalDate.now());
		attestation.setVerifiedAt(verifiedAt);
		attestation.setItSystemUserAttestationEntries(new HashSet<>());
		attestation.setOrganisationUserAttestationEntries(new HashSet<>());
		attestation.setItSystemOrganisationAttestationEntries(new HashSet<>());
		return attestation;
	}
}
