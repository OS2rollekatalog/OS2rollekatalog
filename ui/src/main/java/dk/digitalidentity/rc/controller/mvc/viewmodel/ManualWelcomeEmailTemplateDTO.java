package dk.digitalidentity.rc.controller.mvc.viewmodel;

import java.util.List;

import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ManualWelcomeEmailTemplateDTO {
	private long id;
	private long itSystemId;
	private String itSystemName;
	private ManualAssignmentEffectuationOperation operation;
	private String title;
	private String message;
	private String notes;
	private boolean enabled;
	private List<EmailTemplatePlaceholder> emailTemplatePlaceholders;
}
