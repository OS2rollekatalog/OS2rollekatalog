package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.UserRoleView;

public interface UserRoleViewDao extends DataTablesRepository<UserRoleView, Long> {

}