package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.UserRole;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;
import org.springframework.data.jpa.domain.Specification;

public interface UserroleDatatableDao extends DataTablesRepository<UserRole, String> {
}
