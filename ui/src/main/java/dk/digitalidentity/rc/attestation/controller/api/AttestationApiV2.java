package dk.digitalidentity.rc.attestation.controller.api;

import dk.digitalidentity.rc.attestation.model.dto.OrgUnitUserRoleAssignmentItSystemDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.UserAttestationDTO;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
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
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

@RequestMapping("/api/v2/attestation")
@RestController
@RequireApiRoleManagementRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Attestation API V2")
@Slf4j
public class AttestationApiV2 {

    private final OrganisationAttestationService orgAttestationService;
    private final UserService userService;
    private final OrgUnitService orgUnitService;

    // ---- DTOs ----

    public enum AttestationStateAM { UNSTARTED, IN_PROGRESS, FINISHED }

    public record AttestationConfirmAM(@NotNull String actingUserId) {}

    public record AttestationRoleAM(
            String title, String itSystemName, String description,
            LocalDate fromDate, LocalDate toDate) {}

    public record UserAttestationAM(
            String name, String userId,
            List<AttestationRoleAM> roles, boolean approved) {}

    public record OrgUnitRoleAttestationAM(String roleId, String title, String description) {}

    public record OrgUnitAttestationAM(
            String ouUuid,
            List<UserAttestationAM> userAttestations,
            List<OrgUnitRoleAttestationAM> orgUnitRoleAttestations,
            boolean orgUnitRolesApproved,
            UserAttestationAM managerRoleAttestation) {}

    public record InheritedOuRoleAM(String roleName, String description, String inheritedFromOuName, String itSystemName) {}

    public record OrgUnitAttestationStatusAM(
            String uuid, String name, AttestationStateAM state,
            LocalDate deadline, LocalDate nextAttestation) {}

    public record AttestationOverviewAM(
            LocalDate periodStart, LocalDate periodEnd,
            List<OrgUnitAttestationStatusAM> orgUnits) {}

    // ---- Endpoints ----

