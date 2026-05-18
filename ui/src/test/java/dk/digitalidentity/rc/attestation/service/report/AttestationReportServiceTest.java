package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.RoleStatus;
import dk.digitalidentity.rc.attestation.model.dto.temporal.AttestationUserRoleAssignmentDto;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
					"ou-uuid",                     // roleOuUuid
					"Test OU",                     // roleOuName
					null,                          // responsibleOuUuid
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
				null,                          // responsibleOuUuid
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
