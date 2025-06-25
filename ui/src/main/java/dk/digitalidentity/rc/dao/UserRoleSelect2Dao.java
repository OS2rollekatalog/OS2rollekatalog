package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface UserRoleSelect2Dao extends PagingAndSortingRepository<UserRole, Long> {
	Page<UserRole> findByNameContainingIgnoreCase(String name, Pageable pageable);
}