package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
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

import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createNamedOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitRoleGroupAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HistoricOuAssignmentServiceTest {

	@Mock
	private HistoricOuAssignmentDao historicOuAssignmentDao;
	@Mock
	private OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	@Mock
	private UserRoleDao userRoleDao;

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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			HistoricOuAssignment snapshot = captor.getValue();
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
			itSystem.setAttestationResponsible(responsible);

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getResponsibleUserUuid()).isEqualTo("responsible-uuid");
		}

		@Test
		@DisplayName("responsibleUserUuid is null when role flag is false, even if itSystem has responsible user")
		void responsibleUserUuidIsNullWhenFlagIsFalse() {
			// ---- Given ---- //
			User responsible = createUser("responsible-uuid");
			userRole.setRoleAssignmentAttestationByAttestationResponsible(false);
			itSystem.setAttestationResponsible(responsible);

			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleAdded(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getResponsibleUserUuid()).isNull();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			List<HistoricOuAssignmentExclusion> exclusions = captor.getValue().getExclusions();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			List<HistoricOuAssignmentExclusion> exclusions = captor.getValue().getExclusions();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			List<HistoricOuAssignmentExclusion> exclusions = captor.getValue().getExclusions();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			List<HistoricOuAssignmentExclusion> exclusions = captor.getValue().getExclusions();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getExclusions()).isEmpty();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			java.time.LocalDateTime expected = timestamp.toInstant()
				.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
			assertThat(captor.getValue().getAssignedWhen()).isEqualTo(expected);
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getAssignedWhen())
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getExclusions()).isEmpty();
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
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getExclusions()).isEmpty();
		}
	}

	@Nested
	@DisplayName("recordUserRoleUpdated")
	class RecordUserRoleUpdated {

		@Test
		@DisplayName("closes the existing open record by hash before saving the new one")
		void closesOldRecordThenSavesNew() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleUpdated(ou, assignment);

			// ---- Then ---- //
			verify(historicOuAssignmentDao).closeOpenByRecordHash(any(String.class), any());
			verify(historicOuAssignmentDao).save(any(HistoricOuAssignment.class));
		}

		@Test
		@DisplayName("the new saved record has validTo = null")
		void newRecordIsOpen() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleUpdated(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricOuAssignment> captor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).save(captor.capture());

			assertThat(captor.getValue().getValidTo()).isNull();
		}

		@Test
		@DisplayName("the hash used to close the old record matches the hash of the new record")
		void closedHashMatchesSavedHash() {
			// ---- Given ---- //
			OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);

			// ---- When ---- //
			service.recordUserRoleUpdated(ou, assignment);

			// ---- Then ---- //
			ArgumentCaptor<String> closedHashCaptor = ArgumentCaptor.forClass(String.class);
			ArgumentCaptor<HistoricOuAssignment> savedCaptor = ArgumentCaptor.forClass(HistoricOuAssignment.class);
			verify(historicOuAssignmentDao).closeOpenByRecordHash(closedHashCaptor.capture(), any());
			verify(historicOuAssignmentDao).save(savedCaptor.capture());

			assertThat(closedHashCaptor.getValue()).isEqualTo(savedCaptor.getValue().getRecordHash());
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
	}

	@Nested
	@DisplayName("recordRoleGroupUpdated")
	class RecordRoleGroupUpdated {

		@Test
		@DisplayName("closes all previous records and saves new ones")
		void closesAllOldRecordsAndSavesNew() {
			// ---- Given ---- //
			UserRole role2 = createUserRole(21L, "Role 2", itSystem);
			RoleGroup roleGroup = createRoleGroup(30L, "Test Role Group", "Role group description", List.of(userRole, role2));
			OrgUnitRoleGroupAssignment assignment = createOrgUnitRoleGroupAssignment(roleGroup, ou, false, false, false);

			// ---- When ---- //
			service.recordRoleGroupUpdated(ou, assignment);

			// ---- Then ---- //
			// closeOpenByRecordHash called once per role (2 roles)
			verify(historicOuAssignmentDao, Mockito.times(2))
				.closeOpenByRecordHash(any(String.class), any());
			verify(historicOuAssignmentDao).saveAll(any(List.class));
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
}
