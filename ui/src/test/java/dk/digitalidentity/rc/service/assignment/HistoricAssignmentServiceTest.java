package dk.digitalidentity.rc.service.assignment;

import dk.digitalidentity.rc.dao.assignment.HistoricAssignmentDao;
import dk.digitalidentity.rc.dao.model.assignment.CurrentAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricAssignment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createCurrentAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class HistoricAssignmentServiceTest {

	@Mock
	private HistoricAssignmentDao historicAssignmentDao;

	@InjectMocks
	private HistoricAssignmentService service;

	// ---- ------------- ---- //

	@Nested
	@DisplayName("createFromCurrentAssignments")
	class CreateFromCurrentAssignments {

		@Test
		@DisplayName("saves one HistoricAssignment per CurrentAssignment")
		void savesOneHistoricPerCurrent() {
			// ---- Given ---- //
			CurrentAssignment ca1 = createCurrentAssignment(1L, "hash-1", createUser("user-1"));
			CurrentAssignment ca2 = createCurrentAssignment(2L, "hash-2", createUser("user-2"));

			// ---- When ---- //
			service.createFromCurrentAssignments(Set.of(ca1, ca2));

			// ---- Then ---- //
			ArgumentCaptor<Set<HistoricAssignment>> captor = ArgumentCaptor.forClass(Set.class);
			verify(historicAssignmentDao).saveAll(captor.capture());

			assertThat(captor.getValue()).hasSize(2);
		}

		@Test
		@DisplayName("saves nothing when input set is empty")
		void savesNothingForEmptyInput() {
			// ---- When ---- //
			service.createFromCurrentAssignments(Set.of());

			// ---- Then ---- //
			ArgumentCaptor<Set<HistoricAssignment>> captor = ArgumentCaptor.forClass(Set.class);
			verify(historicAssignmentDao).saveAll(captor.capture());

			assertThat(captor.getValue()).isEmpty();
		}
	}

	@Nested
	@DisplayName("updateValidToFor")
	class UpdateValidToFor {

		@Test
		@DisplayName("sets validTo on all historic assignments found by hash")
		void setsValidToOnFoundAssignments() {
			// ---- Given ---- //
			CurrentAssignment ca = createCurrentAssignment(1L, "hash-1", createUser("user-1"));
			LocalDateTime validTo = LocalDateTime.of(2025, 6, 1, 12, 0);

			HistoricAssignment historic = new HistoricAssignment();
			historic.setRecordHash("hash-1");

			given(historicAssignmentDao.findAllByRecordHashIn(Set.of("hash-1")))
				.willReturn(Set.of(historic));

			// ---- When ---- //
			service.updateValidToFor(Set.of(ca), validTo);

			// ---- Then ---- //
			assertThat(historic.getValidTo()).isEqualTo(validTo);
			verify(historicAssignmentDao).saveAll(Set.of(historic));
		}

		@Test
		@DisplayName("looks up historic assignments by the hashes of the given current assignments")
		void lookupsUseRecordHashesFromCurrentAssignments() {
			// ---- Given ---- //
			CurrentAssignment ca1 = createCurrentAssignment(1L, "hash-1", createUser("user-1"));
			CurrentAssignment ca2 = createCurrentAssignment(2L, "hash-2", createUser("user-2"));

			given(historicAssignmentDao.findAllByRecordHashIn(any()))
				.willReturn(Set.of());

			// ---- When ---- //
			service.updateValidToFor(Set.of(ca1, ca2), LocalDateTime.now());

			// ---- Then ---- //
			ArgumentCaptor<Set<String>> hashCaptor = ArgumentCaptor.forClass(Set.class);
			verify(historicAssignmentDao).findAllByRecordHashIn(hashCaptor.capture());

			assertThat(hashCaptor.getValue()).containsExactlyInAnyOrder("hash-1", "hash-2");
		}
	}

	@Nested
	@DisplayName("getActiveAtDate")
	class GetActiveAtDate {

		@Test
		@DisplayName("queries with start of day and end of day for the given date")
		void queriesWithCorrectDayBoundaries() {
			// ---- Given ---- //
			LocalDate date = LocalDate.of(2025, 6, 15);
			given(historicAssignmentDao.findActiveAtDate(any(), any())).willReturn(List.of());

			// ---- When ---- //
			service.getActiveAtDate(date);

			// ---- Then ---- //
			ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
			ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
			verify(historicAssignmentDao).findActiveAtDate(startCaptor.capture(), endCaptor.capture());

			assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2025, 6, 15, 0, 0, 0));
			assertThat(endCaptor.getValue()).isEqualTo(LocalDateTime.of(2025, 6, 15, 23, 59, 59, 999999999));
		}
	}

	@Nested
	@DisplayName("getActiveAtDateAndItSystems")
	class GetActiveAtDateAndItSystems {

		@Test
		@DisplayName("filters by IT system IDs when list is non-empty")
		void filtersByItSystemIdsWhenPresent() {
			// ---- Given ---- //
			LocalDate date = LocalDate.of(2025, 6, 15);
			List<Long> itSystemIds = List.of(10L, 20L);
			given(historicAssignmentDao.findActiveAtDateAndItSystemIdIn(any(), any(), anyCollection()))
				.willReturn(List.of());

			// ---- When ---- //
			service.getActiveAtDateAndItSystems(date, itSystemIds);

			// ---- Then ---- //
			ArgumentCaptor<Collection<Long>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
			verify(historicAssignmentDao).findActiveAtDateAndItSystemIdIn(any(), any(), idsCaptor.capture());

			assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);
			verifyNoMoreInteractions(historicAssignmentDao);
		}

		@Test
		@DisplayName("falls back to getActiveAtDate when itSystemIds is empty")
		void fallsBackToGetActiveAtDateWhenEmpty() {
			// ---- Given ---- //
			LocalDate date = LocalDate.of(2025, 6, 15);
			given(historicAssignmentDao.findActiveAtDate(any(), any())).willReturn(List.of());

			// ---- When ---- //
			service.getActiveAtDateAndItSystems(date, List.of());

			// ---- Then ---- //
			verify(historicAssignmentDao).findActiveAtDate(any(), any());
			verifyNoMoreInteractions(historicAssignmentDao);
		}

		@Test
		@DisplayName("falls back to getActiveAtDate when itSystemIds is null")
		void fallsBackToGetActiveAtDateWhenNull() {
			// ---- Given ---- //
			LocalDate date = LocalDate.of(2025, 6, 15);
			given(historicAssignmentDao.findActiveAtDate(any(), any())).willReturn(List.of());

			// ---- When ---- //
			service.getActiveAtDateAndItSystems(date, null);

			// ---- Then ---- //
			verify(historicAssignmentDao).findActiveAtDate(any(), any());
			verifyNoMoreInteractions(historicAssignmentDao);
		}
	}
}
