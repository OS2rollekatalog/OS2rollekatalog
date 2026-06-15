package dk.digitalidentity.rc.controller.mvc.viewmodel;


import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmailTemplateDTO {
	private long id;
	private String title;
	private String message;
	private String notes;
	private String templateTypeText;
	private boolean enabled;
	private boolean allowDaysBeforeEvent;
	private boolean allowDaysBeforeEventFeature;
	private Integer daysBeforeEvent;
	private List<EmailTemplatePlaceholder> emailTemplatePlaceholders;
	// Sub-category for grouping in the overview table (e.g. "Normalt rul", "Følsomt rul")
	private String category;
	// Localized display name of the template type (for the "Skabelon" column)
	private String templateTypeName;
	// repeating part support (template types with a RepeatingPartDescriptor)
	private boolean hasRepeatingPart;
	private boolean hasNestedRepeatingPart;
	private String repeatingPart;
	private String nestedRepeatingPart;
	private List<EmailTemplatePlaceholder> repeatingPartPlaceholders;
	private List<EmailTemplatePlaceholder> nestedRepeatingPartPlaceholders;
}
