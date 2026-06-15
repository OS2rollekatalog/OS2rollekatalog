package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationDao;
import dk.digitalidentity.rc.attestation.dao.AttestationOuAssignmentsDao;
import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.dao.AttestationUserRoleAssignmentDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemOrganisationAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemOrganisationAttestationEntry;
import dk.digitalidentity.rc.attestation.model.entity.ItSystemUserAttestationEntry;
import dk.digitalidentity.rc.dao.FunctionDao;
import dk.digitalidentity.rc.dao.TitleDao;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.ItSystemService;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createItSystemAttestation;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createMixedRoleAssignments;
import static dk.digitalidentity.rc.mockfactory.attestation.MockFactory.createUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IT System Users Attestation Service Tests")
class ItSystemUsersAttestationServiceTest {

	private static final long IT_SYSTEM_ID = 1L;
	private static final String USER_UUID = "user-uuid";
	private static final String PERFORMER_USER_ID = "performer-user-id";
	private static final String PERFORMER_USER_UUID = "performer-user-uuid";
	private static final String OU_UUID = "ou-uuid";

	@Mock
	private AttestationUserRoleAssignmentDao attestationUserRoleAssignmentDao;

	@Mock
	private AttestationOuAssignmentsDao attestationOuAssignmentsDao;

	@Mock
	private ItSystemUserAttestationEntryDao itSystemUserAttestationEntryDao;

	@Mock
	private ItSystemOrganisationAttestationEntryDao itSystemOrganisationAttestationEntryDao;

	@Mock
	private AttestationDao attestationDao;

	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Mock
	private AttestationCachedUserService attestationUserService;

	@Mock
	private ItSystemService itSystemService;

	@Mock
	private UserService userService;

	@Mock
	private AttestationEmailNotificationService notificationService;

	@Mock
	private TitleDao titleDao;

	@Mock
	private FunctionDao functionDao;

	@InjectMocks
	private ItSystemUsersAttestationService itSystemUsersAttestationService;

	private User performingUser;
	private ItSystem itSystem;

