package dk.digitalidentity.rc.bootstrap;

import dk.digitalidentity.rc.dao.OrgUnitRoleGroupAssignmentDao;
import dk.digitalidentity.rc.dao.OrgUnitUserRoleAssignmentDao;
import dk.digitalidentity.rc.dao.assignment.HistoricOuAssignmentDao;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.OrgUnitUserRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignment;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion;
import dk.digitalidentity.rc.dao.model.assignment.HistoricOuAssignmentExclusion.ExclusionType;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentHashCalculator;
import dk.digitalidentity.rc.service.assignment.HistoricOuAssignmentService;
import dk.digitalidentity.rc.service.model.AssignedThrough;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createItSystem;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createNamedOrgUnit;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createOrgUnitUserRoleAssignment;
import static dk.digitalidentity.rc.mockfactory.assignment.MockFactory.createUserRole;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExceptedOusHistoricBackfill")
class ExceptedOusHistoricBackfillTest {

	@Mock
	private OrgUnitUserRoleAssignmentDao orgUnitUserRoleAssignmentDao;
	@Mock
	private OrgUnitRoleGroupAssignmentDao orgUnitRoleGroupAssignmentDao;
	@Mock
	private HistoricOuAssignmentDao historicOuAssignmentDao;
	@Mock
	private HistoricOuAssignmentService historicOuAssignmentService;

	@InjectMocks
	private ExceptedOusHistoricBackfill backfill;

	private OrgUnit ou;
	private UserRole userRole;

	@BeforeEach
	void setUp() {
		ou = createNamedOrgUnit("ou-uuid", "Enhed");
		userRole = createUserRole(20L, "Jobfunktionsrolle", createItSystem(10L, "It-system"));
		given(orgUnitRoleGroupAssignmentDao.findByContainsExceptedOusTrue()).willReturn(List.of());
	}

	/** The row the service calculates for a live assignment excepting {@code exceptedOuUuids}, if any. */
	private static HistoricOuAssignment expectedRow(String... exceptedOuUuids) {
		List<HistoricOuAssignmentExclusion> exclusions = new ArrayList<>();
		if (exceptedOuUuids.length > 0) {
			exclusions.add(HistoricOuAssignmentExclusion.builder()
				.exclusionType(ExclusionType.EXCEPTED_OUS)
				.uuids(List.of(exceptedOuUuids))
				.build());
		}
		HistoricOuAssignment row = HistoricOuAssignment.builder()
			.ouUuid("ou-uuid")
			.itSystemId(10L)
			.roleId(20L)
			.assignedThroughType(AssignedThrough.ORGUNIT)
			.assignedThroughUuid("ou-uuid")
			.exclusions(exclusions)
			.build();
		row.setRecordHash(HistoricOuAssignmentHashCalculator.compute(row));
		return row;
	}

	private static HistoricOuAssignment openRowSavedWithoutExceptedOus(long id) {
		HistoricOuAssignment row = expectedRow();
		row.setId(id);
		return row;
	}

	private OrgUnitUserRoleAssignment liveAssignment(long id, HistoricOuAssignment expected) {
		OrgUnitUserRoleAssignment assignment = createOrgUnitUserRoleAssignment(userRole, ou, false, false, false);
		assignment.setId(id);
		given(historicOuAssignmentService.toHistoricRecord(assignment)).willReturn(expected);
		return assignment;
	}

	@Nested
	@DisplayName("run")
	class Run {

		@SuppressWarnings("unchecked")
		@Test
		@DisplayName("gives each of two assignments of the same role its own excepted org units")
		void keepsDuplicateAssignmentsApart() {
			// ---- Given ---- //
			HistoricOuAssignment expectedA = expectedRow("excepted-ou-a");
			HistoricOuAssignment expectedB = expectedRow("excepted-ou-b");
			HistoricOuAssignment firstRow = openRowSavedWithoutExceptedOus(1L);
			HistoricOuAssignment secondRow = openRowSavedWithoutExceptedOus(2L);
			List<OrgUnitUserRoleAssignment> liveAssignments = List.of(liveAssignment(1L, expectedA), liveAssignment(2L, expectedB));
			given(orgUnitUserRoleAssignmentDao.findByContainsExceptedOusTrue()).willReturn(liveAssignments);
			given(historicOuAssignmentDao.findFirstWithExclusionsByRecordHashAndValidToIsNullOrderByIdAsc(firstRow.getRecordHash()))
				.willReturn(Optional.of(firstRow), Optional.of(secondRow));

			// ---- When ---- //
			backfill.run();

			// ---- Then ---- //
			verify(historicOuAssignmentService).stampOrCloseAsDuplicate(firstRow, expectedA.getRecordHash());
			verify(historicOuAssignmentService).stampOrCloseAsDuplicate(secondRow, expectedB.getRecordHash());
			assertThat(firstRow.getExclusions()).singleElement()
				.satisfies(e -> assertThat(e.getUuids()).containsExactly("excepted-ou-a"));
		}

		@Test
		@DisplayName("leaves an assignment alone when its open row already has the current hash")
		void skipsRowsAlreadyUpToDate() {
			// ---- Given ---- //
			HistoricOuAssignment expected = expectedRow("excepted-ou-a");
			List<OrgUnitUserRoleAssignment> liveAssignments = List.of(liveAssignment(1L, expected));
			given(orgUnitUserRoleAssignmentDao.findByContainsExceptedOusTrue()).willReturn(liveAssignments);
			given(historicOuAssignmentDao.existsByRecordHashAndValidToIsNull(expected.getRecordHash())).willReturn(true);

			// ---- When ---- //
			backfill.run();

			// ---- Then ---- //
			verify(historicOuAssignmentService, never()).stampOrCloseAsDuplicate(any(), any());
		}
	}
}
