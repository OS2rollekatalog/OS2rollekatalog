package dk.digitalidentity.rc.controller.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.log.MemoryLogger;
import dk.digitalidentity.rc.security.RequireApiAuditlogAccessRole;

@RequireApiAuditlogAccessRole
@RestController
public class InternalApi {

	@Autowired
	private MemoryLogger memoryLogger;

	@GetMapping("/api/internal/memory")
	public ResponseEntity<?> dumpMemory() {
		memoryLogger.logMemoryMetrics();

		return ResponseEntity.ok().build();
	}

}
