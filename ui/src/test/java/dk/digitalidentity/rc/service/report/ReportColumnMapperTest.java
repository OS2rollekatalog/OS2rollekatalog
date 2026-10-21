package dk.digitalidentity.rc.service.report;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dk.digitalidentity.rc.dao.model.ReportTemplate.ReportTemplateColumnSelection;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportColumnMapper")
class ReportColumnMapperTest {

	@Test
	@DisplayName("An explicit zero-column selection survives a save/load round-trip")
	void explicitlyEmptySelection_SurvivesRoundTrip() {
		Map<String, List<String>> saved = Map.of(ReportColumnDefinitions.USERS, List.of());

		Set<ReportTemplateColumnSelection> entity = ReportColumnMapper.toEntitySelections(saved);
		Map<String, List<String>> loaded = ReportColumnMapper.toFormSelections(entity);

		assertThat(loaded).containsKey(ReportColumnDefinitions.USERS);
		assertThat(loaded.get(ReportColumnDefinitions.USERS)).isEmpty();
	}

	@Test
	@DisplayName("An empty selection for an unknown content type is not persisted")
	void emptySelectionForUnknownContentType_IsDropped() {
		Map<String, List<String>> saved = Map.of("NotARealContentType", List.of());

		Set<ReportTemplateColumnSelection> entity = ReportColumnMapper.toEntitySelections(saved);

		assertThat(entity).isEmpty();
	}
}
