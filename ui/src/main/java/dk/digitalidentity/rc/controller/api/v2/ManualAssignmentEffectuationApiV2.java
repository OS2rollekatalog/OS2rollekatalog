package dk.digitalidentity.rc.controller.api.v2;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.ManualAssignmentEffectuationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequireApiRoleManagementRole
@SecurityRequirement(name = "ApiKey")
@Tag(name = "Manual Assignment Effectuation API V2")
@RequiredArgsConstructor
public class ManualAssignmentEffectuationApiV2 {
	private final ManualAssignmentEffectuationService manualAssignmentEffectuationService;

	@Schema(name = "ManualAssignmentEffectuation")
	record ManualAssignmentEffectuationRecord(
			@Schema(description = "Unique ID of the effectuation task") long id,
			@Schema(description = "UUID of the user the assignment concerns") String userUuid,
			@Schema(description = "ID of the it-system the assignment concerns") long itSystemId,
			@Schema(description = "Name of the it-system the assignment concerns") String itSystemName,
			@Schema(description = "ID of the user-role being assigned or removed, if applicable") Long userRoleId,
			@Schema(description = "Name of the user-role being assigned or removed, if applicable") String userRoleName,
			@Schema(description = "Whether the role is being assigned (ASSIGN) or removed (REMOVE)") ManualAssignmentEffectuationOperation operation,
			@Schema(description = "When the task was created") LocalDateTime createdAt) {
	}

	@Schema(name = "CompleteManualAssignmentEffectuation")
	record CompleteManualAssignmentEffectuationRecord(
			@Schema(description = "Identifier of the performer completing the task - a user UUID, or any identifier for the calling integration/robot", requiredMode = Schema.RequiredMode.REQUIRED)
			@NotBlank String performerIdentifier,
			@Schema(description = "Optional free-text comment, e.g. credentials or notes for the end-user welcome mail") String comment) {
	}

	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns all pending manual-assignment-effectuation tasks.") })
	@Operation(summary = "Get all pending manual-assignment-effectuation tasks", description = "Returns every task with status PENDING, across all it-systems.")
	@GetMapping("/api/v2/manualeffectuation")
	public ResponseEntity<List<ManualAssignmentEffectuationRecord>> getPendingEffectuations() {
		List<ManualAssignmentEffectuationRecord> result = manualAssignmentEffectuationService.findAllPending().stream()
			.map(ManualAssignmentEffectuationApiV2::toRecord)
			.toList();

		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Task was marked as effectuated."),
			@ApiResponse(responseCode = "404", description = "No such effectuation task.")
	})
	@Operation(summary = "Mark a manual-assignment-effectuation task as executed",
		description = "Idempotent: completing an already-effectuated task is a no-op and still returns 200. "
			+ "For ASSIGN tasks, this triggers the configured welcome-email to the affected user (if enabled for the it-system).")
	@PostMapping("/api/v2/manualeffectuation/{id}/complete")
	public ResponseEntity<Void> completeEffectuation(
			@Parameter(description = "Unique ID of the effectuation task", example = "1") @PathVariable("id") long id,
			@RequestBody @Valid CompleteManualAssignmentEffectuationRecord body) {
		manualAssignmentEffectuationService.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such effectuation task: " + id));

		String comment = StringUtils.hasLength(body.comment()) ? body.comment() : null;
		manualAssignmentEffectuationService.markEffectuated(id, body.performerIdentifier(), comment);

		return new ResponseEntity<>(HttpStatus.OK);
	}

	private static ManualAssignmentEffectuationRecord toRecord(ManualAssignmentEffectuation effectuation) {
		return new ManualAssignmentEffectuationRecord(
			effectuation.getId(),
			effectuation.getUser().getUuid(),
			effectuation.getItSystem().getId(),
			effectuation.getItSystem().getName(),
			effectuation.getUserRole() != null ? effectuation.getUserRole().getId() : null,
			effectuation.getUserRole() != null ? effectuation.getUserRole().getName() : null,
			effectuation.getOperation(),
			effectuation.getCreatedAt());
	}
}
