package dk.digitalidentity.rc.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.ReportTemplateDao;
import dk.digitalidentity.rc.dao.model.ReportTemplate;
import dk.digitalidentity.rc.dao.model.User;

@Service
public class ReportTemplateService {

	@Autowired
	private ReportTemplateDao reportTemplateDao;

	public ReportTemplate getById(long id) {
		return reportTemplateDao.findById(id);
	}

	public List<ReportTemplate> getAll() {
		return reportTemplateDao.findAll();
	}

	public void saveTemplate(ReportTemplate entity) {
		reportTemplateDao.save(entity);
	}

	public void deleteTemplate(ReportTemplate entity) {
		reportTemplateDao.delete(entity);
	}
	
	public List<ReportTemplate> getByUser(User user) {
		return reportTemplateDao.findByUsersContains(user);
	}
}
