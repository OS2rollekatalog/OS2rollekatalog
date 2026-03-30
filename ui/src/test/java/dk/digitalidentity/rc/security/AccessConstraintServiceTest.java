package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.PostponedConstraint;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignmentConstraintValue;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.PostponedConstraintService;
import dk.digitalidentity.rc.service.assignment.AssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static dk.digitalidentity.rc.mockfactory.security.MockFactory.createConstraint;
import static dk.digitalidentity.rc.mockfactory.security.MockFactory.createITSystemConstraintType;
import static dk.digitalidentity.rc.mockfactory.security.MockFactory.createOUConstraintType;
import static dk.digitalidentity.rc.mockfactory.security.MockFactory.createUserRoleWithSystemRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AccessConstraintServiceTest {

	@Mock
	PostponedConstraintService postponedConstraintService;
	@Mock
	AssignmentService assignmentService;

	@InjectMocks
	private AccessConstraintService accessConstraintService;

	@Nested
	@DisplayName("constructUserPermissions")
	class permission {
		User testUser;
		ItSystem testRoleCatalogue;

		@BeforeEach
		void setUp() {
			testUser = createUser("test-user-uuid");
			testRoleCatalogue = createItSystem();
		}

		@Nested
		@DisplayName("User role")
		class userRole {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				Permission allowedPermission;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_USERROLE_READ_ID;

					allowedSection = Section.USER_ROLE;
					allowedPermission = Permission.READ;
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();

				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.containsExactly(itSystem.getId());
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.containsExactly(orgunit.getUuid());
				}
			}

			@Nested
			@DisplayName("UPDATE")
			class update {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_USERROLE_UPDATE_ID;

					allowedSection = Section.USER_ROLE;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}

			@Nested
			@DisplayName("CREATE")
			class create {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_USERROLE_CREATE_ID;

					allowedSection = Section.USER_ROLE;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}

			@Nested
			@DisplayName("DELETE")
			class delete {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_USERROLE_DELETE_ID;

					allowedSection = Section.USER_ROLE;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}
		}

		@Nested
		@DisplayName("Rolegroup")
		class rolegroup {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				Permission allowedPermission;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ROLEGROUP_READ_ID;

					allowedSection = Section.ROLE_GROUP;
					allowedPermission = Permission.READ;
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();

				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.containsExactly(itSystem.getId());
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.containsExactly(orgunit.getUuid());
				}
			}

			@Nested
			@DisplayName("UPDATE")
			class update {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ROLEGROUP_UPDATE_ID;

					allowedSection = Section.ROLE_GROUP;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}

			@Nested
			@DisplayName("CREATE")
			class create {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ROLEGROUP_CREATE_ID;

					allowedSection = Section.ROLE_GROUP;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}

			@Nested
			@DisplayName("DELETE")
			class delete {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ROLEGROUP_DELETE_ID;

					allowedSection = Section.ROLE_GROUP;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}
		}

		@Nested
		@DisplayName("IT system")
		class itSystem {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				Permission allowedPermission;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ITSYSTEM_READ_ID;

					allowedSection = Section.IT_SYSTEM;
					allowedPermission = Permission.READ;
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();

				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.containsExactly(itSystem.getId());
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();
				}
			}

			@Nested
			@DisplayName("UPDATE")
			class update {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ITSYSTEM_UPDATE_ID;

					allowedSection = Section.IT_SYSTEM;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}

			@Nested
			@DisplayName("CREATE")
			class create {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ITSYSTEM_CREATE_ID;

					allowedSection = Section.IT_SYSTEM;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}

			@Nested
			@DisplayName("DELETE")
			class delete {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ITSYSTEM_DELETE_ID;

					allowedSection = Section.IT_SYSTEM;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}

				@Test
				@DisplayName("IT system constrained")
				void itsystemConstrained() {
					// Arrange
					ItSystem itSystem = createItSystem();
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createITSystemConstraintType(), String.valueOf(itSystem.getId()))
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.containsExactly(itSystem.getId());
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}
		}

		@Nested
		@DisplayName("Orgunit")
		class orgunit {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				Permission allowedPermission;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_OU_READ_ID;

					allowedSection = Section.ORGUNIT;
					allowedPermission = Permission.READ;
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();

				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.containsExactly(orgunit.getUuid());
				}
			}

			@Nested
			@DisplayName("UPDATE")
			class update {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_OU_UPDATE_ID;

					allowedSection = Section.ORGUNIT;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}


				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}
		}

		@Nested
		@DisplayName("User")
		class user {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				Permission allowedPermission;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_USER_READ_ID;

					allowedSection = Section.USER;
					allowedPermission = Permission.READ;
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();

				}

				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.containsExactly(orgunit.getUuid());
				}
			}

			@Nested
			@DisplayName("UPDATE")
			class update {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_USER_UPDATE_ID;

					allowedSection = Section.USER;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}


				@Test
				@DisplayName("Orgunit constrained")
				void orgunitConstrained() {
					// Arrange
					OrgUnit orgunit = createOrgUnit("test-org-unit", null);
					List<SystemRoleAssignmentConstraintValue> constraints = List.of(
						createConstraint(createOUConstraintType(), orgunit.getUuid())
					);
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, constraints));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.containsExactly(orgunit.getUuid());
					}
				}
			}
		}


		@Nested
		@DisplayName("Auditlog")
		class auditlog {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_LOG_READ_ID;

					allowedSection = Section.LOG;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}
		}

		@Nested
		@DisplayName("Advise")
		class advise {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_ADVISE_READ_ID;

					allowedSection = Section.ADVISE;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}
		}

		@Nested
		@DisplayName("Manager")
		class manager {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				Permission allowedPermission;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_MANAGER_READ_ID;

					allowedSection = Section.MANAGER;
					allowedPermission = Permission.READ;
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermission);
					PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
					assertThat(constraint.getConstrainedItSystemIds())
						.isNull();
					assertThat(constraint.getConstrainedOUUuids())
						.isNull();

				}
			}

			@Nested
			@DisplayName("UPDATE")
			class update {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_MANAGER_UPDATE_ID;

					allowedSection = Section.MANAGER;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}
		}

		@Nested
		@DisplayName("Config")
		class config {

			@Nested
			@DisplayName("READ")
			class read {
				Section allowedSection;
				List<Permission> allowedPermissions;
				String testSystemIdentifier;

				@BeforeEach
				void setUp() {
					testSystemIdentifier = Constants.ROLE_CONFIG_READ_ID;

					allowedSection = Section.CONFIG;
					allowedPermissions = List.of(
						Permission.READ,
						Permission.UPDATE,
						Permission.CREATE,
						Permission.DELETE
					);
				}

				@Test
				@DisplayName("Unconstrained")
				void unconstrained() {
					// Arrange
					Set<UserRole> userRoles = Set.of(createUserRoleWithSystemRole(testSystemIdentifier, List.of()));
					when(assignmentService.getUserRolesByUserAndSystems(testUser, List.of(testRoleCatalogue)))
						.thenReturn(userRoles);

					Set<PostponedConstraint> postponedConstraints = new HashSet<>();
					when(postponedConstraintService.findAllForUserAndRoleCatalogue(testUser))
						.thenReturn(postponedConstraints);

					// Act
					Map<Section, Map<Permission, PermissionConstraint>> result = accessConstraintService.constructUserPermissions(testUser, testRoleCatalogue);

					// Assert
					assertThat(result)
						.containsOnlyKeys(allowedSection);
					assertThat(result.get(allowedSection))
						.containsOnlyKeys(allowedPermissions);
					for (Permission allowedPermission : allowedPermissions) {
						PermissionConstraint constraint = result.get(allowedSection).get(allowedPermission);
						assertThat(constraint.getConstrainedItSystemIds())
							.isNull();
						assertThat(constraint.getConstrainedOUUuids())
							.isNull();
					}
				}
			}
		}
	}
}
