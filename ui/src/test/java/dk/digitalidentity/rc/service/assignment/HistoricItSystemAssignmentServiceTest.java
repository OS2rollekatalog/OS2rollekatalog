package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.HistoricItSystemAssignmentDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
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

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createConstraintValue;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRole;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createSystemRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HistoricItSystemAssignmentServiceTest {

	@Mock
	private HistoricItSystemAssignmentDao dao;

	@InjectMocks
	private HistoricItSystemAssignmentService service;

	// ---- Common test data ---- //

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

	// ---- ------------- ---- //

	@Nested
	@DisplayName("recordSystemRoleAssignmentAdded creates a correct record")
	class RecordSystemRoleAssignmentAdded {

		@Test
		@DisplayName("IT system fields are mapped correctly")
		void itSystemFieldsAreMapped() {
			// ---- Given ---- //
			itSystem.setAttestationExempt(true);
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			HistoricItSystemAssignment snapshot = captor.getValue();
			assertThat(snapshot.getItSystemId()).isEqualTo(10L);
			assertThat(snapshot.getItSystemName()).isEqualTo("Test IT System");
			assertThat(snapshot.isItSystemAttestationExempt()).isTrue();
		}

		@Test
		@DisplayName("user role fields are mapped correctly")
		void userRoleFieldsAreMapped() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			HistoricItSystemAssignment snapshot = captor.getValue();
			assertThat(snapshot.getUserRoleId()).isEqualTo(20L);
			assertThat(snapshot.getUserRoleName()).isEqualTo("Test User Role");
			assertThat(snapshot.getUserRoleDescription()).isEqualTo("User role description");
		}

		@Test
		@DisplayName("system role fields are mapped correctly")
		void systemRoleFieldsAreMapped() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			HistoricItSystemAssignment snapshot = captor.getValue();
			assertThat(snapshot.getSystemRoleId()).isEqualTo(30L);
			assertThat(snapshot.getSystemRoleName()).isEqualTo("Test System Role");
			assertThat(snapshot.getSystemRoleDescription()).isEqualTo("Description of Test System Role");
		}

		@Test
		@DisplayName("new record is open (validFrom set, validTo null, recordHash set)")
		void newRecordIsOpen() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			HistoricItSystemAssignment snapshot = captor.getValue();
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

			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			assertThat(captor.getValue().getResponsibleUserUuid()).isEqualTo("responsible-uuid");
		}

		@Test
		@DisplayName("responsibleUserUuid is null when role flag is false, even if itSystem has responsible user")
		void responsibleUserUuidIsNullWhenFlagIsFalse() {
			// ---- Given ---- //
			User responsible = createUser("responsible-uuid");
			userRole.setRoleAssignmentAttestationByAttestationResponsible(false);
			itSystem.setAttestationResponsible(responsible);

			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			assertThat(captor.getValue().getResponsibleUserUuid()).isNull();
		}

		@Test
		@DisplayName("constraints are mapped with correct name, valueType and value")
		void constraintsAreMapped() {
			// ---- Given ---- //
			SystemRoleAssignmentConstraintValue cv = createConstraintValue("It-system", ConstraintValueType.VALUE, "42");
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setConstraintValues(List.of(cv));

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			List<HistoricItSystemAssignmentConstraint> constraints = captor.getValue().getConstraints();
			assertThat(constraints).hasSize(1);
			assertThat(constraints.getFirst().getConstraintName()).isEqualTo("It-system");
			assertThat(constraints.getFirst().getConstraintValueType()).isEqualTo(ConstraintValueType.VALUE);
			assertThat(constraints.getFirst().getConstraintValue()).isEqualTo("42");
		}

		@Test
		@DisplayName("multiple constraints are all mapped")
		void multipleConstraintsAreMapped() {
			// ---- Given ---- //
			SystemRoleAssignmentConstraintValue cv1 = createConstraintValue("It-system", ConstraintValueType.VALUE, "42");
			SystemRoleAssignmentConstraintValue cv2 = createConstraintValue("Enhed", ConstraintValueType.INHERITED, null);
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setConstraintValues(List.of(cv1, cv2));

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			assertThat(captor.getValue().getConstraints())
				.hasSize(2)
				.extracting(HistoricItSystemAssignmentConstraint::getConstraintName)
				.containsExactlyInAnyOrder("It-system", "Enhed");
		}

		@Test
		@DisplayName("no constraints are created when constraintValues is empty")
		void noConstraintsWhenEmpty() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			assertThat(captor.getValue().getConstraints()).isEmpty();
		}

		@Test
		@DisplayName("no constraints are created when constraintValues is null")
		void noConstraintsWhenNull() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);
			assignment.setConstraintValues(null);

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(captor.capture());

			assertThat(captor.getValue().getConstraints()).isEmpty();
		}
	}

	@Nested
	@DisplayName("record hash stability")
	class RecordHashStability {

		@Test
		@DisplayName("same assignment with same constraints produces the same hash")
		void sameAssignmentProducesSameHash() {
			// ---- Given ---- //
			SystemRoleAssignmentConstraintValue cv = createConstraintValue("It-system", ConstraintValueType.VALUE, "42");

			SystemRoleAssignment assignment1 = createSystemRoleAssignment(systemRole);
			assignment1.setConstraintValues(List.of(cv));

			SystemRoleAssignment assignment2 = createSystemRoleAssignment(systemRole);
			assignment2.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "42")));

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment1);
			service.recordSystemRoleAssignmentAdded(userRole, assignment2);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao, Mockito.times(2)).save(captor.capture());

			List<HistoricItSystemAssignment> saved = captor.getAllValues();
			assertThat(saved.get(0).getRecordHash()).isEqualTo(saved.get(1).getRecordHash());
		}

		@Test
		@DisplayName("different constraint value produces a different hash")
		void differentConstraintValueProducesDifferentHash() {
			// ---- Given ---- //
			SystemRoleAssignment assignment1 = createSystemRoleAssignment(systemRole);
			assignment1.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "42")));

			SystemRoleAssignment assignment2 = createSystemRoleAssignment(systemRole);
			assignment2.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "99")));

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment1);
			service.recordSystemRoleAssignmentAdded(userRole, assignment2);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao, Mockito.times(2)).save(captor.capture());

			List<HistoricItSystemAssignment> saved = captor.getAllValues();
			assertThat(saved.get(0).getRecordHash()).isNotEqualTo(saved.get(1).getRecordHash());
		}

		@Test
		@DisplayName("different constraint name produces a different hash")
		void differentConstraintNameProducesDifferentHash() {
			// ---- Given ---- //
			SystemRoleAssignment assignment1 = createSystemRoleAssignment(systemRole);
			assignment1.setConstraintValues(List.of(createConstraintValue("It-system", ConstraintValueType.VALUE, "42")));

			SystemRoleAssignment assignment2 = createSystemRoleAssignment(systemRole);
			assignment2.setConstraintValues(List.of(createConstraintValue("Enhed", ConstraintValueType.VALUE, "42")));

			// ---- When ---- //
			service.recordSystemRoleAssignmentAdded(userRole, assignment1);
			service.recordSystemRoleAssignmentAdded(userRole, assignment2);

			// ---- Then ---- //
			ArgumentCaptor<HistoricItSystemAssignment> captor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao, Mockito.times(2)).save(captor.capture());

			List<HistoricItSystemAssignment> saved = captor.getAllValues();
			assertThat(saved.get(0).getRecordHash()).isNotEqualTo(saved.get(1).getRecordHash());
		}
	}

	@Nested
	@DisplayName("recordSystemRoleAssignmentRemoved")
	class RecordSystemRoleAssignmentRemoved {

		@Test
		@DisplayName("closes the record by the correct hash with a non-null timestamp")
		void closesCorrectRecordByHash() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// ---- When ---- //
			service.recordSystemRoleAssignmentRemoved(userRole, assignment);

			// ---- Then ---- //
			verify(dao).closeOpenByRecordHash(any(String.class), any());
		}

		@Test
		@DisplayName("the hash used to close matches the hash that would be created for the same assignment")
		void closedHashMatchesExpectedHash() {
			// ---- Given ---- //
			SystemRoleAssignment assignment = createSystemRoleAssignment(systemRole);

			// Capture the hash from an add to know what hash remove should close
			service.recordSystemRoleAssignmentAdded(userRole, assignment);
			ArgumentCaptor<HistoricItSystemAssignment> addCaptor = ArgumentCaptor.forClass(HistoricItSystemAssignment.class);
			verify(dao).save(addCaptor.capture());
			String expectedHash = addCaptor.getValue().getRecordHash();

			// ---- When ---- //
			service.recordSystemRoleAssignmentRemoved(userRole, assignment);

			// ---- Then ---- //
			ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
			verify(dao).closeOpenByRecordHash(hashCaptor.capture(), any());

			assertThat(hashCaptor.getValue()).isEqualTo(expectedHash);
		}
	}
}
