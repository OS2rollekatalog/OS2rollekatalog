package dk.digitalidentity.rc.dao;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import dk.digitalidentity.rc.dao.model.AltAccount;
import dk.digitalidentity.rc.dao.model.enums.AltAccountType;

public interface AltAccountDao extends CrudRepository<AltAccount, Long> {
	List<AltAccount> findByAccountType(AltAccountType type);

	long countByAccountType(AltAccountType kspcics);
}
