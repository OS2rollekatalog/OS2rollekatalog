package dk.digitalidentity.rc.controller.api.v2;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.api.dto.AuditLogHeadDTO;
import dk.digitalidentity.rc.dao.model.AuditLog;
import dk.digitalidentity.rc.dao.model.enums.EntityType;
import dk.digitalidentity.rc.dao.model.enums.EventType;
import dk.digitalidentity.rc.log.AuditLogEntryDao;
import dk.digitalidentity.rc.security.RequireApiAuditlogAccessRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequireApiAuditlogAccessRole
@SecurityRequirement(name = "ApiKey")
public class AuditLogApiV2 {
	
    @Autowired
    private AuditLogEntryDao auditLogEntryDao;

    record AuditLogHeadRecord(@Schema(description = "Index of header") long head) { }

    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns the head index as long"),
                            @ApiResponse(responseCode = "404", description = "No header found")})
    @Operation(summary = "Returns header for the audit log as long index")
    @GetMapping("/api/v2/auditlog/head")
    public ResponseEntity<AuditLogHeadRecord> getHeadIndex() {
    	AuditLogHeadDTO dto = new AuditLogHeadDTO();
        dto.setHead(auditLogEntryDao.getMaxId());
        
        AuditLogHeadRecord auditLogHeadRecord = new AuditLogHeadRecord(dto.getHead());

        return ResponseEntity.ok(auditLogHeadRecord);
    }

    record AuditLogRecord(@Schema(description = "Id of audit log") long id, @Schema(description = "Time of creation") Date timestamp,
                          @Schema(description = "Ip Address of the audit") String ipAddress, @Schema(description = "Username for user who was audited") String username,
                          @Schema(description = "Enumerated type ie. user role, IT System, etc") EntityType entityType,
                          @Schema(description = "Id of the entity as a long") String entityId, @Schema(description = "Name of the entity") String entityName,
                          @Schema(description = "Type of the event") EventType eventType, @Schema(description = "Enumerated type ie. user role, IT System, etc for the secondary entity") EntityType secondaryEntityType,
                          @Schema(description = "Id of the secondary entity as long") String secondaryEntityId, @Schema(description = "Name of the secondary entity") String secondaryEntityName, @Schema(description = "Description of the auditlog")String description) {
    }

    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns List of audit logs"),
            @ApiResponse(responseCode = "404", description = "No header found")})
    @Operation(summary = "Returns a list of the full body for the audit logs - Look at schema for details")
    @GetMapping("/api/v2/auditlog/read")
    public ResponseEntity<List<AuditLogRecord>> getLogs(@Parameter(description = "offset search parameter, defaults to 0", example = "0") @RequestParam(name = "offset", defaultValue = "0") int offset,
                                                        @Parameter(description = "Size search parameter, defaults to 250", example = "250") @RequestParam(name = "size", defaultValue = "250") int size) {
        List<AuditLogRecord> records = new ArrayList<>();
        List<AuditLog> logs = auditLogEntryDao.findAllWithOffsetAndSize(offset, size);

		if (logs == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

        for (AuditLog auditLog : logs) {
            if (auditLog != null) {
                records.add(new AuditLogRecord(auditLog.getId(), auditLog.getTimestamp(), auditLog.getIpAddress(), auditLog.getUsername(), auditLog.getEntityType(), auditLog.getEntityId(),
                        auditLog.getEntityName(), auditLog.getEventType(), auditLog.getSecondaryEntityType(), auditLog.getSecondaryEntityId(), auditLog.getSecondaryEntityName(), auditLog.getDescription()));
            }
        }

        return new ResponseEntity<>(records,HttpStatus.OK);
    }
}
