package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignmentPostponedConstraint;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.Constraint;
import dk.digitalidentity.rc.service.model.PrivilegeGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Regressionstest for at en udskudt afgrænsnings værdi blev konkateneret én
 * gang per system-rolle, der delte afgrænsningstypen på samme bruger-rolle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServicePostponedBppTest {

	private static final String POSTPONED_VALUE = "value-1";

	@Mock
	private AssignmentService assignmentService;

	@Spy
	private RoleCatalogueConfiguration configuration = new RoleCatalogueConfiguration();

	@InjectMocks
	private UserService userService;

	private User user;
	private ItSystem kombitItSystem;
	private ConstraintType postponedConstraintType;

	@BeforeEach
	void setUp() {
		configuration.getCustomer().setCvr("12345678");
		configuration.getIntegrations().getKombit().setDomain("http://example.test/");

		user = new User();
		user.setUuid("user-uuid");
		user.setUserId("testuser");

		kombitItSystem = new ItSystem();
		kombitItSystem.setId(100L);
		kombitItSystem.setName("Demo IT-system");
		kombitItSystem.setIdentifier("demo-it-system");
		kombitItSystem.setSystemType(ItSystemType.KOMBIT);
		kombitItSystem.setAccessBlocked(false);

		postponedConstraintType = new ConstraintType();
		postponedConstraintType.setId(7L);
		postponedConstraintType.setUuid("postponed-type-uuid");
		postponedConstraintType.setName("Postponed type");
		postponedConstraintType.setEntityId("http://example.test/postponed-type/1/parametric");
	}

	@Test
	@DisplayName("POSTPONED-værdi må kun udskrives én gang per system-rolle, ikke gentages for hver søsterrolle")
	void postponedConstraintValueIsNotDuplicatedAcrossSystemRoles() {
		SystemRoleAssignment first = systemRoleAssignmentWithPostponed(1L, "system-role-a");
		SystemRoleAssignment second = systemRoleAssignmentWithPostponed(2L, "system-role-b");
		SystemRoleAssignment third = systemRoleAssignmentWithPostponed(3L, "system-role-c");

		UserRole userRole = new UserRole();
		userRole.setId(50L);
		userRole.setIdentifier("demo-user-role");
		userRole.setName("Demo user role");
		userRole.setItSystem(kombitItSystem);
		userRole.setAllowPostponing(true);
		userRole.setSystemRoleAssignments(List.of(first, second, third));

		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setItSystem(kombitItSystem);
		assignment.setPostponedConstraints(new HashSet<>(Set.of(
				postponedConstraintRow(first.getSystemRole().getId(), POSTPONED_VALUE),
				postponedConstraintRow(second.getSystemRole().getId(), POSTPONED_VALUE),
				postponedConstraintRow(third.getSystemRole().getId(), POSTPONED_VALUE))));

		when(assignmentService.getByUserAndItSystems(any(User.class), anyList()))
				.thenReturn(Set.of(assignment));

		List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

		assertThat(result).hasSize(1);
		List<Constraint> constraints = result.getFirst().getConstraints();
		assertThat(constraints)
				.as("hver system-rolle skal bidrage med præcis sin egen værdi — ikke gentaget for søsterroller")
				.hasSize(3)
				.allSatisfy(c -> assertThat(c.getValue()).isEqualTo(POSTPONED_VALUE));
	}

	@Test
	@DisplayName("POSTPONED-værdi udskrives stadig korrekt når kun én system-rolle har afgrænsningen")
	void postponedConstraintValueIsEmittedForSingleSystemRole() {
		SystemRoleAssignment onlyOne = systemRoleAssignmentWithPostponed(11L, "system-role-x");

		UserRole userRole = new UserRole();
		userRole.setId(51L);
		userRole.setIdentifier("single-postponed-role");
		userRole.setName("Single postponed role");
		userRole.setItSystem(kombitItSystem);
		userRole.setAllowPostponing(true);
		userRole.setSystemRoleAssignments(List.of(onlyOne));

		CurrentAssignment assignment = new CurrentAssignment();
		assignment.setUser(user);
		assignment.setUserRole(userRole);
		assignment.setItSystem(kombitItSystem);
		assignment.setPostponedConstraints(new HashSet<>(Set.of(
				postponedConstraintRow(onlyOne.getSystemRole().getId(), POSTPONED_VALUE))));

		when(assignmentService.getByUserAndItSystems(any(User.class), anyList()))
				.thenReturn(Set.of(assignment));

		List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

		assertThat(result).hasSize(1);
		assertThat(result.getFirst().getConstraints())
				.singleElement()
				.satisfies(c -> assertThat(c.getValue()).isEqualTo(POSTPONED_VALUE));
	}

	private SystemRoleAssignment systemRoleAssignmentWithPostponed(long systemRoleId, String identifier) {
		SystemRole systemRole = new SystemRole();
		systemRole.setId(systemRoleId);
		systemRole.setUuid("sr-uuid-" + systemRoleId);
		systemRole.setName(identifier);
		systemRole.setIdentifier(identifier);

		SystemRoleAssignmentConstraintValue constraint = new SystemRoleAssignmentConstraintValue();
		constraint.setId(900L + systemRoleId);
		constraint.setConstraintType(postponedConstraintType);
		constraint.setConstraintValueType(ConstraintValueType.POSTPONED);
		constraint.setPostponed(true);
		constraint.setConstraintIdentifier("http://example.test/id-" + systemRoleId + "-x-" + postponedConstraintType.getId() + "/1/parametric");

		SystemRoleAssignment sra = new SystemRoleAssignment();
		sra.setId(800L + systemRoleId);
		sra.setSystemRole(systemRole);
		List<SystemRoleAssignmentConstraintValue> values = new ArrayList<>();
		values.add(constraint);
		sra.setConstraintValues(values);
		return sra;
	}

	private CurrentAssignmentPostponedConstraint postponedConstraintRow(long systemRoleId, String value) {
		CurrentAssignmentPostponedConstraint pc = new CurrentAssignmentPostponedConstraint();
		pc.setId(700L + systemRoleId);
		pc.setConstraintTypeId(postponedConstraintType.getId());
		pc.setConstraintTypeUuid(postponedConstraintType.getUuid());
		pc.setConstraintTypeName(postponedConstraintType.getName());
		pc.setConstraintTypeEntityId(postponedConstraintType.getEntityId());
		pc.setSystemRoleId(systemRoleId);
		pc.setValue(List.of(value));
		return pc;
	}
}
