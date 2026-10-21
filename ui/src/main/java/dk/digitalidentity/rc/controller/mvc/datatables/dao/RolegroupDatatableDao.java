package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.model.RoleGroup;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

public interface RolegroupDatatableDao extends DataTablesRepository<RoleGroup, Long> {
}
