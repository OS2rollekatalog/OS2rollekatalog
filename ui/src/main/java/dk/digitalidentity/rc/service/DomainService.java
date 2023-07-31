package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.DomainDao;
import dk.digitalidentity.rc.dao.model.Domain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class DomainService {

	@Autowired
	private DomainDao domainDao;

	public Domain getPrimaryDomain() {
		Domain primary = domainDao.findByName("Administrativt");
		if (primary == null) {
			log.error("No primary domain found");
		}
		
		return primary;
	}

	public Domain getDomainOrPrimary(String name) {
		if (!StringUtils.hasLength(name)) {
			return getPrimaryDomain();
		}

		return getByName(name);
	}

	public static boolean isPrimaryDomain(Domain domain) {
		return "Administrativt".equals(domain.getName());
	}

	public Domain getByName(String name) {
		return domainDao.findByName(name);
	}

	public List<Domain> getAll() {
		return domainDao.findAll();
	}
}
