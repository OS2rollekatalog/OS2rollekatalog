package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.model.entity.temporal.AssignedThroughType;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationUserRoleAssignment;
import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignmentConstraint;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAssignmentsUpdaterJdbcTest {

	@Mock
	private TemporalDao temporalDao;
	@Mock
	private EntityManager entityManager;
	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private UserAssignmentsUpdaterJdbc updater;

	private static final LocalDate WHEN = LocalDate.of(2025, 6, 1);

	// ---- Common base builder ---- //

	private HistoricAssignment.HistoricAssignmentBuilder base() {
		return HistoricAssignment.builder()
				.recordHash("record-hash")
				.validFrom(LocalDateTime.now().minusDays(1))
				.userUuid("user-uuid")
				.userId("user-id")
				.userName("Test User")
				.userRoleId(20L)
				.userRoleName("Test User Role")
				.userRoleDescription("User role description")
				.itSystemId(10L)
				.itSystemName("Test IT System")
				.assignedThroughType(AssignedThrough.DIRECT)
				.assignedThroughOUUuid("ou-uuid")
				.assignedThroughOUName("Test OU")
				.responsibleOUUuid("ou-uuid")
				.responsibleOUName("Test OU")
				.sensitiveRole(false)
				.extraSensitiveRole(false);
	}

	private AttestationUserRoleAssignment runAndCapture(HistoricAssignment assignment) {
		given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
		given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
		given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
		given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.empty());

		updater.updateUserRoleAssignments(WHEN);

		ArgumentCaptor<AttestationUserRoleAssignment> captor = ArgumentCaptor.forClass(AttestationUserRoleAssignment.class);
		verify(temporalDao).saveAttestationUserRoleAssignment(captor.capture());
		return captor.getValue();
	}

	// ---- ------------- ---- //

	@Nested
	@DisplayName("user and user role fields")
	class UserAndUserRoleFields {

		@Test
		@DisplayName("user fields are mapped correctly")
		void userFieldsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.userUuid("specific-user-uuid")
					.userId("specific-user-id")
					.userName("Specific User")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getUserUuid()).isEqualTo("specific-user-uuid");
			assertThat(result.getUserId()).isEqualTo("specific-user-id");
			assertThat(result.getUserName()).isEqualTo("Specific User");
		}

		@Test
		@DisplayName("user role fields are mapped correctly")
		void userRoleFieldsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.userRoleId(42L)
					.userRoleName("My User Role")
					.userRoleDescription("My Description")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getUserRoleId()).isEqualTo(42L);
			assertThat(result.getUserRoleName()).isEqualTo("My User Role");
			assertThat(result.getUserRoleDescription()).isEqualTo("My Description");
		}

		@Test
		@DisplayName("IT system fields are mapped correctly")
		void itSystemFieldsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.itSystemId(10L)
					.itSystemName("My IT System")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getItSystemId()).isEqualTo(10L);
			assertThat(result.getItSystemName()).isEqualTo("My IT System");
		}

		@Test
		@DisplayName("role group fields are mapped when present")
		void roleGroupFieldsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.roleGroupId(50L)
					.roleGroupName("My Role Group")
					.roleGroupDescription("Role Group Description")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRoleGroupId()).isEqualTo(50L);
			assertThat(result.getRoleGroupName()).isEqualTo("My Role Group");
			assertThat(result.getRoleGroupDescription()).isEqualTo("Role Group Description");
		}

		@Test
		@DisplayName("role group fields are null when absent")
		void roleGroupFieldsAreNullWhenAbsent() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRoleGroupId()).isNull();
			assertThat(result.getRoleGroupName()).isNull();
			assertThat(result.getRoleGroupDescription()).isNull();
		}

		@Test
		@DisplayName("sensitive role flags are mapped correctly")
		void sensitiveRoleFlagsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.sensitiveRole(true)
					.extraSensitiveRole(true)
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isSensitiveRole()).isTrue();
			assertThat(result.isExtraSensitiveRole()).isTrue();
		}

		@Test
		@DisplayName("roleOuUuid and roleOuName come from assignedThroughOU fields")
		void roleOuFieldsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughOUUuid("role-ou-uuid")
					.assignedThroughOUName("Role OU")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRoleOuUuid()).isEqualTo("role-ou-uuid");
			assertThat(result.getRoleOuName()).isEqualTo("Role OU");
		}

		@Test
		@DisplayName("responsibleOuUuid and responsibleOuName are taken from the historic record")
		void responsibleOuFieldsAreMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.responsibleOUUuid("responsible-ou-uuid")
					.responsibleOUName("Responsible OU")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleOuUuid()).isEqualTo("responsible-ou-uuid");
			assertThat(result.getResponsibleOuName()).isEqualTo("Responsible OU");
		}

		@Test
		@DisplayName("responsibleCollectionId is taken from the historic record")
		void responsibleCollectionIdIsMapped() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.responsibleCollectionId(99L)
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleCollectionId()).isEqualTo(99L);
		}

		@Test
		@DisplayName("assignedFrom is derived from the validFrom timestamp of the historic record")
		void assignedFromIsDerivedFromValidFrom() {
			// ---- Given ---- //
			LocalDate expectedDate = LocalDate.of(2024, 3, 15);
			HistoricAssignment assignment = base()
					.validFrom(expectedDate.atStartOfDay())
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedFrom()).isEqualTo(expectedDate);
		}

		@Test
		@DisplayName("assignedFrom is null when validFrom is null")
		void assignedFromIsNullWhenValidFromIsNull() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().validFrom(null).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedFrom()).isNull();
		}

		@Test
		@DisplayName("inherited is always false (user assignments are never inherited)")
		void inheritedIsAlwaysFalse() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.isInherited()).isFalse();
		}

		@Test
		@DisplayName("new record has a non-null recordHash")
		void newRecordHasANonNullRecordHash() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRecordHash()).isNotNull();
		}
	}

	@Nested
	@DisplayName("AssignedThrough → AssignedThroughType mapping")
	class AssignedThroughTypeMapping {

		@Test
		@DisplayName("DIRECT maps to DIRECT")
		void directMapsToDirect() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(AssignedThrough.DIRECT).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}

		@Test
		@DisplayName("ROLEGROUP maps to DIRECT (role group assignments are treated as direct)")
		void roleGroupMapsToDirect() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(AssignedThrough.ROLEGROUP).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.DIRECT);
		}

		@Test
		@DisplayName("POSITION maps to POSITION")
		void positionMapsToPosition() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(AssignedThrough.POSITION).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.POSITION);
		}

		@Test
		@DisplayName("ORGUNIT maps to ORGUNIT")
		void orgUnitMapsToOrgUnit() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(AssignedThrough.ORGUNIT).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.ORGUNIT);
		}

		@Test
		@DisplayName("TITLE maps to TITLE")
		void titleMapsToTitle() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(AssignedThrough.TITLE).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isEqualTo(AssignedThroughType.TITLE);
		}

		@Test
		@DisplayName("null assignedThroughType produces null AssignedThroughType")
		void nullAssignedThroughTypeProducesNull() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(null).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughType()).isNull();
		}
	}

	@Nested
	@DisplayName("assignedThroughUuid and assignedThroughName resolution")
	class AssignedThroughResolution {

		@Test
		@DisplayName("DIRECT: assignedThroughUuid and name are both null")
		void directHasNullUuidAndName() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughType(AssignedThrough.DIRECT)
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isNull();
			assertThat(result.getAssignedThroughName()).isNull();
		}

		@Test
		@DisplayName("TITLE: uses title UUID and name")
		void titleUsesTitleFields() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughType(AssignedThrough.TITLE)
					.assignedThroughTitleUuid("title-uuid")
					.assignedThroughTitleName("My Title")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isEqualTo("title-uuid");
			assertThat(result.getAssignedThroughName()).isEqualTo("My Title");
		}

		@Test
		@DisplayName("ROLEGROUP: uses role group ID (as string) and name")
		void roleGroupUsesRoleGroupIdAndName() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ROLEGROUP)
					.assignedThroughRoleGroupId(30L)
					.assignedThroughRoleGroupName("My Role Group")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isEqualTo("30");
			assertThat(result.getAssignedThroughName()).isEqualTo("My Role Group");
		}

		@Test
		@DisplayName("ROLEGROUP: assignedThroughUuid is null when role group ID is null")
		void roleGroupHasNullUuidWhenIdIsNull() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ROLEGROUP)
					.assignedThroughRoleGroupId(null)
					.assignedThroughRoleGroupName("My Role Group")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isNull();
		}

		@Test
		@DisplayName("ORGUNIT: uses OU UUID and name")
		void orgUnitUsesOuFields() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughType(AssignedThrough.ORGUNIT)
					.assignedThroughOUUuid("parent-ou-uuid")
					.assignedThroughOUName("Parent OU")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isEqualTo("parent-ou-uuid");
			assertThat(result.getAssignedThroughName()).isEqualTo("Parent OU");
		}

		@Test
		@DisplayName("POSITION: uses OU UUID and name (default branch)")
		void positionUsesOuFields() {
			// ---- Given ---- //
			HistoricAssignment assignment = base()
					.assignedThroughType(AssignedThrough.POSITION)
					.assignedThroughOUUuid("position-ou-uuid")
					.assignedThroughOUName("Position OU")
					.build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isEqualTo("position-ou-uuid");
			assertThat(result.getAssignedThroughName()).isEqualTo("Position OU");
		}

		@Test
		@DisplayName("null assignedThroughType: both UUID and name are null")
		void nullAssignedThroughTypeHasNullUuidAndName() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().assignedThroughType(null).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getAssignedThroughUuid()).isNull();
			assertThat(result.getAssignedThroughName()).isNull();
		}
	}

	@Nested
	@DisplayName("constraint formatting")
	class ConstraintFormatting {

		@Test
		@DisplayName("single constraint is formatted as 'name: value'")
		void singleConstraintIsFormatted() {
			// ---- Given ---- //
			HistoricAssignmentConstraint constraint = HistoricAssignmentConstraint.builder()
					.constraintTypeUuid("uuid-1")
					.constraintTypeName("It-system")
					.constraintTypeEntityId("entity-1")
					.value(List.of("42"))
					.build();
			HistoricAssignment assignment = base().constraints(Set.of(constraint)).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getPostponedConstraints()).isEqualTo("It-system: 42");
		}

		@Test
		@DisplayName("constraint with multiple values formats them comma-separated")
		void multipleValuesAreCommaSeparated() {
			// ---- Given ---- //
			HistoricAssignmentConstraint constraint = HistoricAssignmentConstraint.builder()
					.constraintTypeUuid("uuid-1")
					.constraintTypeName("Enhed")
					.constraintTypeEntityId("entity-1")
					.value(List.of("val1", "val2", "val3"))
					.build();
			HistoricAssignment assignment = base().constraints(Set.of(constraint)).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getPostponedConstraints()).isEqualTo("Enhed: val1,val2,val3");
		}

		@Test
		@DisplayName("no constraints produces null postponedConstraints")
		void noConstraintsProducesNull() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().build(); // constraints defaults to empty set

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getPostponedConstraints()).isNull();
		}

		@Test
		@DisplayName("null constraints produces null postponedConstraints")
		void nullConstraintsProducesNull() {
			// ---- Given ---- //
			HistoricAssignment assignment = base().constraints(null).build();

			// ---- When ---- //
			AttestationUserRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getPostponedConstraints()).isNull();
		}
	}

	@Nested
	@DisplayName("upsert behaviour (hash-based create vs update)")
	class UpsertBehaviour {

		@Test
		@DisplayName("updates existing record when hash matches")
		void updatesExistingRecordWhenHashMatches() {
			// ---- Given ---- //
			AttestationUserRoleAssignment existing = AttestationUserRoleAssignment.builder()
					.userUuid("user-uuid").userRoleId(20L).itSystemId(10L)
					.userId("user-id").userName("Test User")
					.userRoleName("Test User Role").userRoleDescription("User role description")
					.itSystemName("Test IT System").responsibleOuUuid("ou-uuid").responsibleOuName("Test OU")
					.assignedThroughType(AssignedThroughType.DIRECT)
					.inherited(false).sensitiveRole(false).extraSensitiveRole(false)
					.assignedFrom(LocalDate.of(2024, 1, 1))
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(base().build()));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.of(existing));
			given(temporalDao.updateAttestationUserRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao).updateAttestationUserRoleAssignment(existing);
			verify(temporalDao, never()).saveAttestationUserRoleAssignment(any());
		}

		@Test
		@DisplayName("does not call updateAttestationUserRoleAssignment when content is identical")
		void doesNotUpdateWhenContentIsIdentical() {
			// ---- Given ---- //
			LocalDate assignedFrom = LocalDate.of(2024, 1, 1);
			AttestationUserRoleAssignment existing = AttestationUserRoleAssignment.builder()
					.userUuid("user-uuid").userRoleId(20L).itSystemId(10L)
					.userId("user-id").userName("Test User")
					.userRoleName("Test User Role").userRoleDescription("User role description")
					.itSystemName("Test IT System").responsibleOuUuid("ou-uuid").responsibleOuName("Test OU")
					.roleOuUuid("ou-uuid").roleOuName("Test OU")
					.assignedThroughType(AssignedThroughType.DIRECT)
					.inherited(false).sensitiveRole(false).extraSensitiveRole(false)
					.assignedFrom(assignedFrom)
					.build();

			HistoricAssignment assignment = base()
					.validFrom(assignedFrom.atStartOfDay())
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.of(existing));
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao, never()).updateAttestationUserRoleAssignment(any());
			verify(temporalDao, never()).saveAttestationUserRoleAssignment(any());
		}

		@Test
		@DisplayName("assignedFrom workaround: keeps older date when existing assignedFrom is earlier")
		void assignedFromWorkaroundKeepsOlderDate() {
			// ---- Given ---- //
			LocalDate olderDate = LocalDate.of(2023, 1, 1);
			LocalDate newerDate = LocalDate.of(2024, 6, 1);

			AttestationUserRoleAssignment existing = AttestationUserRoleAssignment.builder()
					.userUuid("user-uuid").userRoleId(20L).itSystemId(10L)
					.userId("user-id").userName("Test User")
					.userRoleName("Test User Role").userRoleDescription("User role description")
					.itSystemName("Test IT System").responsibleOuUuid("ou-uuid").responsibleOuName("Test OU")
					.roleOuUuid("ou-uuid").roleOuName("Test OU")
					.assignedThroughType(AssignedThroughType.DIRECT)
					.inherited(false).sensitiveRole(false).extraSensitiveRole(false)
					.assignedFrom(olderDate)
					.build();

			HistoricAssignment assignment = base()
					.validFrom(newerDate.atStartOfDay())
					.userName("Changed Name") // ensure contentEquals() returns false
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.of(existing));
			given(temporalDao.updateAttestationUserRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			assertThat(existing.getAssignedFrom()).isEqualTo(olderDate);
		}

		@Test
		@DisplayName("assignedFrom workaround: uses updated date when updated assignedFrom is earlier")
		void assignedFromWorkaroundUsesUpdatedDateWhenItIsEarlier() {
			// ---- Given ---- //
			LocalDate newerDate = LocalDate.of(2024, 6, 1);
			LocalDate olderDate = LocalDate.of(2023, 1, 1);

			AttestationUserRoleAssignment existing = AttestationUserRoleAssignment.builder()
					.userUuid("user-uuid").userRoleId(20L).itSystemId(10L)
					.userId("user-id").userName("Test User")
					.userRoleName("Test User Role").userRoleDescription("User role description")
					.itSystemName("Test IT System").responsibleOuUuid("ou-uuid").responsibleOuName("Test OU")
					.roleOuUuid("ou-uuid").roleOuName("Test OU")
					.assignedThroughType(AssignedThroughType.DIRECT)
					.inherited(false).sensitiveRole(false).extraSensitiveRole(false)
					.assignedFrom(newerDate)
					.build();

			HistoricAssignment assignment = base()
					.validFrom(olderDate.atStartOfDay())
					.userName("Changed Name") // ensure contentEquals() returns false
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.of(existing));
			given(temporalDao.updateAttestationUserRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			assertThat(existing.getAssignedFrom()).isEqualTo(olderDate);
		}

		@Test
		@DisplayName("assignedFrom workaround: skipped when existing assignedFrom is null")
		void assignedFromWorkaroundSkippedWhenExistingIsNull() {
			// ---- Given ---- //
			LocalDate newDate = LocalDate.of(2024, 6, 1);

			AttestationUserRoleAssignment existing = AttestationUserRoleAssignment.builder()
					.userUuid("user-uuid").userRoleId(20L).itSystemId(10L)
					.userId("user-id").userName("Test User")
					.userRoleName("Test User Role").userRoleDescription("User role description")
					.itSystemName("Test IT System").responsibleOuUuid("ou-uuid").responsibleOuName("Test OU")
					.roleOuUuid("ou-uuid").roleOuName("Test OU")
					.assignedThroughType(AssignedThroughType.DIRECT)
					.inherited(false).sensitiveRole(false).extraSensitiveRole(false)
					.assignedFrom(null)
					.build();

			HistoricAssignment assignment = base()
					.validFrom(newDate.atStartOfDay())
					.userName("Changed Name") // ensure contentEquals() returns false
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.of(existing));
			given(temporalDao.updateAttestationUserRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			// TemporalFieldUpdater will have copied newDate onto existing; workaround is skipped so it stays
			assertThat(existing.getAssignedFrom()).isEqualTo(newDate);
		}

		@Test
		@DisplayName("assignedFrom workaround: skipped when updated assignedFrom is null")
		void assignedFromWorkaroundSkippedWhenUpdatedIsNull() {
			// ---- Given ---- //
			LocalDate originalDate = LocalDate.of(2023, 5, 10);

			AttestationUserRoleAssignment existing = AttestationUserRoleAssignment.builder()
					.userUuid("user-uuid").userRoleId(20L).itSystemId(10L)
					.userId("user-id").userName("Test User")
					.userRoleName("Test User Role").userRoleDescription("User role description")
					.itSystemName("Test IT System").responsibleOuUuid("ou-uuid").responsibleOuName("Test OU")
					.roleOuUuid("ou-uuid").roleOuName("Test OU")
					.assignedThroughType(AssignedThroughType.DIRECT)
					.inherited(false).sensitiveRole(false).extraSensitiveRole(false)
					.assignedFrom(originalDate)
					.build();

			HistoricAssignment assignment = base()
					.validFrom(null)
					.userName("Changed Name") // ensure contentEquals() returns false
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.of(existing));
			given(temporalDao.updateAttestationUserRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			// TemporalFieldUpdater copies null onto existing; workaround is skipped so null remains
			assertThat(existing.getAssignedFrom()).isNull();
		}
	}

	@Nested
	@DisplayName("stale record invalidation")
	class StaleRecordInvalidation {

		@Test
		@DisplayName("invalidates assignments not touched in this run")
		void invalidatesStaleAssignments() {
			// ---- Given ---- //
			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(base().build()));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.empty());
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of(101L, 102L));

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao).invalidateUserRoleAssignmentsWithIdsIn(List.of(101L, 102L), WHEN);
		}

		@Test
		@DisplayName("does not call invalidate when there are no stale assignments")
		void doesNotInvalidateWhenNoneAreStale() {
			// ---- Given ---- //
			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(base().build()));
			given(temporalDao.findValidUserRoleAssignmentWithHash(any(), any())).willReturn(java.util.Optional.empty());
			given(temporalDao.findAllValidUserRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateUserRoleAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao, never()).invalidateUserRoleAssignmentsWithIdsIn(any(), any());
		}
	}

	@Nested
	@DisplayName("no assignments for date")
	class NoAssignmentsForDate {

		@Test
		@DisplayName("throws AttestationDataUpdaterException when no IT systems have assignments")
		void throwsExceptionWhenNoAssignments() {
			// ---- Given ---- //
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of());

			// ---- When / Then ---- //
			org.junit.jupiter.api.Assertions.assertThrows(
					AttestationDataUpdaterException.class,
					() -> updater.updateUserRoleAssignments(WHEN)
			);
		}

		@Test
		@DisplayName("throws AttestationDataUpdaterException when IT systems have no records")
		void throwsExceptionWhenItSystemsReturnNoRecords() {
			// ---- Given ---- //
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of());

			// ---- When / Then ---- //
			org.junit.jupiter.api.Assertions.assertThrows(
					AttestationDataUpdaterException.class,
					() -> updater.updateUserRoleAssignments(WHEN)
			);
		}

		@Test
		@DisplayName("does not save any assignment when the list is empty")
		void doesNotSaveWhenListIsEmpty() {
			// ---- Given ---- //
			given(temporalDao.getDistinctAssignedItSystems(WHEN)).willReturn(List.of(10L));
			given(temporalDao.findHistoricAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of());

			// ---- When ---- //
			org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
					() -> updater.updateUserRoleAssignments(WHEN));

			// ---- Then ---- //
			verify(temporalDao, never()).saveAttestationUserRoleAssignment(any());
		}
	}
}
