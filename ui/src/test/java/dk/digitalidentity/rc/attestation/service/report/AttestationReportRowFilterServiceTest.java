package dk.digitalidentity.rc.attestation.service.report;

import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentReportRowDTO;
import dk.digitalidentity.rc.attestation.service.AttestationCachedItSystemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Report Row Filter Service Tests")
class AttestationReportRowFilterServiceTest {

	@Mock
	private AttestationCachedItSystemService attestationCachedItSystemService;

	@InjectMocks
	private AttestationReportRowFilterService attestationReportRowFilterService;

	@Nested
	@DisplayName("filter() Tests")
	class FilterTests {

		@Test
		@DisplayName("Should return true for non-exempt IT system")
		void filter_WhenItSystemNotExempt_ShouldReturnTrue() {
			// Arrange
			RoleAssignmentReportRowDTO row = createReportRow(1L);
			when(attestationCachedItSystemService.isItSystemExempt(1L)).thenReturn(false);

			// Act
			Predicate<RoleAssignmentReportRowDTO> predicate = attestationReportRowFilterService.filter();
			boolean result = predicate.test(row);

			// Assert
			assertTrue(result);
			verify(attestationCachedItSystemService).isItSystemExempt(1L);
		}

		@Test
		@DisplayName("Should return false for exempt IT system")
		void filter_WhenItSystemExempt_ShouldReturnFalse() {
			// Arrange
			RoleAssignmentReportRowDTO row = createReportRow(2L);
			when(attestationCachedItSystemService.isItSystemExempt(2L)).thenReturn(true);

			// Act
			Predicate<RoleAssignmentReportRowDTO> predicate = attestationReportRowFilterService.filter();
			boolean result = predicate.test(row);

			// Assert
			assertFalse(result);
			verify(attestationCachedItSystemService).isItSystemExempt(2L);
		}

		@Test
		@DisplayName("Should filter out exempt IT systems when used with stream")
		void filter_WhenUsedWithStream_ShouldFilterExemptSystems() {
			// Arrange
			RoleAssignmentReportRowDTO exemptRow = createReportRow(1L);
			RoleAssignmentReportRowDTO nonExemptRow1 = createReportRow(2L);
			RoleAssignmentReportRowDTO nonExemptRow2 = createReportRow(3L);

			when(attestationCachedItSystemService.isItSystemExempt(1L)).thenReturn(true);
			when(attestationCachedItSystemService.isItSystemExempt(2L)).thenReturn(false);
			when(attestationCachedItSystemService.isItSystemExempt(3L)).thenReturn(false);

			List<RoleAssignmentReportRowDTO> rows = List.of(exemptRow, nonExemptRow1, nonExemptRow2);

			// Act
			List<RoleAssignmentReportRowDTO> filteredRows = rows.stream()
					.filter(attestationReportRowFilterService.filter())
					.toList();

			// Assert
			assertEquals(2, filteredRows.size());
			assertTrue(filteredRows.contains(nonExemptRow1));
			assertTrue(filteredRows.contains(nonExemptRow2));
			assertFalse(filteredRows.contains(exemptRow));
		}

		@Test
		@DisplayName("Should return empty list when all IT systems are exempt")
		void filter_WhenAllExempt_ShouldReturnEmptyList() {
			// Arrange
			RoleAssignmentReportRowDTO row1 = createReportRow(1L);
			RoleAssignmentReportRowDTO row2 = createReportRow(2L);

			when(attestationCachedItSystemService.isItSystemExempt(anyLong())).thenReturn(true);

			List<RoleAssignmentReportRowDTO> rows = List.of(row1, row2);

			// Act
			List<RoleAssignmentReportRowDTO> filteredRows = rows.stream()
					.filter(attestationReportRowFilterService.filter())
					.toList();

			// Assert
			assertTrue(filteredRows.isEmpty());
		}

		@Test
		@DisplayName("Should return all rows when no IT systems are exempt")
		void filter_WhenNoneExempt_ShouldReturnAllRows() {
			// Arrange
			RoleAssignmentReportRowDTO row1 = createReportRow(1L);
			RoleAssignmentReportRowDTO row2 = createReportRow(2L);
			RoleAssignmentReportRowDTO row3 = createReportRow(3L);

			when(attestationCachedItSystemService.isItSystemExempt(anyLong())).thenReturn(false);

			List<RoleAssignmentReportRowDTO> rows = List.of(row1, row2, row3);

			// Act
			List<RoleAssignmentReportRowDTO> filteredRows = rows.stream()
					.filter(attestationReportRowFilterService.filter())
					.toList();

			// Assert
			assertEquals(3, filteredRows.size());
		}

		@Test
		@DisplayName("Should check exemption status for each row's IT system ID")
		void filter_ShouldCheckEachRowItSystemId() {
			// Arrange
			RoleAssignmentReportRowDTO row1 = createReportRow(100L);
			RoleAssignmentReportRowDTO row2 = createReportRow(200L);
			RoleAssignmentReportRowDTO row3 = createReportRow(300L);

			when(attestationCachedItSystemService.isItSystemExempt(anyLong())).thenReturn(false);

			List<RoleAssignmentReportRowDTO> rows = List.of(row1, row2, row3);

			// Act
			rows.stream()
					.filter(attestationReportRowFilterService.filter())
					.toList();

			// Assert
			verify(attestationCachedItSystemService).isItSystemExempt(100L);
			verify(attestationCachedItSystemService).isItSystemExempt(200L);
			verify(attestationCachedItSystemService).isItSystemExempt(300L);
		}
	}

	private RoleAssignmentReportRowDTO createReportRow(Long itSystemId) {
		return RoleAssignmentReportRowDTO.builder()
				.itSystemId(itSystemId)
				.itSystemName("Test System " + itSystemId)
				.userName("Test User")
				.userRoleName("Test Role")
				.build();
	}
}
