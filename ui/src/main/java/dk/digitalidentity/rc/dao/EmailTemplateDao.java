package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.EmailTemplate;
import dk.digitalidentity.rc.dao.model.enums.EmailTemplateType;

public interface EmailTemplateDao extends CrudRepository<EmailTemplate, Long> {
	EmailTemplate findByTemplateType(EmailTemplateType type);
	List<EmailTemplate> findAll();
}