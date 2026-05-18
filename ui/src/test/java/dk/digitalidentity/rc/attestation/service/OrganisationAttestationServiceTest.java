package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitRoleGroupAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationOuRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.OrganisationUserAttestationEntry;
import dk.digitalidentity.rc.dao.FunctionDao;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;
import dk.digitalidentity.rc.service.EmailQueueService;
import dk.digitalidentity.rc.service.EmailTemplateService;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createDisabledEmailTemplate;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createMixedRoleAssignments;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createOrganisationAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Organisation Attestation Service Tests")
class OrganisationAttestationServiceTest {

	private static final String OU_UUID = "ou-uuid";
	private static final String USER_UUID = "user-uuid";
	private static final String PERFORMER_USER_ID = "performer-user-id";
	private static final String PERFORMER_USER_UUID = "performer-user-uuid";

	@Mock
	private AttestationUserRoleAssignmentDao userRoleAssignmentDao;

	@Mock
	private AttestationOuAssignmentsDao ouAssignmentsDao;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private OrganisationUserAttestationEntryDao organisationUserAttestationEntryDao;

	@Mock
	private OrganisationRoleAttestationEntryDao organisationRoleAttestationEntryDao;

	@Mock
	private ManagerSubstituteService managerSubstituteService;

	@Mock
	private UserService userService;

	@Mock
	private AttestationCachedUserService attestationUserService;

	@Mock
	private OrgUnitService orgUnitService;

	@Mock
	private AttestationEmailNotificationService emailNotificationService;

	@Mock
	private TitleDao titleDao;

	@Mock
	private FunctionDao functionDao;

	@Mock
	private SettingsService settingsService;

	@Mock
	private EmailTemplateService emailTemplateService;

	@Mock
	private EmailQueueService emailQueueService;

	@Mock
	private UserRoleService userRoleService;

	@Mock
	private RoleGroupService roleGroupService;

	@InjectMocks
	private OrganisationAttestationService organisationAttestationService;

	private User performingUser;
	private EmailTemplate summaryEmailTemplate;

	@BeforeEach
	void setUp() {
		performingUser = createUser(PERFORMER_USER_UUID, PERFORMER_USER_ID, "Performing User", "performer@example.com");
		summaryEmailTemplate = createDisabledEmailTemplate(EmailTemplateType.ATTESTATION_SUMMARY);
	}

	@Nested
	@DisplayName("verifyUser() Tests")
	class VerifyUserTests {

		@Test
		@DisplayName("Should throw exception when user tries to verify themselves")
		void verifyUser_WhenUserVerifiesSelf_ShouldThrowException() {
			// Arrange
			User selfUser = createUser(USER_UUID, PERFORMER_USER_ID, "Self User");
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(selfUser);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> organisationAttestationService.verifyUser(OU_UUID, USER_UUID, PERFORMER_USER_ID,
							Attestation.AttestationType.ORGANISATION_ATTESTATION));
		}

