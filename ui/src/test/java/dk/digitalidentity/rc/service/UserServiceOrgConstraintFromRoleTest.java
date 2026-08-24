package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.ConstraintType;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.dao.model.UserOUFunction;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.enums.ConstraintValueType;
import dk.digitalidentity.rc.dao.model.enums.ItSystemType;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import dk.digitalidentity.rc.service.model.Constraint;
import dk.digitalidentity.rc.service.model.PrivilegeGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceOrgConstraintFromRoleTest {

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private OrgUnitService orgUnitService;

    @Spy
    private RoleCatalogueConfiguration configuration = new RoleCatalogueConfiguration();

    @InjectMocks
    private UserService userService;

    private User user;
    private ItSystem kombitItSystem;
    private ConstraintType ouConstraintType;

    @BeforeEach
    void setUp() {
        configuration.getCustomer().setCvr("12345678");
        configuration.getIntegrations().getKombit().setDomain("http://example.test/");

        user = new User();
        user.setUuid("user-uuid");
        user.setUserId("testuser");
        user.setManagedOrgUnits(new ArrayList<>());
        user.setSubstituteFor(new ArrayList<>());
        user.setFunctionAssignments(new ArrayList<>());

        kombitItSystem = new ItSystem();
        kombitItSystem.setId(100L);
        kombitItSystem.setName("Test IT-system");
        kombitItSystem.setIdentifier("test-it-system");
        kombitItSystem.setSystemType(ItSystemType.KOMBIT);
        kombitItSystem.setAccessBlocked(false);

        ouConstraintType = new ConstraintType();
        ouConstraintType.setId(1L);
        ouConstraintType.setUuid("ou-constraint-uuid");
        ouConstraintType.setName("Organisation");
        ouConstraintType.setEntityId(Constants.OU_CONSTRAINT_ENTITY_ID);
    }

    @Nested
    @DisplayName("INHERITED_FROM_MANAGER_ROLE")
    class InheritedFromManagerRole {

        @Test
        @DisplayName("skal inkludere den enhed brugeren er leder for")
        void shouldIncludeManagedOrgUnit() {
            // Arrange
            OrgUnit managedOu = orgUnit("ou-a", "Enhed A");
            managedOu.setChildren(new ArrayList<>());
            user.getManagedOrgUnits().add(managedOu);

            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.INHERITED_FROM_MANAGER_ROLE);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactly("ou-a");
        }

        @Test
        @DisplayName("skal inkludere den enhed brugeren er stedfortræder for")
        void shouldIncludeSubstituteOrgUnit() {
            // Arrange
            OrgUnit substituteOu = orgUnit("ou-b", "Enhed B");
            substituteOu.setChildren(new ArrayList<>());
            user.getSubstituteFor().add(managerSubstitute(substituteOu));

            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.INHERITED_FROM_MANAGER_ROLE);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactly("ou-b");
        }

        @Test
        @DisplayName("skal inkludere både leder- og stedfortræderenheder")
        void shouldIncludeBothManagedAndSubstituteOrgUnits() {
            // Arrange
            OrgUnit managedOu = orgUnit("ou-a", "Enhed A");
            managedOu.setChildren(new ArrayList<>());
            user.getManagedOrgUnits().add(managedOu);

            OrgUnit substituteOu = orgUnit("ou-b", "Enhed B");
            substituteOu.setChildren(new ArrayList<>());
            user.getSubstituteFor().add(managerSubstitute(substituteOu));

            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.INHERITED_FROM_MANAGER_ROLE);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactlyInAnyOrder("ou-a", "ou-b");
        }

        @Test
        @DisplayName("skal ikke producere en afgrænsningsværdi når brugeren ingen lederroller har")
        void shouldProduceNoConstraintValueWhenUserHasNoManagerRoles() {
            // Arrange — user has no managed OUs and no substitute records
            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.INHERITED_FROM_MANAGER_ROLE);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert — the privilege group is still emitted but with no constraints (broken constraint is skipped via continue)
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getConstraints()).isEmpty();
        }
    }

    @Nested
    @DisplayName("EXTENDED_INHERITED_FROM_MANAGER_ROLE")
    class ExtendedInheritedFromManagerRole {

        @Test
        @DisplayName("skal inkludere lederenhed og dens aktive børneenheder rekursivt")
        void shouldIncludeManagedOrgUnitAndActiveChildren() {
            // Arrange
            OrgUnit child = orgUnit("ou-c", "Børneenhed C");
            child.setChildren(new ArrayList<>());

            OrgUnit managedOu = orgUnit("ou-a", "Enhed A");
            managedOu.setChildren(List.of(child));

            user.getManagedOrgUnits().add(managedOu);

            when(orgUnitService.isActiveAndIncluded(child)).thenReturn(true);

            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.EXTENDED_INHERITED_FROM_MANAGER_ROLE);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactlyInAnyOrder("ou-a", "ou-c");
        }

        @Test
        @DisplayName("skal ikke inkludere inaktive børneenheder")
        void shouldNotIncludeInactiveChildren() {
            // Arrange
            OrgUnit inactiveChild = orgUnit("ou-inactive", "Inaktiv enhed");
            inactiveChild.setChildren(new ArrayList<>());

            OrgUnit managedOu = orgUnit("ou-a", "Enhed A");
            managedOu.setChildren(List.of(inactiveChild));

            user.getManagedOrgUnits().add(managedOu);

            when(orgUnitService.isActiveAndIncluded(inactiveChild)).thenReturn(false);

            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.EXTENDED_INHERITED_FROM_MANAGER_ROLE);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactly("ou-a");
        }
    }

    @Nested
    @DisplayName("INHERITED_FROM_FUNCTIONS")
    class InheritedFromFunctions {

        @Test
        @DisplayName("skal inkludere den enhed brugeren har en tillidsfunktion i")
        void shouldIncludeFunctionOrgUnit() {
            // Arrange
            OrgUnit functionOu = orgUnit("ou-d", "Enhed D");
            functionOu.setChildren(new ArrayList<>());
            user.getFunctionAssignments().add(userOuFunction(functionOu, "fn-uuid-a"));

            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.INHERITED_FROM_FUNCTIONS, "fn-uuid-a");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactly("ou-d");
        }

        @Test
        @DisplayName("skal filtrere til kun den valgte tillidsfunktion")
        void shouldFilterToSelectedFunction() {
            // Arrange — user has two functions in different OUs
            OrgUnit ouA = orgUnit("ou-a", "Enhed A");
            ouA.setChildren(new ArrayList<>());
            OrgUnit ouB = orgUnit("ou-b", "Enhed B");
            ouB.setChildren(new ArrayList<>());
            user.getFunctionAssignments().add(userOuFunction(ouA, "fn-tillidsrep"));
            user.getFunctionAssignments().add(userOuFunction(ouB, "fn-arbejdsmiljoe"));

            // Constraint only allows fn-tillidsrep
            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.INHERITED_FROM_FUNCTIONS, "fn-tillidsrep");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert — only ouA where user holds fn-tillidsrep
            assertThat(constraintValuesFrom(result)).containsExactly("ou-a");
        }

        @Test
        @DisplayName("skal returnere ingen enheder når ingen valgte tillidsfunktioner matcher")
        void shouldReturnNoOusWhenNoFunctionMatches() {
            // Arrange — user has function fn-other, constraint requires fn-required
            OrgUnit ou = orgUnit("ou-d", "Enhed D");
            ou.setChildren(new ArrayList<>());
            user.getFunctionAssignments().add(userOuFunction(ou, "fn-other"));

            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.INHERITED_FROM_FUNCTIONS, "fn-required");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getConstraints()).isEmpty();
        }

        @Test
        @DisplayName("skal returnere ingen enheder når constraintValue er tom (ingen valgte funktioner)")
        void shouldReturnNoOusWhenConstraintValueIsEmpty() {
            // Arrange — user has a function assignment but no function filter is configured
            OrgUnit ou = orgUnit("ou-d", "Enhed D");
            ou.setChildren(new ArrayList<>());
            user.getFunctionAssignments().add(userOuFunction(ou, "fn-uuid-a"));

            CurrentAssignment assignment = assignmentWithConstraint(ConstraintValueType.INHERITED_FROM_FUNCTIONS);
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert — empty constraintValue = no OUs (require explicit selection)
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getConstraints()).isEmpty();
        }

        @Test
        @DisplayName("skal deduplikere enheder når brugeren har flere matchende funktioner i samme enhed")
        void shouldDeduplicateWhenMultipleFunctionsInSameOrgUnit() {
            // Arrange
            OrgUnit ou = orgUnit("ou-d", "Enhed D");
            ou.setChildren(new ArrayList<>());
            user.getFunctionAssignments().add(userOuFunction(ou, "fn-uuid-a"));
            user.getFunctionAssignments().add(userOuFunction(ou, "fn-uuid-a"));

            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.INHERITED_FROM_FUNCTIONS, "fn-uuid-a");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactly("ou-d");
        }

        @Test
        @DisplayName("skal ikke producere en afgrænsningsværdi når brugeren ingen tillidsfunktioner har")
        void shouldProduceNoConstraintValueWhenUserHasNoFunctions() {
            // Arrange — user has no function assignments
            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.INHERITED_FROM_FUNCTIONS, "fn-uuid-a");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert — the privilege group is still emitted but with no constraints (broken constraint is skipped via continue)
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getConstraints()).isEmpty();
        }
    }

    @Nested
    @DisplayName("EXTENDED_INHERITED_FROM_FUNCTIONS")
    class ExtendedInheritedFromFunctions {

        @Test
        @DisplayName("skal inkludere funktionsenhed og dens aktive børneenheder rekursivt")
        void shouldIncludeFunctionOrgUnitAndActiveChildren() {
            // Arrange
            OrgUnit child = orgUnit("ou-e", "Børneenhed E");
            child.setChildren(new ArrayList<>());

            OrgUnit functionOu = orgUnit("ou-d", "Enhed D");
            functionOu.setChildren(List.of(child));
            user.getFunctionAssignments().add(userOuFunction(functionOu, "fn-uuid-a"));

            when(orgUnitService.isActiveAndIncluded(child)).thenReturn(true);

            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.EXTENDED_INHERITED_FROM_FUNCTIONS, "fn-uuid-a");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactlyInAnyOrder("ou-d", "ou-e");
        }

        @Test
        @DisplayName("skal kun inkludere enheder for den valgte tillidsfunktion")
        void shouldOnlyIncludeOusForSelectedFunction() {
            // Arrange
            OrgUnit ouA = orgUnit("ou-a", "Enhed A");
            ouA.setChildren(new ArrayList<>());
            OrgUnit ouB = orgUnit("ou-b", "Enhed B");
            ouB.setChildren(new ArrayList<>());

            user.getFunctionAssignments().add(userOuFunction(ouA, "fn-tillidsrep"));
            user.getFunctionAssignments().add(userOuFunction(ouB, "fn-arbejdsmiljoe"));

            CurrentAssignment assignment = assignmentWithConstraintAndFunctions(ConstraintValueType.EXTENDED_INHERITED_FROM_FUNCTIONS, "fn-tillidsrep");
            when(assignmentService.getByUserAndItSystems(any(User.class), anyList())).thenReturn(Set.of(assignment));

            // Act
            List<PrivilegeGroup> result = userService.generateOIOBPPPrivileges(user, List.of(kombitItSystem), new HashMap<>());

            // Assert
            assertThat(constraintValuesFrom(result)).containsExactly("ou-a");
        }
    }

    // --- helpers ---

    private SystemRoleAssignmentConstraintValue buildConstraintValue(ConstraintValueType type) {
        SystemRoleAssignmentConstraintValue constraint = new SystemRoleAssignmentConstraintValue();
        constraint.setId(1L);
        constraint.setConstraintType(ouConstraintType);
        constraint.setConstraintValueType(type);
        constraint.setConstraintIdentifier(Constants.OU_CONSTRAINT_ENTITY_ID);
        return constraint;
    }

    private CurrentAssignment buildAssignment(SystemRoleAssignmentConstraintValue constraint) {
        SystemRole systemRole = new SystemRole();
        systemRole.setId(1L);
        systemRole.setUuid("sr-uuid");
        systemRole.setName("test-system-role");
        systemRole.setIdentifier("test-system-role");

        SystemRoleAssignment sra = new SystemRoleAssignment();
        sra.setId(1L);
        sra.setSystemRole(systemRole);
        sra.setConstraintValues(List.of(constraint));

        UserRole userRole = new UserRole();
        userRole.setId(1L);
        userRole.setIdentifier("test-user-role");
        userRole.setName("Test user role");
        userRole.setItSystem(kombitItSystem);
        userRole.setAllowPostponing(false);
        userRole.setSystemRoleAssignments(List.of(sra));

        CurrentAssignment assignment = new CurrentAssignment();
        assignment.setUser(user);
        assignment.setUserRole(userRole);
        assignment.setItSystem(kombitItSystem);
        return assignment;
    }

    private OrgUnit orgUnit(String uuid, String name) {
        OrgUnit ou = new OrgUnit();
        ou.setUuid(uuid);
        ou.setName(name);
        return ou;
    }

    private ManagerSubstitute managerSubstitute(OrgUnit ou) {
        ManagerSubstitute sub = new ManagerSubstitute();
        sub.setOrgUnit(ou);
        return sub;
    }

    private UserOUFunction userOuFunction(OrgUnit ou) {
        return userOuFunction(ou, null);
    }

    private UserOUFunction userOuFunction(OrgUnit ou, String functionUuid) {
        UserOUFunction f = new UserOUFunction();
        f.setOrgUnit(ou);
        if (functionUuid != null) {
            Function fn = new Function();
            fn.setUuid(functionUuid);
            fn.setName(functionUuid);
            fn.setActive(true);
            f.setFunction(fn);
        }
        return f;
    }

    private CurrentAssignment assignmentWithConstraint(ConstraintValueType type) {
        return buildAssignment(buildConstraintValue(type));
    }

    private CurrentAssignment assignmentWithConstraintAndFunctions(ConstraintValueType type, String... functionUuids) {
        SystemRoleAssignmentConstraintValue constraint = buildConstraintValue(type);
        constraint.setConstraintValue(String.join(",", functionUuids));
        return buildAssignment(constraint);
    }

    private List<String> constraintValuesFrom(List<PrivilegeGroup> groups) {
        return groups.stream()
                .flatMap(g -> g.getConstraints().stream())
                .map(Constraint::getValue)
                .flatMap(v -> List.of(v.split(",")).stream())
                .toList();
    }
}
