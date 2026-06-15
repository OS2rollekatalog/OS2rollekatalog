package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationSystemRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemRoleAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createAttestationRun;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystemRolesAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IT System User Roles Attestation Service Tests")
class ItSystemUserRolesAttestationServiceTest {

	@Mock
	private AttestationSystemRoleAssignmentDao attestationSystemRoleAssignmentDAO;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Mock
	private ItSystemRoleAttestationEntryDao itSystemRoleAttestationEntryDao;

	@Mock
	private UserService userService;

	@Mock
	private ItSystemService itSystemService;

	@Mock
	private AttestationEmailNotificationService notificationService;

	@Mock
	private UserRoleService userRoleService;

	@InjectMocks
	private ItSystemUserRolesAttestationService itSystemUserRolesAttestationService;

	@Nested
	@DisplayName("getItSystemAttestationsForUser() Tests")
	class GetItSystemAttestationsForUserTests {

		@Test
		@DisplayName("Should return empty list when no attestations match user")
		void getItSystemAttestationsForUser_WhenNoMatchingAttestations_ShouldReturnEmptyList() {
			// Arrange
			String userUuid = "user-uuid";
			AttestationRun run = createAttestationRun(1L, LocalDate.now().plusDays(14));

			// Act
			List<ItSystemAttestationDTO> result = itSystemUserRolesAttestationService.getItSystemAttestationsForUser(run, userUuid);

			// Assert
			assertNotNull(result);
			assertTrue(result.isEmpty());
		}

		@Test
		@DisplayName("Should filter out attestations that are already verified")
		void getItSystemAttestationsForUser_ShouldFilterOutVerifiedAttestations() {
			// Arrange
			String userUuid = "user-uuid";
			Long responsibleCollectionId = 1L;
			Attestation verifiedAttestation = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test System", responsibleCollectionId);
			verifiedAttestation.setVerifiedAt(ZonedDateTime.now());

			AttestationRun run = createAttestationRun(1L, LocalDate.now().plusDays(14));
			run.getAttestations().add(verifiedAttestation);

			// Act
			List<ItSystemAttestationDTO> result = itSystemUserRolesAttestationService.getItSystemAttestationsForUser(run, userUuid);

			// Assert
			assertTrue(result.isEmpty());
		}

		@Test
		@DisplayName("Should filter out attestations with different attestation type")
		void getItSystemAttestationsForUser_ShouldFilterOutWrongAttestationType() {
			// Arrange
			String userUuid = "user-uuid";
			Long responsibleCollectionId = 1L;
			Attestation wrongTypeAttestation = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test System", responsibleCollectionId);
			wrongTypeAttestation.setAttestationType(Attestation.AttestationType.ORGANISATION_ATTESTATION);

			AttestationRun run = createAttestationRun(1L, LocalDate.now().plusDays(14));
			run.getAttestations().add(wrongTypeAttestation);

			// Act
			List<ItSystemAttestationDTO> result = itSystemUserRolesAttestationService.getItSystemAttestationsForUser(run, userUuid);

			// Assert
			assertTrue(result.isEmpty());
		}

		@Test
		@DisplayName("Should filter out attestations for different user")
		void getItSystemAttestationsForUser_ShouldFilterOutDifferentUser() {
			// Arrange
			String userUuid = "user-uuid";
			Long differentResponsibleCollectionId = 99L;
			Attestation attestationForDifferentUser = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test System", differentResponsibleCollectionId);

			AttestationRun run = createAttestationRun(1L, LocalDate.now().plusDays(14));
			run.getAttestations().add(attestationForDifferentUser);

			// Act
			List<ItSystemAttestationDTO> result = itSystemUserRolesAttestationService.getItSystemAttestationsForUser(run, userUuid);

			// Assert
			assertTrue(result.isEmpty());
		}

