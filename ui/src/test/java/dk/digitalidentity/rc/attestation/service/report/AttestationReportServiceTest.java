package dk.digitalidentity.rc.attestation.service.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import dk.digitalidentity.rc.attestation.model.entity.AttestationUser;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.service.AttestationCachedUserService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
		@DisplayName("Should fall back to the responsible OU when the assignment has no role OU")
		void getUserRoleRows_WithoutRoleOu_ShouldReportResponsibleOu() {
			// Arrange
			List<Long> rowIds = List.of(1L);
			AttestationUserRoleAssignmentDto assignment = new AttestationUserRoleAssignmentDto(
					LocalDate.now().minusDays(30), null, LocalDate.now(), "hash",
					"user-uuid", "testuser", "Test User", 1L, "Test Role", null,
					null, null, null, 1L, "Test System", null,
					"Responsible OU",              // responsibleOuName
					null,                          // roleOuUuid
					null,                          // roleOuName
					"ou-uuid",                     // responsibleOuUuid
					AssignedThroughType.DIRECT, null, null,
					false, false, false, LocalDate.now().minusDays(30), null
			);
			when(attestationUserRoleAssignmentDao.findByIdIn(rowIds)).thenReturn(List.of(assignment));

			// Act
			List<RoleAssignmentReportRowDTO> result = attestationReportService.getUserRoleRows(LocalDate.now().minusMonths(1), LocalDate.now(), rowIds);

			// Assert
			assertEquals("Responsible OU", result.get(0).getOrgUnit());
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
	@DisplayName("Attestation status Tests")
	class AttestationStatusTests {

		private static final LocalDate TODAY = LocalDate.now();
		private static final String OWN_OU = "own-ou-uuid";
		private static final String PARENT_OU = "parent-ou-uuid";
		private static final String USER = "user-uuid";

		@Test
		@DisplayName("Should not report a status for an assignment that had ended when the attestation was created")
		void status_WhenAssignmentEndedBeforeAttestation_ShouldReportNothing() {
			// Arrange - the assignment was gone by the time the manager was asked, so it was not part of the attestation
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), TODAY.minusDays(30), null);
			Attestation attestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), userEntry(null, Collections.emptySet()));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertNull(row.getAttestationCreatedAt());
			assertNull(row.getVerifiedAt());
		}

		@Test
		@DisplayName("Should report the users decision for a role that came with a role group")
		void status_WithRoleGroupAssignment_ShouldReportUserDecision() {
			// Arrange - a role group assignment is direct, but carries the role group id as assignedThroughUuid
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), null, 42L);
			Attestation attestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), userEntry(null, Collections.emptySet()));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.APPROVED, row.getAttestationStatus());
			assertFalse(row.isAttestationInProgress());
		}

		@Test
		@DisplayName("Should mark a decision taken in an attestation that is not finished")
		void status_WithUnfinishedAttestation_ShouldMarkInProgress() {
			// Arrange - the entry is written when the manager decides, verifiedAt only when everyone is done
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), null, null);
			Attestation attestation = ouAttestation(TODAY.minusDays(10), null, userEntry("Skal fratages", Set.of("1")));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.REMARKS, row.getAttestationStatus());
			assertEquals("Skal fratages", row.getRemark());
			assertTrue(row.isAttestationInProgress());
		}

		@Test
		@DisplayName("Should report a role assigned on a parent org unit from that org units attestation")
		void status_WithOrgUnitAssignment_ShouldUseAttestationOfAssigningOrgUnit() {
			// Arrange - roleOuUuid holds the user's own org unit on rows written before the updater was rewritten
			AttestationUserRoleAssignmentDto assignment = assignment(TODAY.minusDays(60), null, null,
					AssignedThroughType.ORGUNIT, PARENT_OU, OWN_OU, "Own OU");
			Attestation attestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), null);
			attestation.setResponsibleOuUuid(PARENT_OU);
			attestation.setOrganisationRolesAttestationEntry(OrganisationRoleAttestationEntry.builder()
					.performedByUserId("CHEF")
					.remarks("Rollen skal fjernes fra enheden")
					.createdAt(ZonedDateTime.now())
					.build());

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.REMARKS, row.getAttestationStatus());
			assertEquals("Rollen skal fjernes fra enheden", row.getRemark());
			assertTrue(row.isInherited());
		}

		@Test
		@DisplayName("Should report the newest covering attestation when none has decided yet")
		void status_WhenCoveringAttestationHasNoDecision_ShouldReportNotVerified() {
			// Arrange - an older attestation also covered it undecided, and must not be the one reported
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(400), null, null);
			Attestation older = ouAttestation(TODAY.minusDays(300), ZonedDateTime.now().minusDays(299), null);
			Attestation attestation = ouAttestation(TODAY.minusDays(10), null, null);

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation, older);

			// Assert - separates "not answered yet" from "never part of an attestation"
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertEquals(TODAY.minusDays(10), row.getAttestationCreatedAt());
			assertFalse(row.isInherited());
		}

		@Test
		@DisplayName("Should report the decision from the previous attestation while a new one is in progress")
		void status_WhenNewerAttestationIsUndecided_ShouldReportThePreviousDecision() {
			// Arrange - the new run has an attestation for the org unit, but the manager has not answered yet
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(400), null, null);
			Attestation lastYear = ouAttestation(TODAY.minusDays(300), ZonedDateTime.now().minusDays(299), userEntry(null, Collections.emptySet()));
			Attestation inProgress = ouAttestation(TODAY.minusDays(10), null, null);

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, inProgress, lastYear);

			// Assert
			assertEquals(AttestationStatus.APPROVED, row.getAttestationStatus());
			assertFalse(row.isAttestationInProgress());
		}

		@Test
		@DisplayName("Should not report an attestation created after the reported period")
		void status_WithAttestationAfterReportedPeriod_ShouldReportNothing() {
			// Arrange - the audit report of one run must not show what a later run decided
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), null, null);
			Attestation attestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), userEntry(null, Collections.emptySet()));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, TODAY.minusDays(20), attestation);

			// Assert
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertNull(row.getAttestationCreatedAt());
		}

		@Test
		@DisplayName("Should report a role assigned on selected titles from the org units attestation")
		void status_WithTitleAssignment_ShouldUseAttestationOfAssigningOrgUnit() {
			// Arrange - a title assignment carries the title in assignedThroughUuid, so the ou is only in roleOuUuid
			AttestationUserRoleAssignmentDto assignment = assignment(TODAY.minusDays(60), null, null,
					AssignedThroughType.TITLE, "title-uuid", PARENT_OU, "Parent OU");
			Attestation attestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), null);
			attestation.setResponsibleOuUuid(PARENT_OU);
			attestation.setOrganisationRolesAttestationEntry(OrganisationRoleAttestationEntry.builder()
					.performedByUserId("CHEF")
					.createdAt(ZonedDateTime.now())
					.build());

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.APPROVED, row.getAttestationStatus());
		}

		@Test
		@DisplayName("Should keep the responsible attestation when an it-system lookup decides nothing")
		void status_WhenItSystemLookupDecidesNothing_ShouldKeepResponsibleAttestation() {
			// Arrange - the manager's finished attestation covers the role but has no decision on the org unit's roles,
			// while an it-system attestation for the same system is still running
			AttestationUserRoleAssignmentDto assignment = assignment(TODAY.minusDays(60), null, null,
					AssignedThroughType.ORGUNIT, PARENT_OU, OWN_OU, "Own OU");
			Attestation ouAttestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), null);
			ouAttestation.setResponsibleOuUuid(PARENT_OU);
			Attestation itSystemAttestation = itSystemAttestation(TODAY.minusDays(10));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, TODAY, List.of(ouAttestation), List.of(itSystemAttestation));

			// Assert - the row belongs to the manager, so it must not inherit the it-system attestation's progress
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertEquals(TODAY.minusDays(10), row.getAttestationCreatedAt());
			assertFalse(row.isAttestationInProgress());
		}

		@Test
		@DisplayName("Should mark a row in the collection an undecided it-system attestation asked about")
		void status_WhenItSystemAttestationForTheRowsCollectionIsUndecided_ShouldMarkTheRow() {
			// Arrange - the it-system responsible was asked about the assignment, but has not answered
			AttestationUserRoleAssignmentDto assignment = assignment(TODAY.minusDays(60), null, null,
					AssignedThroughType.ORGUNIT, PARENT_OU, OWN_OU, "Own OU", 7L);
			Attestation itSystemAttestation = itSystemAttestation(TODAY.minusDays(10));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, TODAY, Collections.emptyList(), List.of(itSystemAttestation));

			// Assert - it was part of an attestation, so it is not verified rather than outside attestation
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertEquals(TODAY.minusDays(10), row.getAttestationCreatedAt());
		}

		@Test
		@DisplayName("Should not mark a row outside the collection of an undecided it-system attestation")
		void status_WhenItSystemAttestationIsForAnotherCollection_ShouldReportNothing() {
			// Arrange - nobody was asked about the assignment, an it-system responsible only attested the same it-system
			AttestationUserRoleAssignmentDto assignment = assignment(TODAY.minusDays(60), null, null,
					AssignedThroughType.ORGUNIT, PARENT_OU, OWN_OU, "Own OU");
			Attestation itSystemAttestation = itSystemAttestation(TODAY.minusDays(10));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, TODAY, Collections.emptyList(), List.of(itSystemAttestation));

			// Assert
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertNull(row.getAttestationCreatedAt());
		}

		@Test
		@DisplayName("Should mark a user with sensitive roles that a sensitive attestation has not decided on")
		void status_WithSensitiveAttestationAndSensitiveUser_ShouldMarkTheRow() {
			// Arrange
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), null, null);
			Attestation attestation = sensitiveOuAttestation(true, false);

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertEquals(TODAY.minusDays(10), row.getAttestationCreatedAt());
		}

		@Test
		@DisplayName("Should report a sensitive attestations decision after the user left its rebuilt user list")
		void status_WithSensitiveAttestationDecisionOnUserNoLongerListed_ShouldReportDecision() {
			// Arrange - the user list is rebuilt nightly from current assignments, so a removed role takes the user out
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), null, null);
			Attestation attestation = sensitiveOuAttestation(false, true);

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.APPROVED, row.getAttestationStatus());
		}

		@Test
		@DisplayName("Should not mark a user a sensitive attestation did not ask about")
		void status_WithSensitiveAttestationAndOtherUser_ShouldReportNothing() {
			// Arrange - the org unit is in the sensitive run because of another user
			AttestationUserRoleAssignmentDto assignment = directAssignment(TODAY.minusDays(60), null, null);
			Attestation attestation = sensitiveOuAttestation(false, false);

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, attestation);

			// Assert
			assertEquals(AttestationStatus.NOT_VERIFIED, row.getAttestationStatus());
			assertNull(row.getAttestationCreatedAt());
		}

		@Test
		@DisplayName("Should report an it-system responsibles decision on the assigning org unit")
		void status_WhenItSystemResponsibleDecidedTheOrgUnit_ShouldReportThatDecision() {
			// Arrange - the manager has not decided, but the it-system responsible attested the org unit the role sits on
			AttestationUserRoleAssignmentDto assignment = assignment(TODAY.minusDays(60), null, null,
					AssignedThroughType.ORGUNIT, PARENT_OU, OWN_OU, "Own OU");
			Attestation ouAttestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(), null);
			ouAttestation.setResponsibleOuUuid(PARENT_OU);
			Attestation itSystemAttestation = itSystemAttestation(TODAY.minusDays(10));
			itSystemAttestation.setItSystemOrganisationAttestationEntries(new HashSet<>(Set.of(
					ItSystemOrganisationAttestationEntry.builder()
							.organisationUuid(PARENT_OU)
							.performedByUserId("SYSTEMANSVARLIG")
							.createdAt(ZonedDateTime.now())
							.build())));

			// Act
			RoleAssignmentReportRowDTO row = rowFor(assignment, TODAY, List.of(ouAttestation), List.of(itSystemAttestation));

			// Assert
			assertEquals(AttestationStatus.APPROVED, row.getAttestationStatus());
		}

		private RoleAssignmentReportRowDTO rowFor(final AttestationUserRoleAssignmentDto assignment, final Attestation... attestations) {
			return rowFor(assignment, TODAY, attestations);
		}

		private RoleAssignmentReportRowDTO rowFor(final AttestationUserRoleAssignmentDto assignment, final LocalDate reportedUntil,
				final Attestation... attestations) {
			return rowFor(assignment, reportedUntil, List.of(attestations), Collections.emptyList());
		}

		private RoleAssignmentReportRowDTO rowFor(final AttestationUserRoleAssignmentDto assignment, final LocalDate reportedUntil,
				final List<Attestation> ouAttestations, final List<Attestation> itSystemAttestations) {
			when(attestationReportContextService.createContext(any(LocalDate.class)))
					.thenReturn(AttestationReportContextService.AttestationReportContext.builder()
							.itSystemUserAttestations(itSystemAttestations)
							.itSystemRolesAttestations(Collections.emptyList())
							.organisationRolesAttestations(ouAttestations)
							.build());
			when(attestationUserRoleAssignmentDao.findByIdIn(List.of(1L))).thenReturn(List.of(assignment));

			List<RoleAssignmentReportRowDTO> rows = attestationReportService.getUserRoleRows(TODAY.minusYears(1), reportedUntil, List.of(1L));
			assertEquals(1, rows.size());
			return rows.get(0);
		}

		private Attestation itSystemAttestation(final LocalDate createdAt) {
			Attestation attestation = new Attestation();
			attestation.setCreatedAt(createdAt);
			attestation.setAttestationType(Attestation.AttestationType.IT_SYSTEM_ATTESTATION);
			attestation.setItSystemId(100L);
			attestation.setResponsibleCollectionId(7L);
			return attestation;
		}

		private Attestation sensitiveOuAttestation(final boolean userHasSensitiveRoles, final boolean decided) {
			Attestation attestation = ouAttestation(TODAY.minusDays(10), ZonedDateTime.now(),
					decided ? userEntry(null, Collections.emptySet()) : null);
			attestation.setSensitive(true);
			if (userHasSensitiveRoles) {
				attestation.setUsersForAttestation(new HashSet<>(Set.of(AttestationUser.builder()
						.userUuid(USER)
						.sensitiveRoles(true)
						.build())));
			}
			return attestation;
		}

		private OrganisationUserAttestationEntry userEntry(final String remarks, final Set<String> rejectedUserRoleIds) {
			return OrganisationUserAttestationEntry.builder()
					.userUuid(USER)
					.remarks(remarks)
					.rejectedUserRoleIds(new HashSet<>(rejectedUserRoleIds))
					.performedByUserId("CHEF")
					.createdAt(ZonedDateTime.now())
					.build();
		}

		private Attestation ouAttestation(final LocalDate createdAt, final ZonedDateTime verifiedAt, final OrganisationUserAttestationEntry entry) {
			Attestation attestation = new Attestation();
			attestation.setCreatedAt(createdAt);
			attestation.setVerifiedAt(verifiedAt);
			attestation.setAttestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION);
			attestation.setResponsibleOuUuid(OWN_OU);
			attestation.setOrganisationUserAttestationEntries(entry == null ? new HashSet<>() : new HashSet<>(Set.of(entry)));
			return attestation;
		}

		private AttestationUserRoleAssignmentDto directAssignment(final LocalDate validFrom, final LocalDate validTo, final Long roleGroupId) {
			return assignment(validFrom, validTo, roleGroupId, AssignedThroughType.DIRECT,
					roleGroupId != null ? String.valueOf(roleGroupId) : null, null, null);
		}

		private AttestationUserRoleAssignmentDto assignment(final LocalDate validFrom, final LocalDate validTo, final Long roleGroupId,
				final AssignedThroughType assignedThroughType, final String assignedThroughUuid,
				final String roleOuUuid, final String roleOuName) {
			return assignment(validFrom, validTo, roleGroupId, assignedThroughType, assignedThroughUuid, roleOuUuid, roleOuName, null);
		}

		private AttestationUserRoleAssignmentDto assignment(final LocalDate validFrom, final LocalDate validTo, final Long roleGroupId,
				final AssignedThroughType assignedThroughType, final String assignedThroughUuid,
				final String roleOuUuid, final String roleOuName, final Long responsibleCollectionId) {
			return new AttestationUserRoleAssignmentDto(
					validFrom, validTo, TODAY, "hash",
					USER, "testuser", "Test User",
					1L, "Test Role", "Role Description",
					roleGroupId, roleGroupId != null ? "Test Role Group" : null, null,
					100L, "Test IT System",
					responsibleCollectionId,
					"Own OU",                      // responsibleOuName
					roleOuUuid, roleOuName,
					OWN_OU,                        // responsibleOuUuid
					assignedThroughType, "Assigned Through", assignedThroughUuid,
					false, false, false,
					validFrom, null);
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
			when(cachedUserService.userNameFromUuidCached("uuid-1")).thenReturn("Systemansvarlig Bruger");

			// Act
			List<ITSystemRoleBuildAttestationDTO> rows = allRolesRows();

			// Assert
			assertEquals(1, rows.size());
			assertEquals(List.of("Systemansvarlig Bruger"), rows.get(0).getResponsibleUserNames());
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

		@Test
		@DisplayName("Should return the remark given when the role build was attested")
		void getAllRolesReportModel_WithRemark_ShouldReturnRemark() {
			// Arrange
			ItSystem itSystem = itSystem(100L, "KMD Opus");
			UserRole userRole = userRole(1L, "Lønmedarbejder");
			Attestation attestation = new Attestation();
			attestation.setItSystemUserRoleAttestationEntries(new HashSet<>(List.of(ItSystemRoleAttestationEntry.builder()
				.userRoleId(1L)
				.remarks("Rollen skal ikke give adgang til borgersager")
				.createdAt(ZonedDateTime.now())
				.performedByUserId("ABCDE")
				.build())));

			when(itSystemService.findAllForAttestation()).thenReturn(List.of(itSystem));
			when(attestationDao.findItSystemRoleAttestations(eq(100L), any(), any())).thenReturn(List.of(attestation));
			when(userRoleService.getByItSystem(itSystem)).thenReturn(List.of(userRole));

			// Act
			List<ITSystemRoleBuildAttestationDTO> rows = allRolesRows();

			// Assert
			assertEquals(1, rows.size());
			assertEquals(AttestationStatus.REMARKS, rows.get(0).getAttestationStatus());
			assertEquals("Rollen skal ikke give adgang til borgersager", rows.get(0).getRemark());
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
}
