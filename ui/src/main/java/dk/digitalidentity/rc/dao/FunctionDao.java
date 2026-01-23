package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.Function;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface FunctionDao extends JpaRepository<Function, String> {
	Function findByNameIgnoreCase(String name);

	List<Function> findByActiveTrue();

	Collection<Function> findByUuidInAndActiveTrue(Set<String> functionUuids);
}
