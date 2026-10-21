package dk.digitalidentity.rc.attestation.service.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Attestation Report Paginator Tests")
class AttestationReportPaginatorTest {

	private static final LocalDate SINCE = LocalDate.now().minusMonths(3);
	private static final LocalDate TO = LocalDate.now();

	@Test
	@DisplayName("Should look up the attestations once and reuse them for every page")
	void next_WithSeveralPages_ShouldLookUpContextOnce() {
		// Arrange - 2500 rows is three pages
		final AttestationReportService service = mock(AttestationReportService.class);
		final AttestationRun run = new AttestationRun();
		final AttestationReportContextService.AttestationReportContext context =
				AttestationReportContextService.AttestationReportContext.builder().build();
		when(service.reportContext(SINCE, TO, run)).thenReturn(context);
		when(service.getUserRoleRows(eq(SINCE), eq(TO), anyList(), same(context))).thenReturn(Collections.emptyList());

		final List<Long> rowIds = LongStream.range(0, 2500).boxed().toList();
		final AttestationReportPaginator paginator =
				new AttestationReportPaginator(service, rowIds, SINCE, TO, _ -> true, run);

		// Act
		int pages = 0;
		while (paginator.hasNext()) {
			paginator.next();
			pages++;
		}

		// Assert - the run the report was pulled for must reach the lookup, and only once
		assertEquals(3, pages);
		verify(service, times(1)).reportContext(SINCE, TO, run);
		verify(service, times(3)).getUserRoleRows(eq(SINCE), eq(TO), anyList(), same(context));
	}

	@Test
	@DisplayName("Should not look up anything for a report without rows")
	void next_WithNoRows_ShouldNotLookUpContext() {
		// Arrange
		final AttestationReportService service = mock(AttestationReportService.class);
		final AttestationReportPaginator paginator = new AttestationReportPaginator(
				service, Collections.emptyList(), SINCE, TO, _ -> true);

		// Act
		final List<RoleAssignmentReportRowDTO> rows = paginator.next();

		// Assert
		assertEquals(Collections.emptyList(), rows);
		verify(service, times(0)).reportContext(SINCE, TO, null);
	}
}
