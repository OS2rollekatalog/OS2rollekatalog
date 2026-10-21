package dk.digitalidentity.rc.service.report;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dk.digitalidentity.rc.dao.model.ReportTemplate.ReportTemplateColumnSelection;

/**
 * Converts between the wire/form-side column-selection shape ({@code Map<String, List<String>>},
 * used by {@code ReportForm}/{@code ReportAM}) and the DB entity-side shape
 * ({@code Set<ReportTemplateColumnSelection>}, used by {@code ReportTemplate}).
 */
public final class ReportColumnMapper {

	// A row with zero columns for a content type is indistinguishable in the DB from that content
	// type never having been touched (both are just "no rows"), so an explicit exclusion needs a
	// marker row to survive a save/load round-trip.
	private static final String EXCLUDED_MARKER = "__EXCLUDED__";

	private ReportColumnMapper() {
	}

	public static Set<ReportTemplateColumnSelection> toEntitySelections(Map<String, List<String>> selectedColumns) {
		Set<ReportTemplateColumnSelection> result = new LinkedHashSet<>();

		if (selectedColumns == null) {
			return result;
		}

		for (Map.Entry<String, List<String>> entry : selectedColumns.entrySet()) {
			List<String> columnKeys = entry.getValue();
			if (columnKeys == null) {
				continue;
			}

			List<ReportColumnDefinitions.ReportColumn> definedColumns = ReportColumnDefinitions.columnsFor(entry.getKey());
			if (definedColumns.isEmpty()) {
				continue;
			}

			if (columnKeys.isEmpty()) {
				result.add(new ReportTemplateColumnSelection(entry.getKey(), EXCLUDED_MARKER));
				continue;
			}

			Set<String> validKeys = definedColumns.stream()
				.map(ReportColumnDefinitions.ReportColumn::key)
				.collect(Collectors.toSet());

			for (String columnKey : columnKeys) {
				if (validKeys.contains(columnKey)) {
					result.add(new ReportTemplateColumnSelection(entry.getKey(), columnKey));
				}
			}
		}

		return result;
	}

	public static Map<String, List<String>> toFormSelections(Set<ReportTemplateColumnSelection> entitySelections) {
		if (entitySelections == null || entitySelections.isEmpty()) {
			return Map.of();
		}

		Map<String, List<String>> grouped = entitySelections.stream()
			.collect(Collectors.groupingBy(
				ReportTemplateColumnSelection::getContentType,
				Collectors.mapping(ReportTemplateColumnSelection::getColumnKey, Collectors.toCollection(ArrayList::new))
			));

		grouped.replaceAll((_, columnKeys) ->
			columnKeys.size() == 1 && columnKeys.contains(EXCLUDED_MARKER) ? List.of() : columnKeys);

		return grouped;
	}
}
