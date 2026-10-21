package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import dk.digitalidentity.rc.dao.SpecificationIdFinder;
import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;

public interface UserRoleViewDatatableDao extends DataTablesRepository<UserRoleView, Long>, SpecificationIdFinder<UserRoleView> {
}
