package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.NotificationViewInactive;

public interface NotificationDatatableDaoInactive extends DataTablesRepository<NotificationViewInactive, Long> {

}