    @Operation(summary = "Get attestation overview for a manager.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attestation overview returned."),
        @ApiResponse(responseCode = "204", description = "No attestation run is currently active."),
        @ApiResponse(responseCode = "404", description = "User not found.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class)))
    })
    @GetMapping("/status")
    @Transactional(readOnly = true)
    public ResponseEntity<AttestationOverviewAM> getAttestationStatus(@RequestParam String userId) {
        User user = resolveUser(userId);
        OrganisationAttestationService.AttestationOverviewData overview = orgAttestationService.getOverviewForUser(user);
        if (overview == null) {
            return ResponseEntity.noContent().build();
        }
        List<OrgUnitAttestationStatusAM> orgUnits = overview.attestations().stream()
            .map(a -> toStatusFromEntity(a, overview.run(), overview.statusInfo(),
                overview.nextAttestationByOu().get(a.getResponsibleOuUuid())))
            .toList();
        return ResponseEntity.ok(new AttestationOverviewAM(overview.run().getCreatedAt(), overview.run().getDeadline(), orgUnits));
    }

    @Operation(summary = "Get full attestation details for an org unit.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Attestation returned."),
        @ApiResponse(responseCode = "403", description = "User does not manage, substitute for, or delegate for this orgUnit.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class))),
        @ApiResponse(responseCode = "404", description = "User or attestation not found.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class)))
    })
    @GetMapping("/{ouUuid}")
    @Transactional(readOnly = true)
    public OrgUnitAttestationAM getOrgUnitAttestation(
            @PathVariable String ouUuid, @RequestParam String userId) {
        User user = resolveUser(userId);
        orgAttestationService.ensureUserManagesOrgUnit(user, ouUuid);

        OrganisationAttestationDTO orgDto = orgAttestationService.getAttestation(ouUuid, user.getUuid(), false);
        if (orgDto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active attestation for OU: " + ouUuid);
        }

        List<UserAttestationAM> userAttestations = mapUserAttestations(orgDto.getUserAttestations());
        List<OrgUnitRoleAttestationAM> ouRoles = flattenOuRoles(orgDto.getOrgUnitUserRoleAssignmentsPrItSystem());

        UserAttestationDTO managerDto = orgAttestationService.getManagerDelegatedAttestationForDelegate(ouUuid, user);
        UserAttestationAM managerRoleAttestation = managerDto != null ? mapUserAttestation(managerDto) : null;

        return new OrgUnitAttestationAM(ouUuid, userAttestations, ouRoles,
            orgDto.isOrgUnitRolesVerified(), managerRoleAttestation);
    }

    @Operation(summary = "Confirm that a user's role assignments are correct.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "User attestation confirmed."),
        @ApiResponse(responseCode = "403", description = "Acting user does not manage or substitute for this org unit.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class))),
        @ApiResponse(responseCode = "404", description = "User not found.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class)))
    })
    @PostMapping("/{ouUuid}/user/{userId}/confirm")
    @Transactional
    public void confirmUserAttestation(
            @PathVariable String ouUuid, @PathVariable String userId,
            @RequestBody @Valid AttestationConfirmAM body) {
        log.debug("confirmUserAttestation: ouUuid={} userId={} actingUserId={}", ouUuid, userId, body.actingUserId());
        User attested = resolveUser(userId);
        orgAttestationService.verifyUser(ouUuid, attested.getUuid(), body.actingUserId(),
            Attestation.AttestationType.ORGANISATION_ATTESTATION);
        log.debug("confirmUserAttestation: success ouUuid={} userUuid={}", ouUuid, attested.getUuid());
    }

    @Operation(summary = "Confirm that the roles assigned directly to the org unit are correct.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Org unit role attestation confirmed."),
        @ApiResponse(responseCode = "403", description = "Acting user does not manage or substitute for this org unit.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class)))
    })
    @PostMapping("/{ouUuid}/roles/confirm")
    @Transactional
    public void confirmOrgUnitRoles(
            @PathVariable String ouUuid,
            @RequestBody @Valid AttestationConfirmAM body) {
        log.debug("confirmOrgUnitRoles: ouUuid={} actingUserId={}", ouUuid, body.actingUserId());
        orgAttestationService.acceptOrgUnitRoles(ouUuid, body.actingUserId(),
            Attestation.AttestationType.ORGANISATION_ATTESTATION);
        log.debug("confirmOrgUnitRoles: success ouUuid={}", ouUuid);
    }

    @Operation(summary = "Confirm the manager's own role assignments (delegate only).")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Manager attestation confirmed."),
        @ApiResponse(responseCode = "404", description = "OU not found or no manager configured.",
            content = @Content(schema = @Schema(implementation = ExceptionResponseAM.class)))
    })
    @PostMapping("/{ouUuid}/manager/confirm")
    @Transactional
    public void confirmManagerAttestation(@PathVariable String ouUuid, @RequestBody @Valid AttestationConfirmAM body) {
        OrgUnit ou = orgUnitService.getByUuid(ouUuid);

        if (ou == null || ou.getManager() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No manager found for OU: " + ouUuid);
        }

        orgAttestationService.verifyUser(ouUuid, ou.getManager().getUuid(), body.actingUserId(), Attestation.AttestationType.MANAGER_DELEGATED_ATTESTATION);
    }

    // ---- Private helpers ----

    private User resolveUser(String userId) {
        User user = userService.getByUserId(userId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        }
        return user;
    }

    private OrgUnitAttestationStatusAM toStatusFromEntity(
            Attestation a,
            AttestationRun run,
            OrganisationAttestationService.AttestationStatusInfo statusInfo,
            LocalDate nextAttestation) {
        AttestationStateAM state;
        if (a.getVerifiedAt() != null
                || orgAttestationService.isOrganisationAttestationDone(a, run, statusInfo)) {
            state = AttestationStateAM.FINISHED;
        } else if (statusInfo.attestationIdsWithUserEntries().contains(a.getId())
                || statusInfo.attestationIdsWithRoleEntries().contains(a.getId())) {
            state = AttestationStateAM.IN_PROGRESS;
        } else {
            state = AttestationStateAM.UNSTARTED;
        }
        return new OrgUnitAttestationStatusAM(a.getResponsibleOuUuid(), a.getResponsibleOuName(), state,
            state == AttestationStateAM.FINISHED ? null : a.getDeadline(), nextAttestation);
    }

    private List<UserAttestationAM> mapUserAttestations(List<UserAttestationDTO> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(this::mapUserAttestation).toList();
    }

    private UserAttestationAM mapUserAttestation(UserAttestationDTO dto) {
        List<AttestationRoleAM> roles = dto.getUserRolesPrItSystem() == null ? List.of() :
            dto.getUserRolesPrItSystem().stream()
                .filter(itSystem -> itSystem.getUserRoles() != null)
                .flatMap(itSystem -> itSystem.getUserRoles().stream()
                    .map(role -> new AttestationRoleAM(
                        role.getRoleName(), itSystem.getItSystemName(),
                        role.getRoleDescription(), role.getAssignedFrom(), role.getAssignedTo())))
                .toList();
        return new UserAttestationAM(dto.getUserName(), dto.getUserId(), roles,
            dto.getVerifiedByUserId() != null);
    }

    private List<OrgUnitRoleAttestationAM> flattenOuRoles(
            List<OrgUnitUserRoleAssignmentItSystemDTO> itSystems) {
        if (itSystems == null) {
            return List.of();
        }
        return itSystems.stream()
            .filter(its -> its.getUserRoles() != null)
            .flatMap(its -> its.getUserRoles().stream()
                .map(r -> new OrgUnitRoleAttestationAM(
                    String.valueOf(r.getRoleId()), r.getRoleName(), r.getRoleDescription())))
            .toList();
    }

}