	@BeforeEach
	void setUp() {
		performingUser = createUser(PERFORMER_USER_UUID, PERFORMER_USER_ID, "Performer User");
		itSystem = createItSystem(IT_SYSTEM_ID, "Test IT System");
		lenient().when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of(PERFORMER_USER_ID));
	}

	@Nested
	@DisplayName("verifyUser() Tests")
	class VerifyUserTests {

		@Test
		@DisplayName("Should verify user and save attestation entry")
		void verifyUser_ShouldSaveAttestationEntry() {
			// Arrange
			Attestation attestation = createItSystemAttestation(1L, "attestation-uuid", IT_SYSTEM_ID, "Test System", 1L);
			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of(PERFORMER_USER_UUID));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(IT_SYSTEM_ID)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(itSystemUserAttestationEntryDao.save(any(ItSystemUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());
			when(attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUsersAttestationService.verifyUser(IT_SYSTEM_ID, USER_UUID, PERFORMER_USER_ID);

			// Assert
			ArgumentCaptor<ItSystemUserAttestationEntry> captor = ArgumentCaptor.forClass(ItSystemUserAttestationEntry.class);
			verify(itSystemUserAttestationEntryDao).save(captor.capture());
			ItSystemUserAttestationEntry savedEntry = captor.getValue();
			assertEquals(USER_UUID, savedEntry.getUserUuid());
			assertEquals(PERFORMER_USER_ID, savedEntry.getPerformedByUserId());
			assertEquals(PERFORMER_USER_UUID, savedEntry.getPerformedByUserUuid());
			assertNotNull(savedEntry.getCreatedAt());
		}

		@Test
		@DisplayName("Should throw exception when IT system not found")
		void verifyUser_WhenItSystemNotFound_ShouldThrowException() {
			// Arrange
			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(null);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUsersAttestationService.verifyUser(IT_SYSTEM_ID, USER_UUID, PERFORMER_USER_ID));
		}

		@Test
		@DisplayName("Should throw exception when user is not the attestation responsible")
		void verifyUser_WhenNotAttestationResponsible_ShouldThrowException() {
			// Arrange
			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			when(itSystemService.getAttestationResponsibleUserIds(itSystem)).thenReturn(List.of("different-user-id"));

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUsersAttestationService.verifyUser(IT_SYSTEM_ID, USER_UUID, PERFORMER_USER_ID));
		}
	}

	@Nested
	@DisplayName("rejectUser() Tests")
	class RejectUserTests {

		@Test
		@DisplayName("Should reject user and save attestation entry with remarks")
		void rejectUser_ShouldSaveAttestationEntryWithRemarks() {
			// Arrange
			Attestation attestation = createItSystemAttestation(1L, "attestation-uuid", IT_SYSTEM_ID, "Test System", 1L);
			String remarks = "Test remarks";
			List<RoleAssignmentDTO> notApproved = createMixedRoleAssignments();
			User targetUser = createUser(USER_UUID, "target-user-id", "Target User");

			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of(PERFORMER_USER_UUID));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(IT_SYSTEM_ID)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(userService.getByUuid(USER_UUID)).thenReturn(targetUser);
			when(itSystemUserAttestationEntryDao.save(any(ItSystemUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());
			when(attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUsersAttestationService.rejectUser(IT_SYSTEM_ID, USER_UUID, remarks, notApproved, PERFORMER_USER_ID);

			// Assert
			ArgumentCaptor<ItSystemUserAttestationEntry> captor = ArgumentCaptor.forClass(ItSystemUserAttestationEntry.class);
			verify(itSystemUserAttestationEntryDao).save(captor.capture());
			ItSystemUserAttestationEntry savedEntry = captor.getValue();
			assertEquals(USER_UUID, savedEntry.getUserUuid());
			assertEquals(remarks, savedEntry.getRemarks());
			assertNotNull(savedEntry.getRejectedUserRoleIds());
			assertNotNull(savedEntry.getRejectedRoleGroupIds());
		}

		@Test
		@DisplayName("Should send notification email when user is rejected")
		void rejectUser_ShouldSendNotificationEmail() {
			// Arrange
			Attestation attestation = createItSystemAttestation(1L, "attestation-uuid", IT_SYSTEM_ID, "Test System", 1L);
			String remarks = "Test remarks";
			List<RoleAssignmentDTO> notApproved = createMixedRoleAssignments();
			User targetUser = createUser(USER_UUID, "target-user-id", "Target User");

			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of(PERFORMER_USER_UUID));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(IT_SYSTEM_ID)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(userService.getByUuid(USER_UUID)).thenReturn(targetUser);
			when(itSystemUserAttestationEntryDao.save(any(ItSystemUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());
			when(attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUsersAttestationService.rejectUser(IT_SYSTEM_ID, USER_UUID, remarks, notApproved, PERFORMER_USER_ID);

			// Assert
			verify(notificationService).sendRequestForChangeMail(
					"Performer User(performer-user-id)",
					"Target User(target-user-id)",
					remarks,
					notApproved);
		}

		@Test
		@DisplayName("Should not send notification when target user not found")
		void rejectUser_WhenTargetUserNotFound_ShouldNotSendNotification() {
			// Arrange
			Attestation attestation = createItSystemAttestation(1L, "attestation-uuid", IT_SYSTEM_ID, "Test System", 1L);
			String remarks = "Test remarks";

			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of(PERFORMER_USER_UUID));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(IT_SYSTEM_ID)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(userService.getByUuid(USER_UUID)).thenReturn(null);
			when(itSystemUserAttestationEntryDao.save(any(ItSystemUserAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());
			when(attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUsersAttestationService.rejectUser(IT_SYSTEM_ID, USER_UUID, remarks, Collections.emptyList(), PERFORMER_USER_ID);

			// Assert
			verify(notificationService, never()).sendRequestForChangeMail(any(), any(), any(), any());
		}
	}

	@Nested
	@DisplayName("verifyOu() Tests")
	class VerifyOuTests {

		@Test
		@DisplayName("Should verify OU and save attestation entry")
		void verifyOu_ShouldSaveAttestationEntry() {
			// Arrange
			Attestation attestation = createItSystemAttestation(1L, "attestation-uuid", IT_SYSTEM_ID, "Test System", 1L);
			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of(PERFORMER_USER_UUID));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(IT_SYSTEM_ID)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(itSystemOrganisationAttestationEntryDao.save(any(ItSystemOrganisationAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());
			when(attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUsersAttestationService.verifyOu(IT_SYSTEM_ID, OU_UUID, PERFORMER_USER_ID);

			// Assert
			ArgumentCaptor<ItSystemOrganisationAttestationEntry> captor = ArgumentCaptor.forClass(ItSystemOrganisationAttestationEntry.class);
			verify(itSystemOrganisationAttestationEntryDao).save(captor.capture());
			ItSystemOrganisationAttestationEntry savedEntry = captor.getValue();
			assertEquals(OU_UUID, savedEntry.getOrganisationUuid());
			assertEquals(PERFORMER_USER_ID, savedEntry.getPerformedByUserId());
			assertEquals(PERFORMER_USER_UUID, savedEntry.getPerformedByUserUuid());
			assertNotNull(savedEntry.getCreatedAt());
		}

		@Test
		@DisplayName("Should throw exception when IT system not found")
		void verifyOu_WhenItSystemNotFound_ShouldThrowException() {
			// Arrange
			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(null);

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUsersAttestationService.verifyOu(IT_SYSTEM_ID, OU_UUID, PERFORMER_USER_ID));
		}
	}

	@Nested
	@DisplayName("rejectOu() Tests")
	class RejectOuTests {

		@Test
		@DisplayName("Should reject OU and save attestation entry with remarks")
		void rejectOu_ShouldSaveAttestationEntryWithRemarks() {
			// Arrange
			Attestation attestation = createItSystemAttestation(1L, "attestation-uuid", IT_SYSTEM_ID, "Test System", 1L);
			String remarks = "Test remarks";

			when(itSystemService.getById(IT_SYSTEM_ID)).thenReturn(itSystem);
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(1L, IT_SYSTEM_ID, List.of(PERFORMER_USER_UUID));
			when(attestationResponsibleCollectionDao.findFirstByItSystemId(IT_SYSTEM_ID)).thenReturn(Optional.of(collection));
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdAndResponsibleCollectionIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID, 1L))
					.thenReturn(Optional.of(attestation));
			when(userService.getByUserId(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(userService.getByUuid(PERFORMER_USER_ID)).thenReturn(performingUser);
			when(itSystemOrganisationAttestationEntryDao.save(any(ItSystemOrganisationAttestationEntry.class)))
					.thenAnswer(invocation -> invocation.getArgument(0));
			when(attestationUserRoleAssignmentDao.listValidAssignmentsByResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());
			when(attestationOuAssignmentsDao.listValidNotInheritedAssignmentsWithResponsibleCollectionIdIn(any(), any()))
					.thenReturn(Collections.emptyList());

			// Act
			itSystemUsersAttestationService.rejectOu(IT_SYSTEM_ID, OU_UUID, remarks, PERFORMER_USER_ID);

			// Assert
			ArgumentCaptor<ItSystemOrganisationAttestationEntry> captor = ArgumentCaptor.forClass(ItSystemOrganisationAttestationEntry.class);
			verify(itSystemOrganisationAttestationEntryDao).save(captor.capture());
			ItSystemOrganisationAttestationEntry savedEntry = captor.getValue();
			assertEquals(OU_UUID, savedEntry.getOrganisationUuid());
			assertEquals(remarks, savedEntry.getRemarks());
		}
	}

	@Nested
	@DisplayName("getAttestation() Tests")
	class GetAttestationTests {

		@Test
		@DisplayName("Should throw exception when attestation not found")
		void getAttestation_WhenNotFound_ShouldThrowException() {
			// Arrange
			when(attestationDao.findFirstByAttestationTypeAndItSystemIdOrderByDeadlineDesc(
					Attestation.AttestationType.IT_SYSTEM_ATTESTATION, IT_SYSTEM_ID))
					.thenReturn(Optional.empty());

			// Act & Assert
			assertThrows(ResponseStatusException.class,
					() -> itSystemUsersAttestationService.getAttestation(IT_SYSTEM_ID, false, PERFORMER_USER_UUID));
		}
	}

	@Nested
	@DisplayName("lookupTitles() Tests")
	class LookupTitlesTests {

		@Test
		@DisplayName("Should return titles when found")
		void lookupTitles_WhenFound_ShouldReturnTitles() {
			// Arrange
			Set<String> titleUuids = new HashSet<>(Arrays.asList("title-1", "title-2"));
			Title title1 = new Title();
			title1.setUuid("title-1");
			title1.setName("Title One");
			title1.setActive(true);
			Title title2 = new Title();
			title2.setUuid("title-2");
			title2.setName("Title Two");
			title2.setActive(true);

			when(titleDao.findByUuidInAndActiveTrue(titleUuids))
					.thenReturn(Arrays.asList(title1, title2));

			// Act
			List<Title> result = titleDao.findByUuidInAndActiveTrue(titleUuids);

			// Assert
			assertEquals(2, result.size());
			assertTrue(result.stream().anyMatch(t -> t.getName().equals("Title One")));
			assertTrue(result.stream().anyMatch(t -> t.getName().equals("Title Two")));
		}
	}

	@Nested
	@DisplayName("lookupFunctions() Tests")
	class LookupFunctionsTests {

		@Test
		@DisplayName("Should return function names when found")
		void lookupFunctions_WhenFound_ShouldReturnNames() {
			// Arrange
			Set<String> functionUuids = Set.of("func-1", "func-2");
			Function function1 = new Function();
			function1.setUuid("func-1");
			function1.setName("Function One");
			function1.setActive(true);
			Function function2 = new Function();
			function2.setUuid("func-2");
			function2.setName("Function Two");
			function2.setActive(true);

			when(functionDao.findByUuidInAndActiveTrue(functionUuids))
					.thenReturn(Arrays.asList(function1, function2));

			// Act
			Collection<Function> result = functionDao.findByUuidInAndActiveTrue(functionUuids);

			// Assert
			assertEquals(2, result.size());
			assertTrue(result.stream().anyMatch(f -> f.getName().equals("Function One")));
			assertTrue(result.stream().anyMatch(f -> f.getName().equals("Function Two")));
		}
	}
}
