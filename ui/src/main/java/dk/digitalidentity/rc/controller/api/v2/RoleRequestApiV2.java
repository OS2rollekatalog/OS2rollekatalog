package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.rolerequest.model.entity.RoleRequest;
import dk.digitalidentity.rc.dao.model.enums.RequestAction;
import dk.digitalidentity.rc.dao.model.enums.RequestApproveStatus;
import dk.digitalidentity.rc.rolerequest.log.RequestAuditLogger;
import dk.digitalidentity.rc.rolerequest.log.RequestLogEvent;
import dk.digitalidentity.rc.rolerequest.service.RequestService;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequestMapping(value = "/api/v2")
@RestController
@RequireApiRoleManagementRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Role Request API V2")
public class RoleRequestApiV2 {

    private final OrgUnitService orgUnitService;
    private final RequestService requestService;
    private final RequestAuditLogger requestLogger;

    public record RoleRequestResponseAM(
        Long id,
        String requesterName, String requesterUserId,
        String receiverName, String receiverUserId,
        String orgUnitName, String orgUnitUuid,
        LocalDate requestDate,
        String reason,
        String requestAction,
        List<RoleItemResponseAM> userRoles,
        List<RoleItemResponseAM> roleGroups
    ) {}

    public record RoleItemResponseAM(
        Long id,
        String name,
        String itSystemName,
        String description,
        LocalDate startDate,
        LocalDate endDate
    ) {}

    public record RoleRequestCreatedAM(
        String requestGroupIdentifier,
        List<Long> requestIds
    ) {}

    public record RoleRequestApprovalAM(@NotNull String approverUserId) {}

    public record RoleRequestRejectionAM(@NotNull String approverUserId, String comment) {}

    public record RoleItemAM(
        @NotNull Long id,
        LocalDate startDate,
        LocalDate stopDate
    ) {}

