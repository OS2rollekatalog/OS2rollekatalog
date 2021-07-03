package dk.digitalidentity.rc.controller.mvc.datatables.dao;

import org.springframework.data.jpa.datatables.repository.DataTablesRepository;

import dk.digitalidentity.rc.controller.mvc.datatables.dao.model.AuditLogView;

public interface AuditLogViewDao extends DataTablesRepository<AuditLogView, Long> {

}