		@Test
		@DisplayName("Should return matching attestations converted to DTOs")
		void getItSystemAttestationsForUser_ShouldReturnMatchingAttestations() {
			// Arrange
			String userUuid = "user-uuid";
			Long responsibleCollectionId = 1L;
			Attestation attestation = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test System", responsibleCollectionId);

			AttestationRun run = createAttestationRun(1L, LocalDate.now().plusDays(14));
			run.getAttestations().add(attestation);

			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(responsibleCollectionId, 100L, List.of(userUuid));
			when(attestationResponsibleCollectionDao.findById(responsibleCollectionId)).thenReturn(Optional.of(collection));
			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(responsibleCollectionId)))
					.thenReturn(Collections.emptyList());

			// Act
			List<ItSystemAttestationDTO> result = itSystemUserRolesAttestationService.getItSystemAttestationsForUser(run, userUuid);

			// Assert
			assertEquals(1, result.size());
			assertEquals(100L, result.get(0).getItSystemId());
		}
	}

	@Nested
	@DisplayName("getItSystemAttestation() Tests")
	class GetItSystemAttestationTests {

		@Test
		@DisplayName("Should return attestation DTO with system role assignments")
		void getItSystemAttestation_ShouldReturnAttestationDto() {
			// Arrange
			Long responsibleCollectionId = 1L;
			Attestation attestation = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test IT System", responsibleCollectionId);

			AttestationSystemRoleAssignment assignment = new AttestationSystemRoleAssignment();
			assignment.setUserRoleId(1L);
			assignment.setUserRoleName("Test Role");
			assignment.setItSystemId(100L);

			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(responsibleCollectionId)))
					.thenReturn(List.of(assignment));

			// Act
			ItSystemAttestationDTO result = itSystemUserRolesAttestationService.getItSystemAttestation(attestation);

			// Assert
			assertNotNull(result);
			assertEquals(100L, result.getItSystemId());
			assertEquals("Test IT System", result.getItSystemName());
		}
	}

	@Nested
	@DisplayName("verifyUserRole() Tests")
	class VerifyUserRoleTests {

		@Test
		@DisplayName("Should throw exception when IT system not found")
		void verifyUserRole_WhenItSystemNotFound_ShouldThrowException() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";

			when(itSystemService.getById(itSystemId)).thenReturn(null);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUserRolesAttestationService.verifyUserRole(itSystemId, userRoleId, performedByUserId));
		}

		@Test
		@DisplayName("Should throw exception when user is not attestation responsible")
		void verifyUserRole_WhenUserNotResponsible_ShouldThrowException() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";

			ItSystem itSystem = new ItSystem();
			itSystem.setId(itSystemId);

			when(itSystemService.getById(itSystemId)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of("different-id"));

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUserRolesAttestationService.verifyUserRole(itSystemId, userRoleId, performedByUserId));
		}

		@Test
		@DisplayName("Should throw exception when attestation not found")
		void verifyUserRole_WhenAttestationNotFound_ShouldThrowException() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";

			User responsibleUser = createUser("responsible-uuid", performedByUserId, "Responsible User");
			ItSystem itSystem = new ItSystem();
			itSystem.setId(itSystemId);

			when(itSystemService.getById(itSystemId)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of(performedByUserId));
			when(userService.getByUserId(performedByUserId)).thenReturn(responsibleUser);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, itSystemId, List.of("responsible-uuid"));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId, 1L))
					.thenReturn(Optional.empty());

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUserRolesAttestationService.verifyUserRole(itSystemId, userRoleId, performedByUserId));
		}

		@Test
		@DisplayName("Should save attestation entry when verification successful")
		void verifyUserRole_WhenSuccessful_ShouldSaveAttestationEntry() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";
			String performedByUserUuid = "performer-uuid";

			User responsibleUser = createUser(performedByUserUuid, performedByUserId, "Responsible User");
			ItSystem itSystem = new ItSystem();
			itSystem.setId(itSystemId);

			Attestation attestation = createItSystemRolesAttestation(1L, "att-uuid", itSystemId, "Test System", 1L);
			attestation.setItSystemUserRoleAttestationEntries(new HashSet<>());

			when(itSystemService.getById(itSystemId)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of(performedByUserId));
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, itSystemId, List.of(performedByUserUuid));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(performedByUserId)).thenReturn(responsibleUser);
			when(itSystemRoleAttestationEntryDao.save(any(ItSystemRoleAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(1L)))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUserRolesAttestationService.verifyUserRole(itSystemId, userRoleId, performedByUserId);

			// Assert
			ArgumentCaptor<ItSystemRoleAttestationEntry> entryCaptor = ArgumentCaptor.forClass(ItSystemRoleAttestationEntry.class);
			verify(itSystemRoleAttestationEntryDao).save(entryCaptor.capture());

			ItSystemRoleAttestationEntry savedEntry = entryCaptor.getValue();
			assertEquals(userRoleId, savedEntry.getUserRoleId());
			assertEquals(performedByUserId, savedEntry.getPerformedByUserId());
			assertEquals(performedByUserUuid, savedEntry.getPerformedByUserUuid());
			assertNull(savedEntry.getRemarks());
		}
	}

	@Nested
	@DisplayName("rejectUserRole() Tests")
	class RejectUserRoleTests {

		@Test
		@DisplayName("Should throw exception when IT system not found")
		void rejectUserRole_WhenItSystemNotFound_ShouldThrowException() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";
			String remarks = "Some remarks";

			when(itSystemService.getById(itSystemId)).thenReturn(null);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUserRolesAttestationService.rejectUserRole(itSystemId, userRoleId, performedByUserId, remarks));
		}

		@Test
		@DisplayName("Should save attestation entry with remarks when rejection successful")
		void rejectUserRole_WhenSuccessful_ShouldSaveAttestationEntryWithRemarks() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";
			String performedByUserUuid = "performer-uuid";
			String remarks = "This role needs changes";

			User responsibleUser = createUser(performedByUserUuid, performedByUserId, "Responsible User");
			ItSystem itSystem = new ItSystem();
			itSystem.setId(itSystemId);
			itSystem.setName("Test IT System");

			Attestation attestation = createItSystemRolesAttestation(1L, "att-uuid", itSystemId, "Test System", 1L);
			attestation.setItSystemUserRoleAttestationEntries(new HashSet<>());

			UserRole userRole = new UserRole();
			userRole.setId(userRoleId);
			userRole.setName("Test Role");

			when(itSystemService.getById(itSystemId)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of(performedByUserId));
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, itSystemId, List.of(performedByUserUuid));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(performedByUserId)).thenReturn(responsibleUser);
			when(itSystemRoleAttestationEntryDao.save(any(ItSystemRoleAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(1L)))
					.thenReturn(Collections.emptyList());
			when(userRoleService.getByItSystem(itSystem)).thenReturn(List.of(userRole));

			// Act
			itSystemUserRolesAttestationService.rejectUserRole(itSystemId, userRoleId, performedByUserId, remarks);

			// Assert
			ArgumentCaptor<ItSystemRoleAttestationEntry> entryCaptor = ArgumentCaptor.forClass(ItSystemRoleAttestationEntry.class);
			verify(itSystemRoleAttestationEntryDao).save(entryCaptor.capture());

			ItSystemRoleAttestationEntry savedEntry = entryCaptor.getValue();
			assertEquals(userRoleId, savedEntry.getUserRoleId());
			assertEquals(performedByUserId, savedEntry.getPerformedByUserId());
			assertEquals(remarks, savedEntry.getRemarks());
		}

		@Test
		@DisplayName("Should send notification when role is rejected")
		void rejectUserRole_WhenSuccessful_ShouldSendNotification() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";
			String performedByUserUuid = "performer-uuid";
			String remarks = "This role needs changes";

			User responsibleUser = createUser(performedByUserUuid, performedByUserId, "Responsible User");
			ItSystem itSystem = new ItSystem();
			itSystem.setId(itSystemId);
			itSystem.setName("Test IT System");

			Attestation attestation = createItSystemRolesAttestation(1L, "att-uuid", itSystemId, "Test System", 1L);
			attestation.setItSystemUserRoleAttestationEntries(new HashSet<>());

			UserRole userRole = new UserRole();
			userRole.setId(userRoleId);
			userRole.setName("Test Role");

			when(itSystemService.getById(itSystemId)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of(performedByUserId));
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, itSystemId, List.of(performedByUserUuid));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(performedByUserId)).thenReturn(responsibleUser);
			when(itSystemRoleAttestationEntryDao.save(any(ItSystemRoleAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(1L)))
					.thenReturn(Collections.emptyList());
			when(userRoleService.getByItSystem(itSystem)).thenReturn(List.of(userRole));

			// Act
			itSystemUserRolesAttestationService.rejectUserRole(itSystemId, userRoleId, performedByUserId, remarks);

			// Assert
			verify(notificationService).sendRequestForRoleChange(
					eq("Responsible User(performer-id)"),
					eq("Test Role"),
					eq("Test IT System"),
					eq(remarks));
		}

		@Test
		@DisplayName("Should not send notification when user role not found")
		void rejectUserRole_WhenUserRoleNotFound_ShouldNotSendNotification() {
			// Arrange
			long itSystemId = 100L;
			long userRoleId = 1L;
			String performedByUserId = "performer-id";
			String performedByUserUuid = "performer-uuid";
			String remarks = "This role needs changes";

			User responsibleUser = createUser(performedByUserUuid, performedByUserId, "Responsible User");
			ItSystem itSystem = new ItSystem();
			itSystem.setId(itSystemId);
			itSystem.setName("Test IT System");

			Attestation attestation = createItSystemRolesAttestation(1L, "att-uuid", itSystemId, "Test System", 1L);
			attestation.setItSystemUserRoleAttestationEntries(new HashSet<>());

			when(itSystemService.getById(itSystemId)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of(performedByUserId));
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, itSystemId, List.of(performedByUserUuid));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystemId)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION, itSystemId, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(performedByUserId)).thenReturn(responsibleUser);
			when(itSystemRoleAttestationEntryDao.save(any(ItSystemRoleAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(1L)))
					.thenReturn(Collections.emptyList());
			when(userRoleService.getByItSystem(itSystem)).thenReturn(Collections.emptyList());

			// Act
			itSystemUserRolesAttestationService.rejectUserRole(itSystemId, userRoleId, performedByUserId, remarks);

			// Assert
			verify(notificationService, never()).sendRequestForRoleChange(any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("finishOutstandingAttestations() Tests")
	class FinishOutstandingAttestationsTests {

		@Test
		@DisplayName("Should not verify attestations that are already verified")
		void finishOutstandingAttestations_WhenAlreadyVerified_ShouldNotChange() {
			// Arrange
			Attestation verifiedAttestation = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test System", 1L);
			verifiedAttestation.setVerifiedAt(ZonedDateTime.now());

			when(attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(
					eq(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION), any(LocalDate.class)))
					.thenReturn(List.of(verifiedAttestation));

			// Act
			itSystemUserRolesAttestationService.finishOutstandingAttestations();

			// Assert
			assertNotNull(verifiedAttestation.getVerifiedAt());
		}

		@Test
		@DisplayName("Should verify attestations that are done but not yet verified")
		void finishOutstandingAttestations_WhenDoneButNotVerified_ShouldVerify() {
			// Arrange
			Long responsibleCollectionId = 1L;
			Attestation unverifiedAttestation = createItSystemRolesAttestation(1L, "att-uuid", 100L, "Test System", responsibleCollectionId);
			unverifiedAttestation.setVerifiedAt(null);

			when(attestationDao.findByAttestationTypeAndDeadlineIsGreaterThanEqual(
					eq(Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION), any(LocalDate.class)))
					.thenReturn(List.of(unverifiedAttestation));

			when(attestationSystemRoleAssignmentDAO.listValidAttestationsByResponsibleCollection(any(LocalDate.class), eq(responsibleCollectionId)))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUserRolesAttestationService.finishOutstandingAttestations();

			// Assert
			assertNotNull(unverifiedAttestation.getVerifiedAt());
		}
	}
}
