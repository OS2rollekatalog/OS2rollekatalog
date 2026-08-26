package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ManualWelcomeEmailTemplateDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualWelcomeEmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplatePlaceholder;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManualWelcomeEmailTemplateService {
	public static final List<EmailTemplatePlaceholder> PLACEHOLDERS = List.of(
		EmailTemplatePlaceholder.RECEIVER_PLACEHOLDER,
		EmailTemplatePlaceholder.ITSYSTEM_PLACEHOLDER,
		EmailTemplatePlaceholder.ROLE_NAME,
		EmailTemplatePlaceholder.COMMENT_PLACEHOLDER);

	private final ManualWelcomeEmailTemplateDao manualWelcomeEmailTemplateDao;

	public ManualWelcomeEmailTemplate findByItSystem(ItSystem itSystem, ManualAssignmentEffectuationOperation operation) {
		ManualWelcomeEmailTemplate template = manualWelcomeEmailTemplateDao.findByItSystem_IdAndOperation(itSystem.getId(), operation);
		if (template == null) {
			template = new ManualWelcomeEmailTemplate();
			template.setItSystem(itSystem);
			template.setOperation(operation);
			template.setEnabled(false);

			if (operation == ManualAssignmentEffectuationOperation.ASSIGN) {
				template.setTitle("Du har fået adgang til {itsystem}");
				template.setMessage("Kære {modtager}\n<br/>\n<br/>\nDu er nu blevet oprettet med rollen {rolle} i it-systemet {itsystem}.\n<br/>\n<br/>\n{supplerende_kommentar}");
			} else {
				template.setTitle("Din adgang til {itsystem} er fjernet");
				template.setMessage("Kære {modtager}\n<br/>\n<br/>\nDin rolle {rolle} i it-systemet {itsystem} er nu blevet fjernet.\n<br/>\n<br/>\n{supplerende_kommentar}");
			}

			template = manualWelcomeEmailTemplateDao.save(template);
		}

		return template;
	}

	public ManualWelcomeEmailTemplate save(ManualWelcomeEmailTemplate template) {
		return manualWelcomeEmailTemplateDao.save(template);
	}

	public ManualWelcomeEmailTemplate findById(long id) {
		return manualWelcomeEmailTemplateDao.findById(id).orElse(null);
	}
}
