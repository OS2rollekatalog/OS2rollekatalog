package dk.digitalidentity.rc.dao;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ManualWelcomeEmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;

public interface ManualWelcomeEmailTemplateDao extends CrudRepository<ManualWelcomeEmailTemplate, Long> {
	ManualWelcomeEmailTemplate findByItSystem_IdAndOperation(long itSystemId, ManualAssignmentEffectuationOperation operation);
}
