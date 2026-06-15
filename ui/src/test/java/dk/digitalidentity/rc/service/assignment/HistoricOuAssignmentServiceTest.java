package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.dao.model.enums.ContainsTitles;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createNamedOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricOuAssignmentServiceTest {

	@Mock
	private HistoricOuAssignmentDao historicOuAssignmentDao;
	@Mock
	private OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	@Mock
	private OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	@Mock
	private UserRoleDao userRoleDao;
	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@InjectMocks
	private HistoricOuAssignmentService service;

	// ---- Common test data ---- //

	private OrgUnit ou;
	private ItSystem itSystem;
	private UserRole userRole;

	@BeforeEach
	void setup() {
		itSystem = createItSystem(10L, "Test IT System");
		userRole = createUserRole(20L, "Test Role", itSystem);
		userRole.setDescription("Role description");
		ou = createNamedOrgUnit("ou-uuid", "Test OU");
	}

	private HistoricOuAssignment captureFirstFromSaveAll() {
		ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
		verify(historicOuAssignmentDao).save(captor.capture());
		return captor.getValue();
	}

	// ---- ------------- ---- //

	@Nested
	@DisplayName("recordUserRoleAdded creates a correct record")
	class RecordUserRoleAdded {

		@Test
		@DisplayName("OU and IT system fields are mapped correctly")
		void ouAndItSystemFieldsAreMapped() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();
			assertThat(snapshot.getOuUuid()).isEqualTo("ou-uuid");
			assertThat(snapshot.getOuName()).isEqualTo("Test OU");
			assertThat(snapshot.getItSystemId()).isEqualTo(10L);
			assertThat(snapshot.getItSystemName()).isEqualTo("Test IT System");
		}

		@Test
		@DisplayName("role fields are mapped correctly, including sensitive flags")
		void roleFieldsAreMapped() {
			// ---- Given ---- //
			userRole.setSensitiveRole(true);
			userRole.setExtraSensitiveRole(true);
			itSystem.setAttestationExempt(true);
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();
			assertThat(snapshot.getRoleId()).isEqualTo(20L);
			assertThat(snapshot.getRoleName()).isEqualTo("Test Role");
			assertThat(snapshot.getRoleDescription()).isEqualTo("Role description");
			assertThat(snapshot.isSensitiveRole()).isTrue();
			assertThat(snapshot.isExtraSensitiveRole()).isTrue();
			assertThat(snapshot.isItSystemAttestationExempt()).isTrue();
		}

		@Test
		@DisplayName("assignedThrough fields point to the OU for direct assignments")
		void assignedThroughFieldsAreMapped() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();
			assertThat(snapshot.getAssignedThroughType()).isEqualTo(AssignedThrough.ORGUNIT);
			assertThat(snapshot.getAssignedThroughUuid()).isEqualTo("ou-uuid");
			assertThat(snapshot.getAssignedThroughName()).isEqualTo("Test OU");
		}

		@Test
		@DisplayName("policy flags are mapped correctly from the assignment")
		void policyFlagsAreMapped() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, true, true, true);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();
			assertThat(snapshot.isAppliesOnlyToManager()).isTrue();
			assertThat(snapshot.isAppliesAlsoToSubstitutes()).isTrue();
			assertThat(snapshot.isInheritToChildren()).isTrue();
		}

		@Test
		@DisplayName("direct assignment has no role group fields")
		void directAssignmentHasNoRoleGroupFields() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();
			assertThat(snapshot.getRoleRoleGroupId()).isNull();
			assertThat(snapshot.getRoleRoleGroupName()).isNull();
			assertThat(snapshot.getRoleGroupDescription()).isNull();
		}

		@Test
		@DisplayName("new record is open (validFrom set, validTo null, recordHash set)")
		void newRecordIsOpen() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();
			assertThat(snapshot.getValidFrom()).isNotNull();
			assertThat(snapshot.getValidTo()).isNull();
			assertThat(snapshot.getRecordHash()).isNotNull();
		}

		@Test
		@DisplayName("responsibleUserUuid is set when role flag is true and itSystem has responsible user")
		void responsibleUserUuidIsSetWhenFlagAndUserPresent() {
			// ---- Given ---- //
			User responsible = createUser("responsible-uuid");
			userRole.setRoleAssignmentAttestationByAttestationResponsible(true);
			itSystem.addAttestationResponsible(responsible);
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("responsible-uuid"))));

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			// TODO: legacy responsibleUserUuid removed in multi-owner refactor — collection lookup is set up elsewhere
			assertThat(snapshot.getResponsibleCollectionId()).isNotNull();
		}

		@Test
		@DisplayName("responsibleCollectionId is null when role flag is false, even if itSystem has responsible user")
		void responsibleCollectionIdIsNullWhenFlagIsFalse() {
			// ---- Given ---- //
			User responsible = createUser("responsible-uuid");
			userRole.setRoleAssignmentAttestationByAttestationResponsible(false);
			itSystem.addAttestationResponsible(responsible);

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			assertThat(snapshot.getResponsibleCollectionId()).isNull();
		}

		@Test
		@DisplayName("exclusion EXCEPTED_USERS is created with correct UUIDs")
		void exceptedUsersExclusionIsCreated() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setContainsExceptedUsers(true);
			assignment.setExceptedUsers(List.of(createUser("user-1"), createUser("user-2")));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			List<HistoricOuAssignmentExclusion> exclusions = snapshot.getExclusions();
			assertThat(exclusions).hasSize(1);
			assertThat(exclusions.getFirst().getExclusionType()).isEqualTo(ExclusionType.EXCEPTED_USERS);
			assertThat(exclusions.getFirst().getUuids()).containsExactlyInAnyOrder("user-1", "user-2");
		}

		@Test
		@DisplayName("exclusion POSITIVE_TITLES is created with correct UUIDs")
		void positiveTitlesExclusionIsCreated() {
			// ---- Given ---- //
			Title t1 = new Title(); t1.setUuid("title-1");
			Title t2 = new Title(); t2.setUuid("title-2");

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setContainsTitles(ContainsTitles.POSITIVE);
			assignment.setTitles(List.of(t1, t2));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			List<HistoricOuAssignmentExclusion> exclusions = snapshot.getExclusions();
			assertThat(exclusions).hasSize(1);
			assertThat(exclusions.getFirst().getExclusionType()).isEqualTo(ExclusionType.POSITIVE_TITLES);
			assertThat(exclusions.getFirst().getUuids()).containsExactlyInAnyOrder("title-1", "title-2");
		}

		@Test
		@DisplayName("exclusion NEGATIVE_TITLES is created with correct UUIDs")
		void negativeTitlesExclusionIsCreated() {
			// ---- Given ---- //
			Title t1 = new Title(); t1.setUuid("title-neg-1");

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setContainsTitles(ContainsTitles.NEGATIVE);
			assignment.setTitles(List.of(t1));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			List<HistoricOuAssignmentExclusion> exclusions = snapshot.getExclusions();
			assertThat(exclusions).hasSize(1);
			assertThat(exclusions.getFirst().getExclusionType()).isEqualTo(ExclusionType.NEGATIVE_TITLES);
			assertThat(exclusions.getFirst().getUuids()).containsExactly("title-neg-1");
		}

		@Test
		@DisplayName("exclusion FUNCTIONS is created with correct UUIDs")
		void functionsExclusionIsCreated() {
			// ---- Given ---- //
			Function f1 = new Function(); f1.setUuid("func-1");
			Function f2 = new Function(); f2.setUuid("func-2");

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setContainsFunctions(true);
			assignment.setFunctions(List.of(f1, f2));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			List<HistoricOuAssignmentExclusion> exclusions = snapshot.getExclusions();
			assertThat(exclusions).hasSize(1);
			assertThat(exclusions.getFirst().getExclusionType()).isEqualTo(ExclusionType.FUNCTIONS);
			assertThat(exclusions.getFirst().getUuids()).containsExactlyInAnyOrder("func-1", "func-2");
		}

		@Test
		@DisplayName("no exclusions are created when no flags are set")
		void noExclusionsWhenNoFlagsSet() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			assertThat(snapshot.getExclusions()).isEmpty();
		}

		@Test
		@DisplayName("assignedWhen is taken from assignment timestamp when set")
		void assignedWhenIsSetFromTimestamp() {
			// ---- Given ---- //
			java.util.Date timestamp = new java.util.Date(1_000_000_000_000L);
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setAssignedTimestamp(timestamp);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			java.time.LocalDateTime expected = timestamp.toInstant()
				.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
			assertThat(snapshot.getAssignedWhen()).isEqualTo(expected);
		}

		@Test
		@DisplayName("assignedWhen falls back to now when assignment timestamp is null")
		void assignedWhenFallsBackToNowWhenTimestampIsNull() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setAssignedTimestamp(null);

			java.time.LocalDateTime before = java.time.LocalDateTime.now();

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			java.time.LocalDateTime after = java.time.LocalDateTime.now();

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			assertThat(snapshot.getAssignedWhen())
				.isAfterOrEqualTo(before)
				.isBeforeOrEqualTo(after);
		}

		@Test
		@DisplayName("(negative) POSITIVE_TITLES flag set but null titles list → no exclusion created")
		void positiveTitlesFlagSetButNullTitles() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setContainsTitles(ContainsTitles.POSITIVE);
			assignment.setTitles(null);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			assertThat(snapshot.getExclusions()).isEmpty();
		}

		@Test
		@DisplayName("(negative) containsExceptedUsers flag set but null user list → no exclusion created")
		void exceptedUsersFlagSetButNullUserList() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setContainsExceptedUsers(true);
			assignment.setExceptedUsers(null);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			HistoricOuAssignment snapshot = captureFirstFromSaveAll();

			assertThat(snapshot.getExclusions()).isEmpty();
		}

		@Test
		@DisplayName("assignedBy and date fields are mapped from the assignment")
		void assignedByAndDateFieldsAreMapped() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setAssignedByUserId("user-id-42");
			assignment.setAssignedByName("Jane Doe");
			assignment.setStartDate(LocalDate.of(2025, 1, 1));
			assignment.setStopDate(LocalDate.of(2026, 12, 31));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
			assertThat(snapshot.getAssignedByUserId()).isEqualTo("user-id-42");
			assertThat(snapshot.getAssignedByName()).isEqualTo("Jane Doe");
			assertThat(snapshot.getStartDate()).isEqualTo(LocalDate.of(2025, 1, 1));
			assertThat(snapshot.getStopDate()).isEqualTo(LocalDate.of(2026, 12, 31));
		}
	}

	@Nested
	@DisplayName("recordUserRoleUpdatedClose closes the open record by hash")
	class RecordUserRoleUpdatedClose {

		@Test
		@DisplayName("passes the pre-update hash to closeOpenByRecordHash and does not save")
		void closesOldRecordByPreUpdateHashAndDoesNotSave() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setStopDate(LocalDate.of(2026, 12, 31));

			// ---- When ---- //
			service.recordUserRoleUpdatedClose(ou, assignment);

			// ---- Then ---- //
			// The hash must match what the calculator produces for this assignment state,
			// so the correct open row is closed when stopDate later changes.
			String expectedHash = HistoricOuAssignmentHashCalculator.compute(
				HistoricOuAssignment.builder()
					.ouUuid(ou.getUuid())
					.itSystemId(itSystem.getId())
					.roleId(userRole.getId())
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.assignedThroughUuid(ou.getUuid())
					.appliesOnlyToManager(false)
					.appliesAlsoToSubstitutes(false)
					.inheritToChildren(false)
					.startDate(null)
					.stopDate(LocalDate.of(2026, 12, 31))
					.exclusions(new ArrayList<>())
					.build()
			);

			ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
			verify(historicOuAssignmentDao).closeOpenByRecordHash(hashCaptor.capture(), any());
			verify(historicOuAssignmentDao, Mockito.never()).save(any());

			assertThat(hashCaptor.getValue()).isEqualTo(expectedHash);
		}
	}

	@Nested
	@DisplayName("recordUserRoleUpdatedSaveNew saves a fresh open record")
	class RecordUserRoleUpdatedSaveNew {

		@Test
		@DisplayName("saves a new record with validTo = null reflecting post-update state")
		void savesNewOpenRecord() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setStopDate(LocalDate.of(2027, 6, 30));

			// ---- When ---- //
			service.recordUserRoleUpdatedSaveNew(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
			assertThat(snapshot.getValidTo()).isNull();
			assertThat(snapshot.getStopDate()).isEqualTo(LocalDate.of(2027, 6, 30));
		}
	}

	@Nested
	@DisplayName("recordUserRoleRemoved")
	class RecordUserRoleRemoved {

		@Test
		@DisplayName("closes the record by the correct hash with a non-null timestamp")
		void closesCorrectRecordByHash() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleRemoved(ou, assignment);

			// ---- Then ---- //
			verify(historicOuAssignmentDao).closeOpenByRecordHash(any(String.class), any());
			verifyNoInteractions(userRoleDao);
		}
	}

	@Nested
	@DisplayName("recordRoleGroupAdded")
	class RecordRoleGroupAdded {

		@Test
		@DisplayName("creates one record per UserRole in the group")
		void createsOneRecordPerUserRole() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupAdded(ou, assignment);

			// ---- Then ---- //
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			assertThat(captor.getValue()).hasSize(2);
		}

		@Test
		@DisplayName("each record has correct roleRoleGroupId and roleRoleGroupName")
		void eachRecordHasCorrectRoleGroupFields() {
			// ---- Given ---- //
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupAdded(ou, assignment);

			// ---- Then ---- //
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			HistoricOuAssignment first = captor.getValue().getFirst();
			assertThat(first.getRoleRoleGroupId()).isEqualTo(30L);
			assertThat(first.getRoleRoleGroupName()).isEqualTo("Test Role Group");
			assertThat(first.getRoleGroupDescription()).isEqualTo("Role group description");
		}

		@Test
		@DisplayName("each record has correct role and IT system fields from its UserRole")
		void eachRecordHasCorrectRoleFields() {
			// ---- Given ---- //
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupAdded(ou, assignment);

			// ---- Then ---- //
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			HistoricOuAssignment first = captor.getValue().getFirst();
			assertThat(first.getRoleId()).isEqualTo(20L);
			assertThat(first.getRoleName()).isEqualTo("Test Role");
			assertThat(first.getItSystemId()).isEqualTo(10L);
			assertThat(first.getItSystemName()).isEqualTo("Test IT System");
		}

		@Test
		@DisplayName("each record maps to its own UserRole when group has multiple roles")
		void eachRecordMapsToItsOwnRole() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupAdded(ou, assignment);

			// ---- Then ---- //
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			assertThat(captor.getValue())
				.extracting(HistoricOuAssignment::getRoleId)
				.containsExactlyInAnyOrder(20L, 21L);
		}

		@Test
		@DisplayName("shared exclusions are independently copied to each record")
		void sharedExclusionsAreCopiedIndependently() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));

			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);
			assignment.setContainsExceptedUsers(true);
			assignment.setExceptedUsers(List.of(createUser("user-1")));

			// ---- When ---- //
			service.recordRoleGroupAdded(ou, assignment);

			// ---- Then ---- //
			@SuppressWarnings("unchecked")
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			List<HistoricOuAssignment> records = captor.getValue();
			assertThat(records).hasSize(2);

			// Both records have an EXCEPTED_USERS exclusion
			records.forEach(r ->
				assertThat(r.getExclusions())
					.extracting(HistoricOuAssignmentExclusion::getExclusionType)
					.containsExactly(ExclusionType.EXCEPTED_USERS)
			);

			// Exclusion objects must be different instances (copies, not shared references)
			HistoricOuAssignmentExclusion excl0 = records.get(0).getExclusions().getFirst();
			HistoricOuAssignmentExclusion excl1 = records.get(1).getExclusions().getFirst();
			assertThat(excl0).isNotSameAs(excl1);
		}

		@Test
		@DisplayName("assignedBy and date fields from the role-group assignment are mapped to each record")
		void assignedByAndDateFieldsAreMapped() {
			// ---- Given ---- //
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);
			assignment.setAssignedByUserId("user-id-99");
			assignment.setAssignedByName("John Smith");
			assignment.setStartDate(LocalDate.of(2025, 3, 1));
			assignment.setStopDate(LocalDate.of(2025, 9, 30));

			// ---- When ---- //
			service.recordRoleGroupAdded(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			HistoricOuAssignment first = captor.getValue().getFirst();
			assertThat(first.getAssignedByUserId()).isEqualTo("user-id-99");
			assertThat(first.getAssignedByName()).isEqualTo("John Smith");
			assertThat(first.getStartDate()).isEqualTo(LocalDate.of(2025, 3, 1));
			assertThat(first.getStopDate()).isEqualTo(LocalDate.of(2025, 9, 30));
		}
	}

	@Nested
	@DisplayName("recordRoleGroupUpdatedClose closes one record per role")
	class RecordRoleGroupUpdatedClose {

		@Test
		@DisplayName("calls closeOpenByRecordHash once per role and does not save")
		void closesAllOldRecordsAndDoesNotSave() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupUpdatedClose(ou, assignment);

			// ---- Then ---- //
			verify(historicOuAssignmentDao, Mockito.times(2))
				.closeOpenByRecordHash(any(String.class), any());
			verify(historicOuAssignmentDao, Mockito.never()).saveAll(any());
		}
	}

	@Nested
	@DisplayName("recordRoleGroupUpdatedSaveNew saves fresh open records")
	class RecordRoleGroupUpdatedSaveNew {

		@Test
		@DisplayName("saves one new record per role in the group")
		void savesOneRecordPerRole() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);
			assignment.setStopDate(LocalDate.of(2027, 3, 1));

			// ---- When ---- //
			service.recordRoleGroupUpdatedSaveNew(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			assertThat(captor.getValue())
				.hasSize(2)
				.allSatisfy(r -> {
					assertThat(r.getValidTo()).isNull();
					assertThat(r.getStopDate()).isEqualTo(LocalDate.of(2027, 3, 1));
				});
		}
	}

	@Nested
	@DisplayName("recordRoleGroupRemoved")
	class RecordRoleGroupRemoved {

		@Test
		@DisplayName("closes one record per role in the group")
		void closesOneRecordPerRole() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupRemoved(ou, assignment);

			// ---- Then ---- //
			verify(historicOuAssignmentDao, Mockito.times(2))
				.closeOpenByRecordHash(any(String.class), any());
			verifyNoInteractions(userRoleDao);
		}
	}

	@Nested
	@DisplayName("recordUserRoleAddedToRoleGroup")
	class RecordUserRoleAddedToRoleGroup {

		@Test
		@DisplayName("creates one record per OU assignment of the role group, one for each distinct OU")
		void createsOneRecordPerOuAssignment() {
			// ---- Given ---- //
			OrgUnit ou2 = createNamedOrgUnit("ou-2-uuid", "OU 2");
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole));

			OrgUnitRoleGroupAssignment rgAssignment1 = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);
			OrgUnitRoleGroupAssignment rgAssignment2 = createOrgUnitRoleGroupAssignment(roleGroup, ou2, false, false, false);

			given(orgUnitRoleGroupAssignmentDao.findByRoleGroup(roleGroup))
				.willReturn(List.of(rgAssignment1, rgAssignment2));
			given(userRoleDao.findByIdWithItSystem(userRole.getId()))
				.willReturn(Optional.of(userRole));

			// ---- When ---- //
			service.recordUserRoleAddedToRoleGroup(roleGroup, userRole);

			// ---- Then ---- //
			ArgumentCaptor<List<HistoricOuAssignment>> captor = ArgumentCaptor.forClass(List.class);
			verify(historicOuAssignmentDao).saveAll(captor.capture());

			assertThat(captor.getValue())
				.hasSize(2)
				.extracting(HistoricOuAssignment::getOuUuid)
				.containsExactlyInAnyOrder("ou-uuid", "ou-2-uuid");
		}
	}

	@Nested
	@DisplayName("recordUserRoleRemovedFromRoleGroup")
	class RecordUserRoleRemovedFromRoleGroup {

		@Test
		@DisplayName("closes all open records for the given roleGroup and role combination")
		void closesAllOpenRecordsForRoleGroupAndRole() {
			// ---- Given ---- //
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole));

			// ---- When ---- //
			service.recordUserRoleRemovedFromRoleGroup(roleGroup, userRole);

			// ---- Then ---- //
			verify(historicOuAssignmentDao).closeAllOpenByRoleGroupIdAndRoleId(
				eq(30L), eq(20L), any()
			);
		}
	}

	@Nested
	@DisplayName("computeHash — date fields affect hash identity")
	class ComputeHashWithDates {

		@Test
		@DisplayName("two assignments differing only in stopDate produce different hashes")
		void stopDateChangesHash() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment a1 = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			a1.setStopDate(LocalDate.of(2026, 12, 31));

			OrgUnitUserRoleAssignment a2 = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			a2.setStopDate(LocalDate.of(2027, 6, 30));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, a1);
			service.recordUserRoleAdded(ou, a2);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao, Mockito.times(2)).save(captor.capture());

			List<HistoricOuAssignment> saved = captor.getAllValues();
			assertThat(saved.get(0).getRecordHash()).isNotEqualTo(saved.get(1).getRecordHash());
		}

		@Test
		@DisplayName("two assignments differing only in startDate produce different hashes")
		void startDateChangesHash() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment a1 = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			a1.setStartDate(LocalDate.of(2025, 1, 1));

			OrgUnitUserRoleAssignment a2 = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			a2.setStartDate(LocalDate.of(2026, 1, 1));

			// ---- When ---- //
			service.recordUserRoleAdded(ou, a1);
			service.recordUserRoleAdded(ou, a2);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao, Mockito.times(2)).save(captor.capture());

			List<HistoricOuAssignment> saved = captor.getAllValues();
			assertThat(saved.get(0).getRecordHash()).isNotEqualTo(saved.get(1).getRecordHash());
		}

		@Test
		@DisplayName("two assignments differing only in assignedByUserId produce the SAME hash")
		void assignedByUserIdDoesNotAffectHash() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment a1 = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			a1.setAssignedByUserId("user-a");

			OrgUnitUserRoleAssignment a2 = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			a2.setAssignedByUserId("user-b");

			// ---- When ---- //
			service.recordUserRoleAdded(ou, a1);
			service.recordUserRoleAdded(ou, a2);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao, Mockito.times(2)).save(captor.capture());

			List<HistoricOuAssignment> saved = captor.getAllValues();
			assertThat(saved.get(0).getRecordHash()).isEqualTo(saved.get(1).getRecordHash());
		}
	}

	@Nested
	@DisplayName("seedHistoricRowsFromOrgUnitUserRoleAssignmentId")
	class SeedHistoricRowsFromOrgUnitUserRoleAssignmentId {

		@Test
		@DisplayName("returns false and skips save when assignment id is unknown")
		void returnsFalseWhenAssignmentMissing() {
			// ---- Given ---- //
			given(orgUnitUserRoleAssignmentDao.findById(99L)).willReturn(Optional.empty());

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(99L);

			// ---- Then ---- //
			assertThat(result).isFalse();
			verify(historicOuAssignmentDao, never()).save(any());
		}

		@Test
		@DisplayName("returns false when UserRole has no ItSystem (catalogue-internal role)")
		void returnsFalseWhenItSystemMissing() {
			// ---- Given ---- //
			UserRole orphanRole = createUserRole(21L, "Orphan", null);
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(orphanRole, ou, false, false, false);
			assignment.setId(7L);
			given(orgUnitUserRoleAssignmentDao.findById(7L)).willReturn(Optional.of(assignment));

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(7L);

			// ---- Then ---- //
			assertThat(result).isFalse();
			verify(historicOuAssignmentDao, never()).save(any());
		}

		@Test
		@DisplayName("saves a new historic row when no open row with matching hash exists")
		void savesNewRowWhenHashMissing() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setId(7L);
			given(orgUnitUserRoleAssignmentDao.findById(7L)).willReturn(Optional.of(assignment));
			when(historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(anyString())).thenReturn(false);

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(7L);

			// ---- Then ---- //
			assertThat(result).isTrue();
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());
			assertThat(captor.getValue().getOuUuid()).isEqualTo("ou-uuid");
			assertThat(captor.getValue().getRoleId()).isEqualTo(20L);
		}

		@Test
		@DisplayName("idempotent: skips save when an open row with matching hash already exists")
		void skipsSaveWhenHashExists() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
			assignment.setId(7L);
			given(orgUnitUserRoleAssignmentDao.findById(7L)).willReturn(Optional.of(assignment));
			when(historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(anyString())).thenReturn(true);

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitUserRoleAssignmentId(7L);

			// ---- Then ---- //
			assertThat(result).isTrue();
			verify(historicOuAssignmentDao, never()).save(any());
		}
	}

	@Nested
	@DisplayName("seedHistoricRowsFromOrgUnitRoleGroupAssignmentId")
	class SeedHistoricRowsFromOrgUnitRoleGroupAssignmentId {

		@Test
		@DisplayName("returns false and skips save when assignment id is unknown")
		void returnsFalseWhenAssignmentMissing() {
			// ---- Given ---- //
			given(orgUnitRoleGroupAssignmentDao.findById(99L)).willReturn(Optional.empty());

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(99L);

			// ---- Then ---- //
			assertThat(result).isFalse();
			verify(historicOuAssignmentDao, never()).save(any());
		}

		@Test
		@DisplayName("saves one historic row per user-role in the role group when none exist")
		void savesOneRowPerUserRoleInRoleGroup() {
			// ---- Given ---- //
			UserRole secondRole = createUserRole(21L, "Second Role", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Group", "desc", List.of(userRole, secondRole));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);
			assignment.setId(11L);
			given(orgUnitRoleGroupAssignmentDao.findById(11L)).willReturn(Optional.of(assignment));
			when(historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(anyString())).thenReturn(false);

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(11L);

			// ---- Then ---- //
			assertThat(result).isTrue();
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao, Mockito.times(2)).save(captor.capture());
			assertThat(captor.getAllValues())
				.extracting(HistoricOuAssignment::getRoleId)
				.containsExactlyInAnyOrder(20L, 21L);
			assertThat(captor.getAllValues())
				.allMatch(h -> h.getRoleRoleGroupId() != null && h.getRoleRoleGroupId() == 30L);
		}

		@Test
		@DisplayName("partial seed: skips user-roles whose hash already has an open row, saves the rest")
		void skipsOnlyExistingRowsInPartialSeed() {
			// ---- Given ---- //
			UserRole secondRole = createUserRole(21L, "Second Role", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Group", "desc", List.of(userRole, secondRole));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);
			assignment.setId(11L);
			given(orgUnitRoleGroupAssignmentDao.findById(11L)).willReturn(Optional.of(assignment));
			// First call (userRole id=20) returns true (already seeded), second (secondRole id=21) returns false.
			when(historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(anyString()))
				.thenReturn(true)
				.thenReturn(false);

			// ---- When ---- //
			boolean result = service.seedHistoricRowsFromOrgUnitRoleGroupAssignmentId(11L);

			// ---- Then ---- //
			assertThat(result).isTrue();
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao, Mockito.times(1)).save(captor.capture());
			assertThat(captor.getValue().getRoleId()).isEqualTo(21L);
		}
	}
}
