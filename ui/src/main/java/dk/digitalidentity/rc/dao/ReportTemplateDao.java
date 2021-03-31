package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.dao.model.User;

public interface ReportTemplateDao extends CrudRepository<ReportTemplate, Long> {
	ReportTemplate getById(long id);
	List<ReportTemplate> findAll();
	List<ReportTemplate> findByUsersContains(User user);
}
