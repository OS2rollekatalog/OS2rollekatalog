package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.enums.ApprovableBy;
import dk.digitalidentity.rc.service.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createRoleGroup;
import static dk.digitalidentity.rc.mockfactory.rolerequest.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApproverOptionServiceTest {
	@Mock
	SettingsService settingsService;

	@InjectMocks
	private ApproverOptionService approverOptionService;

	@Nested
	@DisplayName("Possible to get the correct Approver Option for a User Role")
	class CanGetApproverOptionFromUserRole {

		@Test
		@DisplayName("should use the roles' options, if not INHERITED")
		void usesRoleOptions() {
			// Arrange
			List<ApprovableBy> roleOptions = List.of();
			List<ApprovableBy> systemOptions = List.of(ApprovableBy.AUTHORIZED);

			ItSystem system = createItSystem("test-it-system", systemOptions);
			UserRole userRole = createUserRole("test-user-role", system, roleOptions);

			// Act
			List<ApprovableBy> result = approverOptionService.getInheritedApproverOption(userRole);

			// Assert
			assertThat(result)
				.containsExactlyElementsOf(roleOptions);
		}

		@Test
		@DisplayName("should use the systems options, role is INHERITED")
		void usesSystemOptions() {
			// Arrange
			List<ApprovableBy> roleOptions = List.of();
			List<ApprovableBy> systemOptions = List.of();

			ItSystem system = createItSystem("test-it-system", systemOptions);
			UserRole userRole = createUserRole("test-user-role", system, roleOptions);

			// Act
			List<ApprovableBy> result = approverOptionService.getInheritedApproverOption(userRole);

			// Assert
			assertThat(result)
				.containsExactlyElementsOf(systemOptions);
		}

		@Test
		@DisplayName("should use the global options, if both role and system is INHERITED")
		void usesGlobalOptions() {
			// Arrange
			List<ApprovableBy> roleOptions = List.of(ApprovableBy.INHERIT);
			List<ApprovableBy> systemOptions = List.of(ApprovableBy.INHERIT);
			List<ApprovableBy> globalOptions = List.of();

			ItSystem system = createItSystem("test-it-system", systemOptions);
			UserRole userRole = createUserRole("test-user-role", system, roleOptions);
			when(settingsService.getRolerequestApprover()).thenReturn(globalOptions);

			// Act
			List<ApprovableBy> result = approverOptionService.getInheritedApproverOption(userRole);

			// Assert
			assertThat(result)
				.containsExactlyElementsOf(globalOptions);
		}
	}

	@Nested
	@DisplayName("Possible to get the correct Approver Option for a Role Group")
	class CanGetApproverOptionFromRoleGroup {

		@Test
		@DisplayName("should use the roles' options, if not INHERITED")
		void usesRoleOptions() {
			// Arrange
			List<ApprovableBy> roleOptions = List.of();
			RoleGroup rolegroup = createRoleGroup(1L, List.of(), roleOptions);

			// Act
			List<ApprovableBy> result = approverOptionService.getInheritedApproverOption(rolegroup);

			// Assert
			assertThat(result)
				.containsExactlyElementsOf(roleOptions);
		}

		@Test
		@DisplayName("should use the global options, if both role is INHERITED")
		void usesGlobalOptions() {
			// Arrange
			List<ApprovableBy> roleOptions = List.of(ApprovableBy.INHERIT);
			List<ApprovableBy> globalOptions = List.of();

			RoleGroup rolegroup = createRoleGroup(1L, List.of(), roleOptions);
			when(settingsService.getRolerequestApprover()).thenReturn(globalOptions);

			// Act
			List<ApprovableBy> result = approverOptionService.getInheritedApproverOption(rolegroup);

			// Assert
			assertThat(result)
				.containsExactlyElementsOf(globalOptions);
		}
	}
}