		@Test
		@DisplayName("Should verify user and save attestation entry")
		void verifyUser_ShouldSaveAttestationEntry() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));
			when(organisationUserAttestationEntryDao.save(any(OrganisationUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(settingsService.isADAttestationEnabled()).thenReturn(false);
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY))
					.thenReturn(summaryEmailTemplate);

			// Act
			organisationAttestationService.verifyUser(OU_UUID, USER_UUID, PERFORMER_USER_ID,
					Attestation.AttestationType.ORGANISATION_ATTESTATION);

			// Assert
			ArgumentCaptor<OrganisationUserAttestationEntry> captor = ArgumentCaptor.forClass(OrganisationUserAttestationEntry.class);
			verify(organisationUserAttestationEntryDao).save(captor.capture());
			OrganisationUserAttestationEntry savedEntry = captor.getValue();
			assertEquals(USER_UUID, savedEntry.getUserUuid());
			assertEquals(PERFORMER_USER_ID, savedEntry.getPerformedByUserId());
			assertNotNull(savedEntry.getCreatedAt());
		}
	}

	@Nested
	@DisplayName("rejectUser() Tests")
	class RejectUserTests {

		@Test
		@DisplayName("Should throw exception when user tries to reject themselves")
		void rejectUser_WhenUserRejectsSelf_ShouldThrowException() {
			// Arrange
			User selfUser = createUser(USER_UUID, PERFORMER_USER_ID, "Self User");
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(selfUser);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> organisationAttestationService.rejectUser(OU_UUID, USER_UUID, PERFORMER_USER_ID,
							"remarks", Collections.emptyList(), Attestation.AttestationType.ORGANISATION_ATTESTATION));
		}

		@Test
		@DisplayName("Should reject user and send notification email")
		void rejectUser_ShouldSaveEntryAndSendEmail() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			String remarks = "Test remarks";
			List<RoleAssignmentDTO> notApproved = createMixedRoleAssignments();
			User targetUser = createUser(USER_UUID, "target-user-id", "Target User");

			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));
			when(organisationUserAttestationEntryDao.save(any(OrganisationUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(userService.getByUuid(USER_UUID)).thenReturn(targetUser);
			when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(settingsService.isADAttestationEnabled()).thenReturn(false);
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY))
					.thenReturn(summaryEmailTemplate);

			// Act
			organisationAttestationService.rejectUser(OU_UUID, USER_UUID, PERFORMER_USER_ID, remarks, notApproved,
					Attestation.AttestationType.ORGANISATION_ATTESTATION);

			// Assert
			verify(emailNotificationService).sendRequestForChangeMail(
					"Performing User(performer-user-id)",
					"Target User(target-user-id)",
					remarks,
					notApproved);
		}

		@Test
		@DisplayName("Should not send notification when target user not found")
		void rejectUser_WhenTargetUserNotFound_ShouldNotSendNotification() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");

			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));
			when(organisationUserAttestationEntryDao.save(any(OrganisationUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(userService.getByUuid(USER_UUID)).thenReturn(null);
			when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(settingsService.isADAttestationEnabled()).thenReturn(false);
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY))
					.thenReturn(summaryEmailTemplate);

			// Act
			organisationAttestationService.rejectUser(OU_UUID, USER_UUID, PERFORMER_USER_ID, "remarks",
					Collections.emptyList(), Attestation.AttestationType.ORGANISATION_ATTESTATION);

			// Assert
			verify(emailNotificationService, never()).sendRequestForChangeMail(any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("requestAdRemoval() Tests")
	class RequestAdRemovalTests {

		@Test
		@DisplayName("Should throw exception when user tries to request their own removal")
		void requestAdRemoval_WhenUserRequestsSelf_ShouldThrowException() {
			// Arrange
			User selfUser = createUser(USER_UUID, PERFORMER_USER_ID, "Self User");
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(selfUser);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> organisationAttestationService.requestAdRemoval(OU_UUID, USER_UUID, PERFORMER_USER_ID,
							Attestation.AttestationType.ORGANISATION_ATTESTATION));
		}

		@Test
		@DisplayName("Should request AD removal and send notification")
		void requestAdRemoval_ShouldSaveEntryAndSendEmail() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			User targetUser = createUser(USER_UUID, "target-user-id", "Target User");

			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));
			when(organisationUserAttestationEntryDao.save(any(OrganisationUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(userService.getByUuid(USER_UUID)).thenReturn(targetUser);
			when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(settingsService.isADAttestationEnabled()).thenReturn(false);
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY))
					.thenReturn(summaryEmailTemplate);

			// Act
			organisationAttestationService.requestAdRemoval(OU_UUID, USER_UUID, PERFORMER_USER_ID,
					Attestation.AttestationType.ORGANISATION_ATTESTATION);

			// Assert
			ArgumentCaptor<OrganisationUserAttestationEntry> captor = ArgumentCaptor.forClass(OrganisationUserAttestationEntry.class);
			verify(organisationUserAttestationEntryDao).save(captor.capture());
			assertTrue(captor.getValue().isAdRemoval());

			verify(emailNotificationService).sendRequestForAdRemoval(
					"Performing User(performer-user-id)",
					"Target User(target-user-id)",
					attestation.getResponsibleOuName());
		}
	}

	@Nested
	@DisplayName("acceptOrgUnitRoles() Tests")
	class AcceptOrgUnitRolesTests {

		@Test
		@DisplayName("Should accept org unit roles and save entry")
		void acceptOrgUnitRoles_ShouldSaveEntry() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");

			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));
			when(organisationRoleAttestationEntryDao.save(any(OrganisationRoleAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(settingsService.isADAttestationEnabled()).thenReturn(false);
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY))
					.thenReturn(summaryEmailTemplate);

			// Act
			organisationAttestationService.acceptOrgUnitRoles(OU_UUID, PERFORMER_USER_ID,
					Attestation.AttestationType.ORGANISATION_ATTESTATION);

			// Assert
			ArgumentCaptor<OrganisationRoleAttestationEntry> captor = ArgumentCaptor.forClass(OrganisationRoleAttestationEntry.class);
			verify(organisationRoleAttestationEntryDao).save(captor.capture());
			OrganisationRoleAttestationEntry savedEntry = captor.getValue();
			assertEquals(PERFORMER_USER_ID, savedEntry.getPerformedByUserId());
			assertNotNull(savedEntry.getCreatedAt());
		}

		@Test
		@DisplayName("Should throw exception when org roles already verified")
		void acceptOrgUnitRoles_WhenAlreadyVerified_ShouldThrowException() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			OrganisationRoleAttestationEntry existingEntry = OrganisationRoleAttestationEntry.builder()
					.performedByUserId("existing-user-id")
					.build();
			attestation.setOrganisationRolesAttestationEntry(existingEntry);

			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> organisationAttestationService.acceptOrgUnitRoles(OU_UUID, PERFORMER_USER_ID,
							Attestation.AttestationType.ORGANISATION_ATTESTATION));
		}
	}

	@Nested
	@DisplayName("rejectOrgUnitRoles() Tests")
	class RejectOrgUnitRolesTests {

		@Test
		@DisplayName("Should reject org unit roles and save entry with remarks")
		void rejectOrgUnitRoles_ShouldSaveEntryWithRemarks() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			String remarks = "Roles need modification";
			List<RoleAssignmentDTO> notApproved = createMixedRoleAssignments();
			OrgUnit orgUnit = createOrgUnit(OU_UUID, "Test OU");

			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.of(attestation));
			when(organisationRoleAttestationEntryDao.save(any(OrganisationRoleAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(userService.getOptionalByUuid(PERFORMER_USER_UUID)).thenReturn(Optional.of(performingUser));
			when(orgUnitService.getByUuid(OU_UUID)).thenReturn(orgUnit);
			when(ouAssignmentsDao.listValidNotInheritedAssignmentsForOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(userRoleAssignmentDao.listValidAssignmentsByResponsibleOu(any(), any()))
					.thenReturn(Collections.emptyList());
			when(settingsService.isADAttestationEnabled()).thenReturn(false);
			when(emailTemplateService.findByTemplateType(EmailTemplateType.ATTESTATION_SUMMARY))
					.thenReturn(summaryEmailTemplate);

			// Act
			organisationAttestationService.rejectOrgUnitRoles(OU_UUID, PERFORMER_USER_ID, remarks, notApproved,
					Attestation.AttestationType.ORGANISATION_ATTESTATION);

			// Assert
			ArgumentCaptor<OrganisationRoleAttestationEntry> captor = ArgumentCaptor.forClass(OrganisationRoleAttestationEntry.class);
			verify(organisationRoleAttestationEntryDao).save(captor.capture());
			OrganisationRoleAttestationEntry savedEntry = captor.getValue();
			assertEquals(remarks, savedEntry.getRemarks());
			assertNotNull(savedEntry.getRejectedUserRoleIds());
			assertNotNull(savedEntry.getRejectedRoleGroupIds());
		}
	}

	@Nested
	@DisplayName("hasAttestationWithinTheLastYear() Tests")
	class HasAttestationWithinTheLastYearTests {

		@Test
		@DisplayName("Should return false when no attestations exist")
		void hasAttestationWithinTheLastYear_WhenNoAttestations_ShouldReturnFalse() {
			// Arrange
			OrgUnit orgUnit = createOrgUnit(OU_UUID, "Test OU");
			when(attestationDao.findByCreatedAtGreaterThanEqualAndResponsibleOuUuid(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			boolean result = organisationAttestationService.hasAttestationWithinTheLastYear(orgUnit);

			// Assert
			assertFalse(result);
		}

		@Test
		@DisplayName("Should return true when attestations exist")
		void hasAttestationWithinTheLastYear_WhenAttestationsExist_ShouldReturnTrue() {
			// Arrange
			OrgUnit orgUnit = createOrgUnit(OU_UUID, "Test OU");
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			when(attestationDao.findByCreatedAtGreaterThanEqualAndResponsibleOuUuid(any(), any()))
					.thenReturn(Collections.singletonList(attestation));

			// Act
			boolean result = organisationAttestationService.hasAttestationWithinTheLastYear(orgUnit);

			// Assert
			assertTrue(result);
		}

		@Test
		@DisplayName("Should return false when attestations list is null")
		void hasAttestationWithinTheLastYear_WhenAttestationsNull_ShouldReturnFalse() {
			// Arrange
			OrgUnit orgUnit = createOrgUnit(OU_UUID, "Test OU");
			when(attestationDao.findByCreatedAtGreaterThanEqualAndResponsibleOuUuid(any(), any()))
					.thenReturn(null);

			// Act
			boolean result = organisationAttestationService.hasAttestationWithinTheLastYear(orgUnit);

			// Assert
			assertFalse(result);
		}
	}

	@Nested
	@DisplayName("isOrgVerified() Tests")
	class IsOrgVerifiedTests {

		@Test
		@DisplayName("Should return true when no role assignments exist")
		void isOrgVerified_WhenNoAssignments_ShouldReturnTrue() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");

			// Act
			boolean result = OrganisationAttestationService.isOrgVerified(
					attestation,
					Collections.emptyList(),
					Collections.emptyList());

			// Assert
			assertTrue(result);
		}

		@Test
		@DisplayName("Should return false when assignments exist but not verified")
		void isOrgVerified_WhenAssignmentsExistAndNotVerified_ShouldReturnFalse() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			List<OrgUnitRoleGroupAssignmentDTO> roleGroupAssignments = Collections.singletonList(
					OrgUnitRoleGroupAssignmentDTO.builder()
							.groupId(1L)
							.groupName("Test Group")
							.build()
			);

			// Act
			boolean result = OrganisationAttestationService.isOrgVerified(
					attestation,
					roleGroupAssignments,
					Collections.emptyList());

			// Assert
			assertFalse(result);
		}

		@Test
		@DisplayName("Should return true when assignments exist and attestation entry exists")
		void isOrgVerified_WhenAssignmentsExistAndVerified_ShouldReturnTrue() {
			// Arrange
			Attestation attestation = createOrganisationAttestation(1L, "attestation-uuid", OU_UUID, "Test OU");
			attestation.setOrganisationRolesAttestationEntry(
					OrganisationRoleAttestationEntry.builder()
							.performedByUserId("user-id")
							.build()
			);
			List<OrgUnitUserRoleAssignmentItSystemDTO> userRoleAssignments = Collections.singletonList(
					OrgUnitUserRoleAssignmentItSystemDTO.builder()
							.itSystemId(1L)
							.itSystemName("Test System")
							.build()
			);

			// Act
			boolean result = OrganisationAttestationService.isOrgVerified(
					attestation,
					Collections.emptyList(),
					userRoleAssignments);

			// Assert
			assertTrue(result);
		}
	}

	@Nested
	@DisplayName("getAttestation() Tests")
	class GetAttestationTests {

		@Test
		@DisplayName("Should return null when attestation not found")
		void getAttestation_WhenNotFound_ShouldReturnNull() {
			// Arrange
			when(attestationDao.findFirstByAttestationTypeAndResponsibleOuUuidOrderByDeadlineDesc(
					Attestation.AttestationType.ORGANISATION_ATTESTATION, OU_UUID))
					.thenReturn(Optional.empty());

			// Act
			var result = organisationAttestationService.getAttestation(OU_UUID, USER_UUID, false);

			// Assert
			assertNull(result);
		}
	}

	@Nested
	@DisplayName("orgUnitRoleGroups() Tests")
	class OrgUnitRoleGroupsTests {

		@Test
		@DisplayName("DTO.inherit mirrors the source inherit flag (not a derived value)")
		void inheritReflectsSourceFlag() {
			// Pre-existing bug fix: tidligere blev DTO.inherit beregnet som
			// !isInherited() && assignedThroughType==ORGUNIT — som er sandt for ALLE direkte
			// rolle-buket-tildelinger og dermed gav flueben i "Nedarves" uanset om inherit var
			// sat i kilde-tabellen. Skal i stedet læse a.isInherit() direkte.

			// ---- Given: rolle-buket tildelt på OU UDEN nedarvning ---- //
			AttestationOuRoleAssignment notInherited = AttestationOuRoleAssignment.builder()
				.roleGroupId(31L)
				.roleGroupName("Group")
				.roleId(100L)
				.assignedThroughType(AssignedThroughType.ORGUNIT)
				.assignedThroughUuid(OU_UUID)
				.ouUuid(OU_UUID)
				.titleUuids(List.of())
				.exceptedUserUuids(List.of())
				.exceptedTitleUuids(List.of())
				.functionUuids(List.of())
				.inherit(false)
				.inherited(false)
				.build();

			// ---- When ---- //
			List<OrgUnitRoleGroupAssignmentDTO> dtos =
				organisationAttestationService.orgUnitRoleGroups(List.of(notInherited));

			// ---- Then ---- //
			assertEquals(1, dtos.size());
			assertFalse(dtos.get(0).isInherit(), "DTO.inherit skal være false når source inherit=false");
		}

		@Test
		@DisplayName("DTO.inherit is true when the source assignment has inherit=true")
		void inheritReflectsSourceTrue() {
			// ---- Given: rolle-buket tildelt på OU MED nedarvning ---- //
			AttestationOuRoleAssignment inherited = AttestationOuRoleAssignment.builder()
				.roleGroupId(32L)
				.roleGroupName("Inheriting Group")
				.roleId(101L)
				.assignedThroughType(AssignedThroughType.ORGUNIT)
				.assignedThroughUuid(OU_UUID)
				.ouUuid(OU_UUID)
				.titleUuids(List.of())
				.exceptedUserUuids(List.of())
				.exceptedTitleUuids(List.of())
				.functionUuids(List.of())
				.inherit(true)
				.inherited(false)
				.build();

			// ---- When ---- //
			List<OrgUnitRoleGroupAssignmentDTO> dtos =
				organisationAttestationService.orgUnitRoleGroups(List.of(inherited));

			// ---- Then ---- //
			assertEquals(1, dtos.size());
			assertTrue(dtos.get(0).isInherit(), "DTO.inherit skal være true når source inherit=true");
		}
	}
}