    public record RoleRequestAM(
        @NotNull String requesterUserId,
        @NotNull String receiverUserId,
        @NotNull String orgUnitUuid,
        List<RoleItemAM> userRoles,
        List<RoleItemAM> roleGroups,
        String reason,
        @NotNull RequestAction requestAction
    ) {}

    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Role request created successfully.", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = RoleRequestCreatedAM.class)) }),
        @ApiResponse(responseCode = "400", description = "Invalid request data", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid ApiKey", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "403", description = "Requester is not allowed to request one of the given roles", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "404", description = "User, OrgUnit or role not found", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Transactional
    @Operation(summary = "Create a role request for a user. The request will appear in the pending approval queue.")
    @PostMapping(value = "rolerequest")
    public ResponseEntity<?> createRoleRequest(@RequestBody @Valid RoleRequestAM body) {
        boolean hasUserRoles = body.userRoles() != null && !body.userRoles().isEmpty();
        boolean hasRoleGroups = body.roleGroups() != null && !body.roleGroups().isEmpty();
        if (!hasUserRoles && !hasRoleGroups) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one of userRoles or roleGroups must be non-empty");
        }

        User requester = requestService.resolveUserByUserId(body.requesterUserId(), "requester");
        User receiver = requestService.resolveUserByUserId(body.receiverUserId(), "receiver");

        OrgUnit orgUnit = orgUnitService.getOptionalByUuid(body.orgUnitUuid())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OrgUnit not found: " + body.orgUnitUuid()));

        UUID groupUuid = UUID.randomUUID();
        List<RoleRequest> requestGroup = new ArrayList<>();

        List<RoleItemAM> userRoles = body.userRoles() != null ? body.userRoles() : List.of();
        for (RoleItemAM item : userRoles) {
            requestGroup.add(
                    requestService.createRoleRequest(requester, receiver, orgUnit,
                            groupUuid, body.reason(), item.id(), null, item.startDate(), item.stopDate(), body.requestAction())
            );
        }

        List<RoleItemAM> roleGroups = body.roleGroups() != null ? body.roleGroups() : List.of();
        for (RoleItemAM item : roleGroups) {
            requestGroup.add(
                    requestService.createRoleRequest(requester, receiver, orgUnit, groupUuid, body.reason(), null,
                            item.id(), item.startDate(), item.stopDate(), body.requestAction())
            );
        }

        requestService.saveRequestGroupWithLogAndAutoApprove(requestGroup, requester);

        List<Long> ids = requestGroup.stream().map(RoleRequest::getId).toList();
        return new ResponseEntity<>(new RoleRequestCreatedAM(groupUuid.toString(), ids), HttpStatus.CREATED);
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Returns IDs of roles that the requester is allowed to request for the receiver."),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid ApiKey", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "404", description = "Requester or receiver user not found", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Operation(summary = "Returns IDs of roles that the requester user is allowed to request for the receiver user.")
    @GetMapping(value = "rolerequest/requestable-for-user")
    public ResponseEntity<List<Long>> getRequestableRoleIdsForUser(@RequestParam String requesterUserId, @RequestParam String receiverUserId) {
        User requester = requestService.resolveUserByUserId(requesterUserId, "requester");
        User receiver = requestService.resolveUserByUserId(receiverUserId, "receiver");
        List<Long> ids = requestService.getRequestableUserRoles(requester, receiver)
            .map(UserRole::getId)
            .toList();
        return ResponseEntity.ok(ids);
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Returns pending role requests where the given user is the receiver."),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid ApiKey", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "404", description = "Receiver user not found", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Operation(summary = "Returns pending role requests for the given receiver user.")
    @GetMapping(value = "rolerequest/pending")
    public ResponseEntity<List<RoleRequestResponseAM>> getPendingRequestsForReceiver(@RequestParam String receiverUserId) {
        User receiver = requestService.resolveUserByUserId(receiverUserId, "receiver");
        List<RoleRequestResponseAM> result = requestService.getPendingForReceiver(receiver).stream()
            .map(this::toResponseAM)
            .toList();
        return ResponseEntity.ok(result);
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Returns list of approvable pending role requests."),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid ApiKey", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "404", description = "Approver user not found", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Operation(summary = "Returns pending role requests approvable by the given user.")
    @GetMapping(value = "rolerequest/approvable")
    public ResponseEntity<List<RoleRequestResponseAM>> getApprovablePendingRequests(@RequestParam String approverUserId) {
        User approver = requestService.resolveUserByUserId(approverUserId, "approver");
        List<RoleRequestResponseAM> result = requestService.getPendingApprovableRequestsForUser(approver.getUuid()).stream()
            .map(this::toResponseAM)
            .toList();
        return ResponseEntity.ok(result);
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Role request was approved."),
        @ApiResponse(responseCode = "400", description = "Request is not pending, or service rejected the approval", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid ApiKey", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "403", description = "Approver is not allowed to approve this request", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "404", description = "RoleRequest or approver user not found", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Transactional
    @Operation(summary = "Approves the role request with the given id.")
    @PostMapping(value = "rolerequest/{requestId}/approve")
    public ResponseEntity<?> approveRoleRequest(@PathVariable Long requestId, @RequestBody @Valid RoleRequestApprovalAM body) {
        User approver = requestService.resolveUserByUserId(body.approverUserId(), "approver");
        RoleRequest request = requestService.getRoleRequestById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RoleRequest not found: " + requestId));
        if (!requestService.canApprove(request, approver)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not allowed to approve this request");
        }
        if (request.getStatus() != RequestApproveStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RoleRequest is not pending: " + requestId);
        }
        ResponseEntity<String> result = requestService.approveRequest(request);
        if (!result.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getBody());
        }
        requestLogger.logRequest(RequestLogEvent.APPROVE, request, null, approver);
        return ResponseEntity.ok().build();
    }

    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Role request was rejected."),
        @ApiResponse(responseCode = "400", description = "Request is not pending, or service rejected the operation", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "401", description = "Unauthorized - missing or invalid ApiKey", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "403", description = "Approver is not allowed to reject this request", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
        @ApiResponse(responseCode = "404", description = "RoleRequest or approver user not found", content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Transactional
    @Operation(summary = "Rejects the role request with the given id.")
    @PostMapping(value = "rolerequest/{requestId}/reject")
    public ResponseEntity<?> rejectRoleRequest(@PathVariable Long requestId, @RequestBody @Valid RoleRequestRejectionAM body) {
        User approver = requestService.resolveUserByUserId(body.approverUserId(), "approver");
        RoleRequest request = requestService.getRoleRequestById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RoleRequest not found: " + requestId));
        if (!requestService.canApprove(request, approver)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not allowed to reject this request");
        }
        if (request.getStatus() != RequestApproveStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RoleRequest is not pending: " + requestId);
        }
        ResponseEntity<String> result = requestService.rejectRequest(request, body.comment());
        if (!result.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getBody());
        }
        requestLogger.logRequest(RequestLogEvent.DENY, request, body.comment(), approver);
        return ResponseEntity.ok().build();
    }

    private RoleRequestResponseAM toResponseAM(RoleRequest r) {
        LocalDate requestDate = r.getRequestTimestamp() != null
            ? r.getRequestTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            : null;
        List<RoleItemResponseAM> userRoles = r.getUserRole() != null
            ? List.of(new RoleItemResponseAM(r.getUserRole().getId(), r.getUserRole().getName(),
                r.getUserRole().getItSystem() != null ? r.getUserRole().getItSystem().getName() : null,
                r.getUserRole().getDescription(), r.getStartDate(), r.getEndDate()))
            : List.of();
        List<RoleItemResponseAM> roleGroups = r.getRoleGroup() != null
            ? List.of(new RoleItemResponseAM(r.getRoleGroup().getId(), r.getRoleGroup().getName(),
                null, r.getRoleGroup().getDescription(), r.getStartDate(), r.getEndDate()))
            : List.of();
        return new RoleRequestResponseAM(
            r.getId(),
            r.getRequester() != null ? r.getRequester().getName() : null,
            r.getRequester() != null ? r.getRequester().getUserId() : null,
            r.getReceiver() != null ? r.getReceiver().getName() : null,
            r.getReceiver() != null ? r.getReceiver().getUserId() : null,
            r.getOrgUnit() != null ? r.getOrgUnit().getName() : null,
            r.getOrgUnit() != null ? r.getOrgUnit().getUuid() : null,
            requestDate,
            r.getReason(),
            r.getRequestAction() != null ? r.getRequestAction().name() : null,
            userRoles,
            roleGroups
        );
    }

}
