package dk.digitalidentity.rc.test.integration.controller;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import dk.digitalidentity.rc.service.model.RoleAssignedToUserDTO;
import dk.digitalidentity.rc.service.model.RoleAssignmentType;
import dk.digitalidentity.rc.test.integration.setup.BaseIntegrationTest;
import dk.digitalidentity.rc.test.integration.setup.BasicTestDataFactory;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserControllerTest extends BaseIntegrationTest {

	private final BasicTestDataFactory testDataFactory;

	private static final String LOGGED_IN_USER_UUID = "logged-in-user-uuid";
	private static final String LOGGED_IN_USER_ID = "logged-in-user-id";

	private BasicTestDataFactory.BasicTestData testData;
	private User loggedInUser;

	@BeforeEach
	void setUp() {
		testData = testDataFactory.createBasicTestData();
		loggedInUser = testDataFactory.createUser(LOGGED_IN_USER_UUID, LOGGED_IN_USER_ID, "test user name", testData.itSystem().getDomain());
		grantCRUDAccess(LOGGED_IN_USER_UUID, Section.USER);

		flushAndClear();
	}


	@Nested
	@DisplayName("test of user assignment fragment (getAssignedRolesFragment)")
	class GetAssignedRolesFragment {
		static final String assignmentsModelName = "assignments";
		static final Class<?> assignmentDTOClass = RoleAssignedToUserDTO.class;


		@Test
		@DisplayName("Should redirect user list to on non-existing user")
		void shouldRedirectOnInvalidUser() throws Exception {
			// Arrange
			String userUuid = "non-existing-user";

			// Act
			mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("../list"));
		}

		@Test
		@DisplayName("Should contain valid assignment dto's")
		void shouldContainAssignments() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();

			assertNotNull(mav); // model is not null

			assertThat(mav.getModel())
					.as("Should return assignments in model")
					.containsKey(assignmentsModelName);

			List<?> assignments = (List<?>) mav.getModel().get(assignmentsModelName);

			assertThat(assignments)
					.as("assignments should not be empty")
					.isNotEmpty();

			assertThat(assignments.getFirst())
					.as("Contents of assignments should be correct class")
					.isInstanceOf(assignmentDTOClass);

		}

		@Test
		@DisplayName("Should contain direct UserRole assignments")
		void shouldContainDirectAssignments() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<Long> directAssignments = assignments.stream()
					.filter(a -> AssignedThrough.DIRECT.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.USERROLE.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			assertThat(directAssignments).containsExactlyInAnyOrder(testData.urDirectlyAssigned().getId());
		}

		@Test
		@DisplayName("Should contain UserRole assignments inherited from OUs and RoleGroups")
		void shouldContainUserRoleInheritedAssignments() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<Long> orgUnitAssignments = assignments.stream()
					.filter(a -> AssignedThrough.ORGUNIT.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.USERROLE.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			// Should NOT contain roles from parent orgunit that is set to not inherit
			assertThat(orgUnitAssignments).containsExactlyInAnyOrder(
					testData.urViaChildOU().getId(),
					testData.urViaParentOUInherited().getId()
			);

			// userRoler nedarvet via en rollebuket vises som ROLEGROUP, ikke ORGUNIT,
			// så Tildeling-kolonnen viser hvilken buket de stammer fra
			List<Long> roleGroupAssignments = assignments.stream()
					.filter(a -> AssignedThrough.ROLEGROUP.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.USERROLE.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			assertThat(roleGroupAssignments).contains(
					testData.urViaRgViaChildOU().getId(),
					testData.urViaRgViaParentOUInherited().getId()
			);
		}

		@Test
		@DisplayName("Should contain direct RoleGroup assignments")
		void shouldContainDirectRoleGroupAssignments() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<Long> directAssignments = assignments.stream()
					.filter(a -> AssignedThrough.DIRECT.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.ROLEGROUP.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			assertThat(directAssignments).containsExactlyInAnyOrder(testData.rgDirectlyAssigned().getId());
		}

		@Test
		@DisplayName("Should contain RoleGroup assignments inherited from OUs")
		void shouldContainRoleGroupInheritedAssignments() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<Long> directAssignments = assignments.stream()
					.filter(a -> AssignedThrough.DIRECT.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.ROLEGROUP.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			// Should NOT contain roles from parent orgunit that is set to not inherit
			assertThat(directAssignments).containsExactlyInAnyOrder(
					testData.rgDirectlyAssigned().getId()
			);
		}

		@Test
		@DisplayName("Should contain negative UserRole assignments from excepted Title in OUs")
		void shouldContainNegativeTitleAssignment() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<Long> directAssignments = assignments.stream()
					.filter(a -> AssignedThrough.TITLE.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.NEGATIVE.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			// Should NOT contain roles from parent orgunit that is set to not inherit
			assertThat(directAssignments).containsExactlyInAnyOrder(
					testData.urNegativeViaChildOU().getId()
			);
		}


		@Test
		@DisplayName("Should contain negative RoleGroup assignments from excepted Title in OUs")
		void shouldContainNegativeRoleGroupTitleAssignment() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<Long> directAssignments = assignments.stream()
					.filter(a -> AssignedThrough.TITLE.equals(a.getAssignedThrough()))
					.filter(a -> RoleAssignmentType.NEGATIVE_ROLEGROUP.equals(a.getType()))
					.map(RoleAssignedToUserDTO::getRoleId)
					.toList();

			// Should NOT contain roles from parent orgunit that is set to not inherit
			assertThat(directAssignments).containsExactlyInAnyOrder(
					testData.rgNegativeViaChildOU().getId()
			);
		}

		@Test
		@DisplayName("only direct assignments should be editable")
		void shouldHaveNoEditableIndirectAssignments() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Grant the assign permission required
			grantAssigningAccess(LOGGED_IN_USER_UUID, Section.USER);

			List<Long> allowedIds = List.of(
					testData.urDirectlyAssigned().getId(),
					testData.rgDirectlyAssigned().getId()
			);

			flushAndClear();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<RoleAssignedToUserDTO> editableAssignments = assignments.stream()
					.filter(RoleAssignedToUserDTO::isCanEdit)
					.toList();

			assertThat(editableAssignments.stream()
					.filter(a -> AssignedThrough.DIRECT.equals(a.getAssignedThrough())).toList())
					.as("Only direct assignments should be editable, and the only editable assignments should be direct")
					.hasSameSizeAs(editableAssignments);

			assertThat(editableAssignments.stream()
					.map(RoleAssignedToUserDTO::getRoleId))
					.as("Only the directly assigned roles should be editable")
					.containsExactlyInAnyOrderElementsOf(allowedIds);
		}

		@Test
		@DisplayName("internal RC roles should not be editable by user without admin role")
		void internalRoleNotEditableWithoutAdminRole() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();
			UserRole roleCatalogueRole = testDataFactory.assignUserRoleToUser("administrator", testData.user());
			testDataFactory.updateUserAssignmentCalculation(testData.user());

			// Grant the assign permission required
			grantPermission(LOGGED_IN_USER_UUID, Section.USER, Permission.ASSIGN);

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
							.with(mockLogin(loggedInUser, List.of())))
					.andReturn();

			flushAndClear();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<RoleAssignedToUserDTO> roleCatalogueAssignments = assignments.stream()
					.filter(a -> a.getRoleId() == roleCatalogueRole.getId())
					.toList();

			assertThat(roleCatalogueAssignments.stream()
					.map(RoleAssignedToUserDTO::getRoleId).toList())
					.as("Assignments should not contain more internal roles than was assigned")
					.containsExactlyInAnyOrder(roleCatalogueRole.getId());

			assertThat(roleCatalogueAssignments.getFirst().isCanEdit())
					.as("Internal role should not be editable without direct admin role")
					.isFalse();
		}


		@Test
		@DisplayName("internal RC roles should  be editable by user with admin role")
		void internalRoleEditableWithAdminRole() throws Exception {
			// Arrange
			String userUuid = testData.user().getUuid();

			// Add internal role to the viewed users roles
			UserRole roleCatalogueRole = testDataFactory.assignUserRoleToUser("administrator", testData.user());
			testDataFactory.updateUserAssignmentCalculation(testData.user());

			// Grant the assign permission required
			grantAssigningAccess(LOGGED_IN_USER_UUID, Section.USER);

			flushAndClear();

			// Act
			MvcResult result = mockMvc.perform(get("/ui/users/manage/{uuid}/roles", userUuid)
					.with(mockLogin(loggedInUser, List.of(Constants.ROLE_ADMINISTRATOR)))) // Direct admin role
					.andReturn();

			// Assert
			ModelAndView mav = result.getModelAndView();
			List<RoleAssignedToUserDTO> assignments = (List<RoleAssignedToUserDTO>) mav.getModel().get(assignmentsModelName);


			List<RoleAssignedToUserDTO> roleCatalogueAssignments = assignments.stream()
					.filter(a -> a.getRoleId() == roleCatalogueRole.getId())
					.toList();

			assertThat(roleCatalogueAssignments.stream()
					.map(RoleAssignedToUserDTO::getRoleId).toList())
					.as("Assignments should not contain more internal roles than was assigned")
					.containsExactlyInAnyOrder(roleCatalogueRole.getId());

			assertThat(roleCatalogueAssignments.getFirst().isCanEdit())
					.as("Internal role should be editable with direct admin role")
					.isTrue();
		}
	}
}
