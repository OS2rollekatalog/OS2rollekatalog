package dk.digitalidentity.rc.event;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.config.model.Titles;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitRoleGroupAssignment;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.Title;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.model.OrgUnitWithTitlesDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewTitlesInOrgUnitsEventHandlerTest {

	@Mock
	private SettingsService settingsService;
	@Mock
	private NotificationService notificationService;
	@Mock
	private RoleCatalogueConfiguration configuration;

	@InjectMocks
	private NewTitlesInOrgUnitsEventHandler handler;

	@BeforeEach
	void enableTitles() {
		Titles titles = new Titles();
		titles.setEnabled(true);
		when(configuration.getTitles()).thenReturn(titles);
	}

	@Nested
	@DisplayName("NEW_TITLE_IN_ORG_UNIT — fires for all new titles on OUs with title-scoped assignments")
	class NewTitleInOrgUnit {

		@Test
		@DisplayName("shouldFireForNewTitleWithNoPriorAssignments")
		void shouldFireForNewTitleWithNoPriorAssignments() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(true);

			OrgUnitWithTitlesDTO dto = TestData.dtoWithTitleScopedAssignmentForDifferentTitle("ou-1", "Sagsbehandler");
			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationService).save(captor.capture());
			assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.NEW_TITLE_IN_ORG_UNIT);
			assertThat(captor.getValue().getMessage()).contains("Sagsbehandler");
		}

		@Test
		@DisplayName("shouldFireForNewTitleThatAlreadyHasPriorAssignments")
		void shouldFireForNewTitleThatAlreadyHasPriorAssignments() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(true);

			Title title = TestData.title("title-uuid", "Sagsbehandler");
			OrgUnitWithTitlesDTO dto = TestData.dtoWithExistingUserRoleAssignmentForTitle("ou-1", title);
			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationService, times(1)).save(captor.capture());
			assertThat(captor.getAllValues().stream().map(Notification::getNotificationType))
				.contains(NotificationType.NEW_TITLE_IN_ORG_UNIT);
		}

		@Test
		@DisplayName("shouldNotFireWhenOuHasNoTitleScopedAssignments")
		void shouldNotFireWhenOuHasNoTitleScopedAssignments() {
			Title newTitle = TestData.title("new-uuid", "Sagsbehandler");

			OrgUnit ou = TestData.orgUnit("ou-1", "Testenheden");
			OrgUnitUserRoleAssignment unscopedAssignment = new OrgUnitUserRoleAssignment();
			unscopedAssignment.setTitles(new ArrayList<>());
			ou.setUserRoleAssignments(List.of(unscopedAssignment));

			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(ou);
			dto.getNewTitles().add(newTitle);

			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			verify(notificationService, never()).save(any());
		}

		@Test
		@DisplayName("shouldNotFireWhenNotificationTypeDisabled")
		void shouldNotFireWhenNotificationTypeDisabled() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(false);

			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this,
				Set.of(TestData.dtoWithTitleScopedAssignmentForDifferentTitle("ou-1", "Sagsbehandler")));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			verify(notificationService, never()).save(any());
		}
	}

	@Nested
	@DisplayName("RETURNING_TITLE_IN_ORG_UNIT — fires only for titles with no prior assignments")
	class ReturningTitleInOrgUnit {

		@Test
		@DisplayName("shouldFireForTitleWithNoPriorUserRoleAssignments")
		void shouldFireForTitleWithNoPriorUserRoleAssignments() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(false);
			when(settingsService.isNotificationTypeEnabled(NotificationType.RETURNING_TITLE_IN_ORG_UNIT)).thenReturn(true);

			OrgUnitWithTitlesDTO dto = TestData.dtoWithTitleScopedAssignmentForDifferentTitle("ou-1", "Sagsbehandler");
			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationService).save(captor.capture());
			assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.RETURNING_TITLE_IN_ORG_UNIT);
			assertThat(captor.getValue().getMessage()).contains("Sagsbehandler");
		}

		@Test
		@DisplayName("shouldNotFireWhenTitleAlreadyHasPriorUserRoleAssignment")
		void shouldNotFireWhenTitleAlreadyHasPriorUserRoleAssignment() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(true);

			Title title = TestData.title("title-uuid", "Sagsbehandler");
			OrgUnitWithTitlesDTO dto = TestData.dtoWithExistingUserRoleAssignmentForTitle("ou-1", title);
			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationService).save(captor.capture());
			assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.NEW_TITLE_IN_ORG_UNIT);
		}

		@Test
		@DisplayName("shouldNotFireWhenTitleAlreadyHasPriorRoleGroupAssignment")
		void shouldNotFireWhenTitleAlreadyHasPriorRoleGroupAssignment() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(true);

			Title title = TestData.title("title-uuid", "Konsulent");
			OrgUnitWithTitlesDTO dto = TestData.dtoWithExistingRoleGroupAssignmentForTitle("ou-1", title);
			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationService).save(captor.capture());
			assertThat(captor.getValue().getNotificationType()).isEqualTo(NotificationType.NEW_TITLE_IN_ORG_UNIT);
		}

		@Test
		@DisplayName("shouldNotFireWhenNotificationTypeDisabled")
		void shouldNotFireWhenNotificationTypeDisabled() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(false);
			when(settingsService.isNotificationTypeEnabled(NotificationType.RETURNING_TITLE_IN_ORG_UNIT)).thenReturn(false);

			OrgUnitWithTitlesDTO dto = TestData.dtoWithTitleScopedAssignmentForDifferentTitle("ou-1", "Sagsbehandler");
			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			verify(notificationService, never()).save(any());
		}

		@Test
		@DisplayName("shouldNotFireWhenOuHasNoTitleScopedAssignmentsAtAll")
		void shouldNotFireWhenOuHasNoTitleScopedAssignmentsAtAll() {
			Title newTitle = TestData.title("new-uuid", "Sagsbehandler");

			OrgUnit ou = TestData.orgUnit("ou-1", "Testenheden");
			OrgUnitUserRoleAssignment unscopedAssignment = new OrgUnitUserRoleAssignment();
			unscopedAssignment.setTitles(new ArrayList<>());
			ou.setUserRoleAssignments(List.of(unscopedAssignment));

			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(ou);
			dto.getNewTitles().add(newTitle);

			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			verify(notificationService, never()).save(any());
		}
	}

	@Nested
	@DisplayName("Mixed OU — titles both with and without prior assignments")
	class MixedTitles {

		@Test
		@DisplayName("shouldFireBothNotificationTypesWhenOuHasTitlesWithAndWithoutPriorAssignments")
		void shouldFireBothNotificationTypesWhenOuHasTitlesWithAndWithoutPriorAssignments() {
			when(settingsService.isNotificationTypeEnabled(NotificationType.NEW_TITLE_IN_ORG_UNIT)).thenReturn(true);
			when(settingsService.isNotificationTypeEnabled(NotificationType.RETURNING_TITLE_IN_ORG_UNIT)).thenReturn(true);

			// titleWithPriorAssignment: OU already has an assignment scoped to this title
			Title titleWithPriorAssignment = TestData.title("existing-uuid", "Konsulent");
			// brandNewTitle: OU has no assignment for this title
			Title brandNewTitle = TestData.title("new-uuid", "Leder");

			OrgUnit ou = TestData.orgUnit("ou-1", "Testenheden");
			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setTitles(List.of(titleWithPriorAssignment));
			ou.setUserRoleAssignments(List.of(assignment));
			ou.setRoleGroupAssignments(new ArrayList<>());

			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(ou);
			dto.getNewTitles().add(titleWithPriorAssignment);
			dto.getNewTitles().add(brandNewTitle);

			NewTitlesInOrgUnitsEvent event = new NewTitlesInOrgUnitsEvent(this, Set.of(dto));

			handler.handleNewTitlesInOrgUnitsEvent(event);

			// NEW_TITLE_IN_ORG_UNIT fires once for all titles; RETURNING_TITLE_IN_ORG_UNIT fires once for brandNewTitle only
			ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
			verify(notificationService, times(2)).save(captor.capture());

			List<NotificationType> types = captor.getAllValues().stream()
				.map(Notification::getNotificationType)
				.toList();
			assertThat(types).containsExactlyInAnyOrder(
				NotificationType.NEW_TITLE_IN_ORG_UNIT,
				NotificationType.RETURNING_TITLE_IN_ORG_UNIT
			);

			// RETURNING_TITLE_IN_ORG_UNIT message should contain only the brand new title
			Notification returningNotification = captor.getAllValues().stream()
				.filter(n -> n.getNotificationType() == NotificationType.RETURNING_TITLE_IN_ORG_UNIT)
				.findFirst().orElseThrow();
			assertThat(returningNotification.getMessage()).contains("Leder");
			assertThat(returningNotification.getMessage()).doesNotContain("Konsulent");
		}
	}

	static class TestData {

		static Title title(String uuid, String name) {
			Title t = new Title();
			t.setUuid(uuid);
			t.setName(name);
			return t;
		}

		static OrgUnit orgUnit(String uuid, String name) {
			OrgUnit ou = new OrgUnit();
			ou.setUuid(uuid);
			ou.setName(name);
			ou.setUserRoleAssignments(new ArrayList<>());
			ou.setRoleGroupAssignments(new ArrayList<>());
			return ou;
		}

		// OU has a title-scoped assignment for a *different* title — guard passes, but new title has no prior assignment
		static OrgUnitWithTitlesDTO dtoWithTitleScopedAssignmentForDifferentTitle(String ouUuid, String newTitleName) {
			Title newTitle = title(ouUuid + "-title", newTitleName);
			Title otherTitle = title(ouUuid + "-other", "Anden stilling");

			OrgUnit ou = orgUnit(ouUuid, "Enhed " + ouUuid);
			OrgUnitUserRoleAssignment existingAssignment = new OrgUnitUserRoleAssignment();
			existingAssignment.setTitles(List.of(otherTitle));
			ou.setUserRoleAssignments(List.of(existingAssignment));

			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(ou);
			dto.getNewTitles().add(newTitle);
			return dto;
		}

		// OU already has a user role assignment scoped to the given title
		static OrgUnitWithTitlesDTO dtoWithExistingUserRoleAssignmentForTitle(String ouUuid, Title title) {
			OrgUnit ou = orgUnit(ouUuid, "Enhed " + ouUuid);

			OrgUnitUserRoleAssignment assignment = new OrgUnitUserRoleAssignment();
			assignment.setTitles(List.of(title));
			ou.setUserRoleAssignments(List.of(assignment));

			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(ou);
			dto.getNewTitles().add(title);
			return dto;
		}

		// OU already has a role group assignment scoped to the given title
		static OrgUnitWithTitlesDTO dtoWithExistingRoleGroupAssignmentForTitle(String ouUuid, Title title) {
			OrgUnit ou = orgUnit(ouUuid, "Enhed " + ouUuid);

			OrgUnitRoleGroupAssignment assignment = new OrgUnitRoleGroupAssignment();
			assignment.setTitles(List.of(title));
			ou.setRoleGroupAssignments(List.of(assignment));

			OrgUnitWithTitlesDTO dto = new OrgUnitWithTitlesDTO();
			dto.setOrgUnit(ou);
			dto.getNewTitles().add(title);
			return dto;
		}
	}
}
