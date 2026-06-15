package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.exception.AttestationDataUpdaterException;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignment;
import dk.digitalidentity.rc.attestation.model.entity.temporal.AttestationSystemRoleAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemRoleAssignmentsUpdaterJdbcTest {

	@Mock
	private TemporalDao temporalDao;
	@Mock
	private PlatformTransactionManager transactionManager;

	@InjectMocks
	private SystemRoleAssignmentsUpdaterJdbc updater;

	private static final LocalDate WHEN = LocalDate.of(2025, 6, 1);

	// ---- Common base builder ---- //

	private HistoricItSystemAssignment.HistoricItSystemAssignmentBuilder base() {
		return HistoricItSystemAssignment.builder()
				.recordHash("record-hash")
				.validFrom(LocalDateTime.now().minusDays(1))
				.itSystemId(10L)
				.itSystemName("Test IT System")
				.itSystemAttestationExempt(false)
				.userRoleId(20L)
				.userRoleName("Test User Role")
				.userRoleDescription("User role description")
				.systemRoleId(30L)
				.systemRoleName("Test System Role")
				.systemRoleDescription("System role description")
				.responsibleCollectionId(7L);
	}

	private AttestationSystemRoleAssignment runAndCapture(HistoricItSystemAssignment assignment) {
		given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
		given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
		given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(assignment));
		given(temporalDao.findValidSystemRoleAssignmentWithHash(any(), anyString())).willReturn(null);
		given(temporalDao.findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

		updater.updateItSystemAssignments(WHEN);

		ArgumentCaptor<AttestationSystemRoleAssignment> captor = ArgumentCaptor.forClass(AttestationSystemRoleAssignment.class);
		verify(temporalDao).saveAttestationSystemRoleAssignment(captor.capture());
		return captor.getValue();
	}

	// ---- ------------- ---- //

	@Nested
	@DisplayName("field mapping")
	class FieldMapping {

		@Test
		@DisplayName("IT system fields are mapped correctly")
		void itSystemFieldsAreMapped() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base()
					.itSystemId(10L)
					.itSystemName("My IT System")
					.build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getItSystemId()).isEqualTo(10L);
			assertThat(result.getItSystemName()).isEqualTo("My IT System");
		}

		@Test
		@DisplayName("user role fields are mapped correctly")
		void userRoleFieldsAreMapped() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base()
					.userRoleId(42L)
					.userRoleName("My User Role")
					.userRoleDescription("My Description")
					.build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getUserRoleId()).isEqualTo(42L);
			assertThat(result.getUserRoleName()).isEqualTo("My User Role");
			assertThat(result.getUserRoleDescription()).isEqualTo("My Description");
		}

		@Test
		@DisplayName("system role fields are mapped correctly")
		void systemRoleFieldsAreMapped() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base()
					.systemRoleId(99L)
					.systemRoleName("My System Role")
					.systemRoleDescription("System role description")
					.build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getSystemRoleId()).isEqualTo(99L);
			assertThat(result.getSystemRoleName()).isEqualTo("My System Role");
			assertThat(result.getSystemRoleDescription()).isEqualTo("System role description");
		}

		@Test
		@DisplayName("responsibleCollectionId is mapped correctly")
		void responsibleCollectionIdIsMapped() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base()
					.responsibleCollectionId(123L)
					.build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleCollectionId()).isEqualTo(123L);
		}

		@Test
		@DisplayName("responsibleCollectionId is null when not set")
		void responsibleCollectionIdIsNullWhenAbsent() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base()
					.responsibleCollectionId(null)
					.build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getResponsibleCollectionId()).isNull();
		}

		@Test
		@DisplayName("new record has a non-null recordHash")
		void newRecordHasNonNullRecordHash() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base().build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getRecordHash()).isNotNull();
		}
	}

	@Nested
	@DisplayName("constraint mapping")
	class ConstraintMapping {

		@Test
		@DisplayName("single constraint is mapped with name, valueType and value")
		void singleConstraintIsMapped() {
			// ---- Given ---- //
			HistoricItSystemAssignmentConstraint constraint = HistoricItSystemAssignmentConstraint.builder()
					.constraintName("It-system")
					.constraintValueType(ConstraintValueType.VALUE)
					.constraintValue("42")
					.build();
			HistoricItSystemAssignment assignment = base().constraints(List.of(constraint)).build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getConstraints()).hasSize(1);
			AttestationSystemRoleAssignmentConstraint mapped = result.getConstraints().iterator().next();
			assertThat(mapped.getName()).isEqualTo("It-system");
			assertThat(mapped.getValueType()).isEqualTo(ConstraintValueType.VALUE);
			assertThat(mapped.getValue()).isEqualTo("42");
		}

		@Test
		@DisplayName("multiple constraints are all mapped")
		void multipleConstraintsAreMapped() {
			// ---- Given ---- //
			HistoricItSystemAssignmentConstraint c1 = HistoricItSystemAssignmentConstraint.builder()
					.constraintName("It-system")
					.constraintValueType(ConstraintValueType.VALUE)
					.constraintValue("42")
					.build();
			HistoricItSystemAssignmentConstraint c2 = HistoricItSystemAssignmentConstraint.builder()
					.constraintName("Enhed")
					.constraintValueType(ConstraintValueType.INHERITED)
					.constraintValue(null)
					.build();
			HistoricItSystemAssignment assignment = base().constraints(List.of(c1, c2)).build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getConstraints())
					.hasSize(2)
					.extracting(AttestationSystemRoleAssignmentConstraint::getName)
					.containsExactlyInAnyOrder("It-system", "Enhed");
		}

		@Test
		@DisplayName("each constraint back-references the parent assignment")
		void constraintBackReferencesAssignment() {
			// ---- Given ---- //
			HistoricItSystemAssignmentConstraint constraint = HistoricItSystemAssignmentConstraint.builder()
					.constraintName("It-system")
					.constraintValueType(ConstraintValueType.VALUE)
					.constraintValue("42")
					.build();
			HistoricItSystemAssignment assignment = base().constraints(List.of(constraint)).build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			AttestationSystemRoleAssignmentConstraint mapped = result.getConstraints().iterator().next();
			assertThat(mapped.getAssignment()).isSameAs(result);
		}

		@Test
		@DisplayName("no constraints when constraint list is empty")
		void noConstraintsWhenEmpty() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base().constraints(List.of()).build();

			// ---- When ---- //
			AttestationSystemRoleAssignment result = runAndCapture(assignment);

			// ---- Then ---- //
			assertThat(result.getConstraints()).isEmpty();
		}
	}

	@Nested
	@DisplayName("upsert behaviour (hash-based create vs update)")
	class UpsertBehaviour {

		@Test
		@DisplayName("saves a new record when no existing record matches the hash")
		void savesNewRecordWhenNoHashMatch() {
			// ---- Given ---- //
			HistoricItSystemAssignment assignment = base().build();

			// ---- When ---- //
			runAndCapture(assignment); // verify(save) is inside runAndCapture

			// ---- Then: save called, update not called ---- //
			verify(temporalDao, never()).updateAttestationSystemRoleAssignment(any());
		}

		@Test
		@DisplayName("updates the existing record when one matches the hash")
		void updatesExistingRecordWhenHashMatches() {
			// ---- Given ---- //
			AttestationSystemRoleAssignment existing = AttestationSystemRoleAssignment.builder()
					.itSystemId(10L).userRoleId(20L).systemRoleId(30L).build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
			given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L))
					.willReturn(List.of(base().build()));
			given(temporalDao.findValidSystemRoleAssignmentWithHash(any(), anyString())).willReturn(existing);
			given(temporalDao.updateAttestationSystemRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateItSystemAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao).updateAttestationSystemRoleAssignment(existing);
			verify(temporalDao, never()).saveAttestationSystemRoleAssignment(any());
		}

		@Test
		@DisplayName("on update, constraints on existing record are replaced with the new ones")
		void constraintsAreReplacedOnUpdate() {
			// ---- Given ---- //
			AttestationSystemRoleAssignment existing = AttestationSystemRoleAssignment.builder()
					.itSystemId(10L).userRoleId(20L).systemRoleId(30L)
					.constraints(Set.of(AttestationSystemRoleAssignmentConstraint.builder()
							.name("old-constraint").build()))
					.build();

			HistoricItSystemAssignmentConstraint newConstraint = HistoricItSystemAssignmentConstraint.builder()
					.constraintName("new-constraint")
					.constraintValueType(ConstraintValueType.VALUE)
					.constraintValue("99")
					.build();

			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
			given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L))
					.willReturn(List.of(base().constraints(List.of(newConstraint)).build()));
			given(temporalDao.findValidSystemRoleAssignmentWithHash(any(), anyString())).willReturn(existing);
			given(temporalDao.updateAttestationSystemRoleAssignment(any())).willReturn(1);
			given(temporalDao.findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateItSystemAssignments(WHEN);

			// ---- Then ---- //
			assertThat(existing.getConstraints())
					.hasSize(1)
					.extracting(AttestationSystemRoleAssignmentConstraint::getName)
					.containsExactly("new-constraint");
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
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
			given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(base().build()));
			given(temporalDao.findValidSystemRoleAssignmentWithHash(any(), anyString())).willReturn(null);
			given(temporalDao.findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of(201L, 202L));

			// ---- When ---- //
			updater.updateItSystemAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao).invalidateSystemRoleAssignmentsWithIdsIn(List.of(201L, 202L), WHEN);
		}

		@Test
		@DisplayName("does not call invalidate when there are no stale assignments")
		void doesNotInvalidateWhenNoneAreStale() {
			// ---- Given ---- //
			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
			given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of(base().build()));
			given(temporalDao.findValidSystemRoleAssignmentWithHash(any(), anyString())).willReturn(null);
			given(temporalDao.findAllValidSystemRoleAssignmentIdsByUpdatedAtLessThan(WHEN)).willReturn(List.of());

			// ---- When ---- //
			updater.updateItSystemAssignments(WHEN);

			// ---- Then ---- //
			verify(temporalDao, never()).invalidateSystemRoleAssignmentsWithIdsIn(any(), any());
		}
	}

	@Nested
	@DisplayName("no assignments for date")
	class NoAssignmentsForDate {

		@Test
		@DisplayName("throws AttestationDataUpdaterException when no IT systems have assignments")
		void throwsWhenNoItSystems() {
			// ---- Given ---- //
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of());

			// ---- When / Then ---- //
			assertThrows(AttestationDataUpdaterException.class, () -> updater.updateItSystemAssignments(WHEN));
		}

		@Test
		@DisplayName("throws AttestationDataUpdaterException when IT system list returns no records")
		void throwsWhenItSystemHasNoRecords() {
			// ---- Given ---- //
			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
			given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of());

			// ---- When / Then ---- //
			assertThrows(AttestationDataUpdaterException.class, () -> updater.updateItSystemAssignments(WHEN));
		}

		@Test
		@DisplayName("does not save any assignment when the list is empty")
		void doesNotSaveWhenListIsEmpty() {
			// ---- Given ---- //
			given(transactionManager.getTransaction(any())).willReturn(mock(TransactionStatus.class));
			given(temporalDao.getDistinctItSystemIdsFromHistoricItSystemAssignment(WHEN)).willReturn(List.of(10L));
			given(temporalDao.listHistoricItSystemAssignmentsByItSystemAndDate(WHEN, 10L)).willReturn(List.of());

			// ---- When ---- //
			assertThrows(Exception.class, () -> updater.updateItSystemAssignments(WHEN));

			// ---- Then ---- //
			verify(temporalDao, never()).saveAttestationSystemRoleAssignment(any());
		}
	}
}
