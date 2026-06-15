package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.attestation.dao.AttestationResponsibleCollectionDao;
import dk.digitalidentity.rc.attestation.model.entity.AttestationResponsibleCollection;
import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.serializer.SystemRoleAssignmentDao;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricItSystemAssignmentConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
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

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createConstraintValue;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRole;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricItSystemAssignmentServiceTest {

	@Mock
	private HistoricItSystemAssignmentDao dao;

	@Mock
	private AttestationResponsibleCollectionDao attestationResponsibleCollectionDao;

	@Mock
	private SystemRoleAssignmentDao systemRoleAssignmentDao;

	@InjectMocks
	private HistoricItSystemAssignmentService service;

	private ItSystem itSystem;
	private UserRole userRole;
	private SystemRole systemRole;

	@BeforeEach
	void setup() {
		itSystem = createItSystem(10L, "Test IT System");
		userRole = createUserRole(20L, "Test User Role", itSystem);
		userRole.setDescription("User role description");
		systemRole = createSystemRole(30L, "Test System Role");
	}

	private List<HistoricItSystemAssignment> captureSavedRecords() {
		ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
		verify(dao).save(captor.capture());
		return List.of(captor.getValue());
	}

	private List<List<HistoricItSystemAssignment>> captureAllSavedBatches() {
		ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
		verify(dao, Mockito.atLeastOnce()).save(captor.capture());
		return captor.getAllValues().stream()
			.map(List::of)
			.collect(java.util.stream.Collectors.toList());
	}

	@Nested
	@DisplayName("recordSystemRoleAssignmentAdded creates a correct record")
	class RecordSystemRoleAssignmentAdded {

		@Test
		@DisplayName("IT system fields are mapped correctly")
		void itSystemFieldsAreMapped() {
			itSystem.setAttestationExempt(true);
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			List<HistoricItSystemAssignment> saved = captureSavedRecords();
			assertThat(saved).hasSize(1);
			HistoricItSystemAssignment snapshot = saved.getFirst();
			assertThat(snapshot.getItSystemId()).isEqualTo(10L);
			assertThat(snapshot.getItSystemName()).isEqualTo("Test IT System");
			assertThat(snapshot.isItSystemAttestationExempt()).isTrue();
		}

		@Test
		@DisplayName("user role fields are mapped correctly")
		void userRoleFieldsAreMapped() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			HistoricItSystemAssignment snapshot = captureSavedRecords().getFirst();
			assertThat(snapshot.getUserRoleId()).isEqualTo(20L);
			assertThat(snapshot.getUserRoleName()).isEqualTo("Test User Role");
			assertThat(snapshot.getUserRoleDescription()).isEqualTo("User role description");
		}

		@Test
		@DisplayName("system role fields are mapped correctly")
		void systemRoleFieldsAreMapped() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			HistoricItSystemAssignment snapshot = captureSavedRecords().getFirst();
			assertThat(snapshot.getSystemRoleId()).isEqualTo(30L);
			assertThat(snapshot.getSystemRoleName()).isEqualTo("Test System Role");
			assertThat(snapshot.getSystemRoleDescription()).isEqualTo("Description of Test System Role");
		}

		@Test
		@DisplayName("new record is open (validFrom set, validTo null, recordHash set)")
		void newRecordIsOpen() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			HistoricItSystemAssignment snapshot = captureSavedRecords().getFirst();
			assertThat(snapshot.getValidFrom()).isNotNull();
			assertThat(snapshot.getValidTo()).isNull();
			assertThat(snapshot.getRecordHash()).isNotNull();
		}

		@Test
		@DisplayName("responsibleCollectionId is set when role flag is true and itSystem has responsible user")
		void responsibleCollectionIdIsSetWhenFlagAndUserPresent() {
			User responsible = createUser("responsible-uuid");
			userRole.setRoleAssignmentAttestationByAttestationResponsible(true);
			itSystem.addAttestationResponsible(responsible);
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("responsible-uuid"))));

			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			List<HistoricItSystemAssignment> saved = captureSavedRecords();
			assertThat(saved).hasSize(1);
			// TODO: legacy responsibleUserUuid removed in multi-owner refactor — collection lookup is set up elsewhere
			assertThat(saved.getFirst().getResponsibleCollectionId()).isNotNull();
		}

		// TODO: legacy fan-out test removed — multi-owner now produces ONE row with a responsibleCollectionId,
		// fan-out to individual users happens at attestation-creation time via the collection.

		@Test
		@DisplayName("responsibleCollectionId is set even when the assignment-attestation flag is false — role construction attestation only requires a responsible on the IT system")
		void responsibleCollectionIdIsSetEvenWhenFlagIsFalse() {
			// Regression: rolleopbygnings-attestering (IT_SYSTEM_ROLES_ATTESTATION) var fejlagtigt
			// betinget af tildelings-krydset, så systemansvarlige ikke fik attesteringer for roller
			// uden krydset. Jf. doc/Attestation-Modul-Udvikler-Guide.md §2.3.
			User responsible = createUser("responsible-uuid");
			userRole.setRoleAssignmentAttestationByAttestationResponsible(false);
			itSystem.addAttestationResponsible(responsible);
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("responsible-uuid"))));

			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			List<HistoricItSystemAssignment> saved = captureSavedRecords();
			assertThat(saved).hasSize(1);
			assertThat(saved.getFirst().getResponsibleCollectionId()).isEqualTo(42L);
		}

		@Test
		@DisplayName("responsibleCollectionId is null when the IT system has no responsible collection")
		void responsibleCollectionIdIsNullWithoutCollection() {
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.empty());

			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			List<HistoricItSystemAssignment> saved = captureSavedRecords();
			assertThat(saved).hasSize(1);
			assertThat(saved.getFirst().getResponsibleCollectionId()).isNull();
		}

		@Test
		@DisplayName("constraints are mapped with correct name, valueType and value")
		void constraintsAreMapped() {
			SystemRoleAssignmentConstraintValue cv = createConstraintValue("It-system", ConstraintValueType.VALUE, "42");
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setConstraintValues(List.of(cv));

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			List<HistoricItSystemAssignmentConstraint> constraints = captureSavedRecords().getFirst().getConstraints();
			assertThat(constraints).hasSize(1);
			assertThat(constraints.getFirst().getConstraintName()).isEqualTo("It-system");
			assertThat(constraints.getFirst().getConstraintValueType()).isEqualTo(ConstraintValueType.VALUE);
			assertThat(constraints.getFirst().getConstraintValue()).isEqualTo("42");
		}

		@Test
		@DisplayName("multiple constraints are all mapped")
		void multipleConstraintsAreMapped() {
			SystemRoleAssignmentConstraintValue cv1 = createConstraintValue("It-system", ConstraintValueType.VALUE, "42");
			SystemRoleAssignmentConstraintValue cv2 = createConstraintValue("Enhed", ConstraintValueType.INHERITED, null);
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setConstraintValues(List.of(cv1, cv2));

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			assertThat(captureSavedRecords().getFirst().getConstraints())
				.hasSize(2)
				.extracting(HistoricItSystemAssignmentConstraint::getConstraintName)
				.containsExactlyInAnyOrder("It-system", "Enhed");
		}

		@Test
		@DisplayName("no constraints are created when constraintValues is empty")
		void noConstraintsWhenEmpty() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			assertThat(captureSavedRecords().getFirst().getConstraints()).isEmpty();
		}

		@Test
		@DisplayName("no constraints are created when constraintValues is null")
		void noConstraintsWhenNull() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setConstraintValues(null);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			assertThat(captureSavedRecords().getFirst().getConstraints()).isEmpty();
		}
	}

	@Nested
	@DisplayName("record hash stability")
	class RecordHashStability {

		@Test
		@DisplayName("same assignment with same constraints produces the same hash")
		void sameAssignmentProducesSameHash() {
			SystemRoleAssignmentConstraintValue cv = createConstraintValue("It-system", ConstraintValueType.VALUE, "42");

			SystemRoleAssignment assignment1 = createSystemRoleAssignment(systemRole);
			assignment1.setConstraintValues(List.of(cv));

			SystemRoleAssignment assignment2 = createSystemRoleAssignment(systemRole);
			assignment2.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "42")));

			service.recordSystemRoleAssignmentAdded(userRole, assignment1);
			service.recordSystemRoleAssignmentAdded(userRole, assignment2);

			List<List<HistoricItSystemAssignment>> batches = captureAllSavedBatches();
			assertThat(batches).hasSize(2);
			assertThat(batches.get(0).getFirst().getRecordHash())
				.isEqualTo(batches.get(1).getFirst().getRecordHash());
		}

		@Test
		@DisplayName("different constraint value produces a different hash")
		void differentConstraintValueProducesDifferentHash() {
			SystemRoleAssignment assignment1 = createSystemRoleAssignment(systemRole);
			assignment1.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "42")));

			SystemRoleAssignment assignment2 = createSystemRoleAssignment(systemRole);
			assignment2.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "99")));

			service.recordSystemRoleAssignmentAdded(userRole, assignment1);
			service.recordSystemRoleAssignmentAdded(userRole, assignment2);

			List<List<HistoricItSystemAssignment>> batches = captureAllSavedBatches();
			assertThat(batches.get(0).getFirst().getRecordHash())
				.isNotEqualTo(batches.get(1).getFirst().getRecordHash());
		}

		@Test
		@DisplayName("different constraint name produces a different hash")
		void differentConstraintNameProducesDifferentHash() {
			SystemRoleAssignment assignment1 = createSystemRoleAssignment(systemRole);
			assignment1.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "42")));

			SystemRoleAssignment assignment2 = createSystemRoleAssignment(systemRole);
			assignment2.setConstraintValues(List.of(createConstraintValue("Enhed", ConstraintValueType.VALUE, "42")));

			service.recordSystemRoleAssignmentAdded(userRole, assignment1);
			service.recordSystemRoleAssignmentAdded(userRole, assignment2);

			List<List<HistoricItSystemAssignment>> batches = captureAllSavedBatches();
			assertThat(batches.get(0).getFirst().getRecordHash())
				.isNotEqualTo(batches.get(1).getFirst().getRecordHash());
		}

		@Test
		@DisplayName("multiple responsibles produce one record with a collection id (fan-out happens at attestation-creation time)")
		void differentResponsibleProducesDifferentHash() {
			userRole.setRoleAssignmentAttestationByAttestationResponsible(true);
			itSystem.addAttestationResponsible(createUser("uuid-a"));
			itSystem.addAttestationResponsible(createUser("uuid-b"));
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("uuid-a", "uuid-b"))));

			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			List<HistoricItSystemAssignment> saved = captureSavedRecords();
			assertThat(saved).hasSize(1);
			assertThat(saved.getFirst().getResponsibleCollectionId()).isEqualTo(42L);
		}
	}

	@Nested
	@DisplayName("recordSystemRoleAssignmentSeedIfMissing")
	class RecordSystemRoleAssignmentSeedIfMissing {

		@Test
		@DisplayName("inserts a new record when no open row with the same hash exists")
		void insertsWhenMissing() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			when(dao.existsByRecordHashAndValidToIsNull(any())).thenReturn(false);

			// ---- When ---- //
			service.recordSystemRoleAssignmentSeedIfMissing(userRole, assignment);

			// ---- Then ---- //
			verify(dao).save(any(HistoricItSystemAssignment.class));
		}

		@Test
		@DisplayName("does nothing when an open row with the same hash already exists")
		void skipsWhenPresent() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			when(dao.existsByRecordHashAndValidToIsNull(any())).thenReturn(true);

			// ---- When ---- //
			service.recordSystemRoleAssignmentSeedIfMissing(userRole, assignment);

			// ---- Then ---- //
			verify(dao, never()).save(any(HistoricItSystemAssignment.class));
		}

		@Test
		@DisplayName("seed produces the same hash as a fresh add for the same assignment")
		void hashUsedMatchesAddHash() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			when(dao.existsByRecordHashAndValidToIsNull(any())).thenReturn(false);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);
			service.recordSystemRoleAssignmentSeedIfMissing(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao, Mockito.times(2)).save(captor.capture());
			assertThat(captor.getAllValues().get(0).getRecordHash())
				.isEqualTo(captor.getAllValues().get(1).getRecordHash());
		}
	}

	@Nested
	@DisplayName("recordSystemRoleAssignmentEdited")
	class RecordSystemRoleAssignmentEdited {

		@Test
		@DisplayName("closes every pre-edit hash variant and inserts a fresh record when hash changes")
		void closesAndInsertsOnRealEdit() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			List<String> preEditHashes = List.of("pre-edit-hash-current", "pre-edit-hash-legacy");

			// ---- When ---- //
			service.recordSystemRoleAssignmentEdited(userRole, assignment, preEditHashes);

			// ---- Then ---- //
			verify(dao).closeOpenByRecordHash(eq("pre-edit-hash-current"), any());
			verify(dao).closeOpenByRecordHash(eq("pre-edit-hash-legacy"), any());
			verify(dao).save(any(HistoricItSystemAssignment.class));
		}

		@Test
		@DisplayName("does nothing when the new hash matches one of the pre-edit hash variants")
		void noopWhenHashUnchanged() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			List<String> preEditHashes = service.computeRecordHashVariants(userRole, assignment);

			// ---- When ---- //
			service.recordSystemRoleAssignmentEdited(userRole, assignment, preEditHashes);

			// ---- Then ---- //
			verify(dao, never()).closeOpenByRecordHash(any(), any());
			verify(dao, never()).save(any(HistoricItSystemAssignment.class));
		}
	}

	@Nested
	@DisplayName("recordSystemRoleAssignmentRemoved")
	class RecordSystemRoleAssignmentRemoved {

		@Test
		@DisplayName("closes the record by the correct hash with a non-null timestamp")
		void closesCorrectRecordByHash() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentRemoved(userRole, assignment);

			verify(dao).closeOpenByRecordHash(any(String.class), any());
		}

		@Test
		@DisplayName("the hash used to close matches the hash that would be created for the same assignment")
		void closedHashMatchesExpectedHash() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			service.recordSystemRoleAssignmentAdded(userRole, assignment);
			String expectedHash = captureSavedRecords().getFirst().getRecordHash();

			service.recordSystemRoleAssignmentRemoved(userRole, assignment);

			ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
			verify(dao).closeOpenByRecordHash(hashCaptor.capture(), any());

			assertThat(hashCaptor.getValue()).isEqualTo(expectedHash);
		}

		@Test
		@DisplayName("with a responsible collection, closes both the current hash and the legacy hash without collection id")
		void closesBothHashVariantsWhenCollectionExists() {
			// Dækker deployment-vinduet: åbne rækker skrevet før collectionen fandtes (eller før
			// fixet af flag-gatingen) står med legacy-hashen og skal stadig kunne lukkes af et
			// fjern-event, indtil reparations-tasken har været forbi.
			itSystem.addAttestationResponsible(createUser("uuid-a"));
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("uuid-a"))));
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			String currentHash = service.computeRecordHash(userRole, assignment);

			service.recordSystemRoleAssignmentRemoved(userRole, assignment);

			ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
			verify(dao, Mockito.times(2)).closeOpenByRecordHash(hashCaptor.capture(), any());
			assertThat(hashCaptor.getAllValues()).contains(currentHash);
			assertThat(hashCaptor.getAllValues()).doesNotHaveDuplicates();
		}
	}

	@Nested
	@DisplayName("computeRecordHashVariants")
	class ComputeRecordHashVariants {

		@Test
		@DisplayName("returns current and legacy hash when the IT system has a responsible collection")
		void returnsBothVariantsWithCollection() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.empty());
			String legacyHash = service.computeRecordHash(userRole, assignment);

			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("uuid-a"))));
			String currentHash = service.computeRecordHash(userRole, assignment);

			List<String> variants = service.computeRecordHashVariants(userRole, assignment);

			assertThat(variants).containsExactly(currentHash, legacyHash);
		}

		@Test
		@DisplayName("returns a single hash when the IT system has no responsible collection")
		void returnsSingleVariantWithoutCollection() {
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(anyLong()))
				.willReturn(Optional.empty());

			List<String> variants = service.computeRecordHashVariants(userRole, assignment);

			assertThat(variants).containsExactly(service.computeRecordHash(userRole, assignment));
		}
	}

	@Nested
	@DisplayName("seedHistoricRowFromSystemRoleAssignmentId")
	class SeedHistoricRowFromSystemRoleAssignmentId {

		@Test
		@DisplayName("loads SRA inside the tx and persists when missing")
		void loadsAndPersists() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setUserRole(userRole);
			when(systemRoleAssignmentDao.findById(7L)).thenReturn(java.util.Optional.of(assignment));
			when(dao.existsByRecordHashAndValidToIsNull(any())).thenReturn(false);

			// ---- When ---- //
			boolean result = service.seedHistoricRowFromSystemRoleAssignmentId(7L);

			// ---- Then ---- //
			assertThat(result).isTrue();
			verify(dao).save(any(HistoricItSystemAssignment.class));
		}

		@Test
		@DisplayName("returns false and skips persist when SRA does not exist")
		void skipsWhenSraGone() {
			// ---- Given ---- //
			when(systemRoleAssignmentDao.findById(99L)).thenReturn(java.util.Optional.empty());

			// ---- When ---- //
			boolean result = service.seedHistoricRowFromSystemRoleAssignmentId(99L);

			// ---- Then ---- //
			assertThat(result).isFalse();
			verify(dao, never()).save(any(HistoricItSystemAssignment.class));
		}

		@Test
		@DisplayName("returns false when UserRole has no IT-system")
		void skipsWhenNoItSystem() {
			// ---- Given ---- //
			UserRole roleWithoutItSystem = new UserRole();
			roleWithoutItSystem.setId(50L);
			roleWithoutItSystem.setName("Rolle uden it-system");
			roleWithoutItSystem.setItSystem(null);
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setUserRole(roleWithoutItSystem);
			when(systemRoleAssignmentDao.findById(8L)).thenReturn(java.util.Optional.of(assignment));

			// ---- When ---- //
			boolean result = service.seedHistoricRowFromSystemRoleAssignmentId(8L);

			// ---- Then ---- //
			assertThat(result).isFalse();
			verify(dao, never()).save(any(HistoricItSystemAssignment.class));
		}
	}

	@Nested
	@DisplayName("repairResponsibleCollectionRow")
	class RepairResponsibleCollectionRow {

		private static final long COLLECTION_ID = 42L;

		/** Åben historic-række hvis recordHash er beregnet med det angivne collection-id. */
		private HistoricItSystemAssignment openRow(Long hashedWithCollectionId, Long storedCollectionId) {
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId()))
				.willReturn(hashedWithCollectionId == null
					? Optional.empty()
					: Optional.of(new AttestationResponsibleCollection(hashedWithCollectionId, itSystem.getId(), List.of("responsible-uuid"))));
			String hash = service.computeRecordHash(userRole, createSystemRoleAssignment(systemRole));

			return HistoricItSystemAssignment.builder()
				.id(1L)
				.recordHash(hash)
				.validFrom(java.time.LocalDateTime.now().minusDays(10))
				.validTo(null)
				.itSystemId(itSystem.getId())
				.userRoleId(userRole.getId())
				.systemRoleId(systemRole.getId())
				.constraints(new java.util.ArrayList<>())
				.responsibleCollectionId(storedCollectionId)
				.build();
		}

		@Test
		@DisplayName("stamps collection id and recomputes hash on a row written without one")
		void repairsRowMissingCollectionId() {
			HistoricItSystemAssignment row = openRow(null, null);
			given(dao.findById(1L)).willReturn(Optional.of(row));
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(COLLECTION_ID, itSystem.getId(), List.of("responsible-uuid"))));
			String expectedHash = service.computeRecordHash(userRole, createSystemRoleAssignment(systemRole));
			given(dao.existsByRecordHashAndValidToIsNull(expectedHash)).willReturn(false);

			boolean changed = service.repairResponsibleCollectionRow(1L);

			assertThat(changed).isTrue();
			assertThat(row.getResponsibleCollectionId()).isEqualTo(COLLECTION_ID);
			assertThat(row.getRecordHash()).isEqualTo(expectedHash);
			assertThat(row.getValidTo()).isNull();
			verify(dao).save(row);
		}

		@Test
		@DisplayName("recomputes a stale hash on a row where collection id was SQL-backfilled without rehash")
		void repairsStaleHashAfterSqlBackfill() {
			// Hash beregnet med NULL, men collection-id efterfølgende sat via SQL-backfill.
			HistoricItSystemAssignment row = openRow(null, COLLECTION_ID);
			given(dao.findById(1L)).willReturn(Optional.of(row));
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(COLLECTION_ID, itSystem.getId(), List.of("responsible-uuid"))));
			String expectedHash = service.computeRecordHash(userRole, createSystemRoleAssignment(systemRole));
			given(dao.existsByRecordHashAndValidToIsNull(expectedHash)).willReturn(false);

			boolean changed = service.repairResponsibleCollectionRow(1L);

			assertThat(changed).isTrue();
			assertThat(row.getRecordHash()).isEqualTo(expectedHash);
			verify(dao).save(row);
		}

		@Test
		@DisplayName("does nothing when the row is already consistent")
		void noopWhenConsistent() {
			HistoricItSystemAssignment row = openRow(COLLECTION_ID, COLLECTION_ID);
			given(dao.findById(1L)).willReturn(Optional.of(row));

			boolean changed = service.repairResponsibleCollectionRow(1L);

			assertThat(changed).isFalse();
			assertThat(row.getResponsibleCollectionId()).isEqualTo(COLLECTION_ID);
			verify(dao, never()).save(any(HistoricItSystemAssignment.class));
		}

		@Test
		@DisplayName("closes the row as a duplicate when another open row already has the target hash")
		void closesDuplicateWhenTargetHashExists() {
			HistoricItSystemAssignment row = openRow(null, null);
			given(dao.findById(1L)).willReturn(Optional.of(row));
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId()))
				.willReturn(Optional.of(new AttestationResponsibleCollection(COLLECTION_ID, itSystem.getId(), List.of("responsible-uuid"))));
			String originalHash = row.getRecordHash();
			given(dao.existsByRecordHashAndValidToIsNull(any())).willReturn(true);

			boolean changed = service.repairResponsibleCollectionRow(1L);

			assertThat(changed).isTrue();
			assertThat(row.getValidTo()).isNotNull();
			assertThat(row.getResponsibleCollectionId()).isNull();
			assertThat(row.getRecordHash()).isEqualTo(originalHash);
			verify(dao).save(row);
		}

		@Test
		@DisplayName("does nothing for closed or missing rows")
		void noopForClosedOrMissingRows() {
			HistoricItSystemAssignment closedRow = openRow(null, null);
			closedRow.setValidTo(java.time.LocalDateTime.now().minusDays(1));
			given(dao.findById(1L)).willReturn(Optional.of(closedRow));
			given(dao.findById(2L)).willReturn(Optional.empty());

			assertThat(service.repairResponsibleCollectionRow(1L)).isFalse();
			assertThat(service.repairResponsibleCollectionRow(2L)).isFalse();
			verify(dao, never()).save(any(HistoricItSystemAssignment.class));
		}
	}

	@Nested
	@DisplayName("repairResponsibleCollectionForItSystem")
	class RepairResponsibleCollectionForItSystem {

		@Test
		@DisplayName("repairs every open row for the IT system and returns the number of changed rows")
		void repairsAllOpenRowsForSystem() {
			AttestationResponsibleCollection collection = new AttestationResponsibleCollection(42L, itSystem.getId(), List.of("responsible-uuid"));

			// Række skrevet uden collection-id (hash beregnet med null).
			given(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId())).willReturn(Optional.empty());
			String nullHash = service.computeRecordHash(userRole, createSystemRoleAssignment(systemRole));
			HistoricItSystemAssignment staleRow = HistoricItSystemAssignment.builder()
				.id(1L)
				.recordHash(nullHash)
				.validFrom(java.time.LocalDateTime.now().minusDays(10))
				.itSystemId(itSystem.getId())
				.userRoleId(userRole.getId())
				.systemRoleId(systemRole.getId())
				.constraints(new java.util.ArrayList<>())
				.build();

			given(attestationResponsibleCollectionDao.findFirstByItSystemId(itSystem.getId())).willReturn(Optional.of(collection));
			String expectedHash = service.computeRecordHash(userRole, createSystemRoleAssignment(systemRole));
			// Allerede konsistent række.
			HistoricItSystemAssignment consistentRow = HistoricItSystemAssignment.builder()
				.id(2L)
				.recordHash(expectedHash)
				.validFrom(java.time.LocalDateTime.now().minusDays(10))
				.itSystemId(itSystem.getId())
				.userRoleId(userRole.getId())
				.systemRoleId(systemRole.getId())
				.constraints(new java.util.ArrayList<>())
				.responsibleCollectionId(42L)
				.build();

			given(dao.findByItSystemIdAndValidToIsNull(itSystem.getId())).willReturn(List.of(staleRow, consistentRow));
			given(dao.existsByRecordHashAndValidToIsNull(expectedHash)).willReturn(false);

			int changed = service.repairResponsibleCollectionForItSystem(itSystem.getId());

			assertThat(changed).isEqualTo(1);
			assertThat(staleRow.getResponsibleCollectionId()).isEqualTo(42L);
			assertThat(staleRow.getRecordHash()).isEqualTo(expectedHash);
			verify(dao).save(staleRow);
			verify(dao, never()).save(consistentRow);
		}
	}
}
