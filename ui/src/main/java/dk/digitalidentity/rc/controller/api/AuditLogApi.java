package dk.digitalidentity.rc.controller.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.AuditLogHeadDTO;
import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.log.AuditLogEntryDao;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;

@RequireApiReadAccessRole
@RestController
public class AuditLogApi {

	@Autowired
	private AuditLogEntryDao auditLogEntryDao;

	@GetMapping("/api/auditlog/head")
	@ResponseBody
	public ResponseEntity<?> getHeadIndex() {
		var dto = new AuditLogHeadDTO();
		dto.setHead(auditLogEntryDao.getMaxId());

		return ResponseEntity.ok(dto);
	}

	@GetMapping("/api/auditlog/read")
	public ResponseEntity<?> getLogs(@RequestParam(name = "offset", defaultValue = "0") int offset, @RequestParam(name = "size", defaultValue = "250") int size) {
		List<AuditLog> logs = auditLogEntryDao.findAllWithOffsetAndSize(offset, size);

		return ResponseEntity.ok(logs);
	}
}
