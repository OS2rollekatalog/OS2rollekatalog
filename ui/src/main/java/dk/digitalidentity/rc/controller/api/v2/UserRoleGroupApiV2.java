package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.controller.api.mapper.RoleGroupMapper;
import dk.digitalidentity.rc.controller.api.mapper.UserMapper;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.controller.api.model.RoleGroupAM;
import dk.digitalidentity.rc.controller.api.model.UserAM2;
import dk.digitalidentity.rc.controller.api.model.UserRoleAssignmentAM;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.security.RequireApiRoleManagementRole;
import dk.digitalidentity.rc.service.RoleGroupService;
import dk.digitalidentity.rc.service.UserRoleService;
import dk.digitalidentity.rc.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequireApiReadAccessRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Role-group API V2")
public class UserRoleGroupApiV2 {
    private final RoleGroupService roleGroupService;
    private final UserRoleService userRoleService;
    private final UserService userService;

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns all existing rolegroups. Can be empty list."),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Operation(summary = "Get all rolegroups.", description = "Returns all existing rolegroups.")
    @GetMapping("/api/v2/rolegroup")
    public ResponseEntity<List<RoleGroupAM>> getAllRoleGroups() {
        final List<RoleGroupAM> roleGroups = roleGroupService.getAll().stream()
                .map(RoleGroupMapper::toApi)
                .collect(Collectors.toList());
        return new ResponseEntity<>(roleGroups,HttpStatus.OK);
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Get a rolegroup"),
            @ApiResponse(responseCode = "404", description = "Not Found", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Operation(summary = "Get a rolegroup by id")
    @GetMapping("/api/v2/rolegroup/{id}")
    public ResponseEntity<RoleGroupAM> getRoleGroup(@PathVariable final long id) {
        final RoleGroup roleGroup = roleGroupService.getOptionalById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new ResponseEntity<>(RoleGroupMapper.toApi(roleGroup), HttpStatus.OK);
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "No content"),
            @ApiResponse(responseCode = "404", description ="No rolegroup with the given id was not found.", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })
    })
    @Operation(summary = "Delete rolegroup.", description = "Deletes a rolegroup with the given id, as long as a rolegroup with the given id exist")
    @RequireApiRoleManagementRole
    @DeleteMapping("/api/v2/rolegroup/{id}")
    @Transactional
    public ResponseEntity<?> deleteRoleGroup(@PathVariable final long id) {
        final RoleGroup roleGroup = roleGroupService.getOptionalById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        roleGroupService.delete(roleGroup);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Rolegroup updated"),
            @ApiResponse(responseCode = "400", description = "Bad request", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
            @ApiResponse(responseCode = "404", description = "No rolegroup with the given id was found.", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })

    })
    @Operation(summary = "Update a rolegroup", description = "Update a rolegroup, does not support partial update, all values should be included")
    @PutMapping("/api/v2/rolegroup/{id}")
    @RequireApiRoleManagementRole
    @Transactional
    public ResponseEntity<?> updateRoleGroup(@PathVariable("id") final long roleGroupId, @RequestBody @Valid final RoleGroupAM roleGroupRecord) {
        if (roleGroupRecord.getId() != null && roleGroupRecord.getId() != roleGroupId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Id mismatch");
        }
        final RoleGroup roleGroup = roleGroupService.getOptionalById(roleGroupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        setRoleGroupProperties(roleGroupRecord, roleGroup);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);

    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Rolegroup created"),
            @ApiResponse(responseCode = "409", description = "Conflict", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),

    })
    @Operation(summary = "Create a rolegroup", description = "Creates a rolegroup and returns the newly created rolegroup.")
    @PostMapping("/api/v2/rolegroup")
    @RequireApiRoleManagementRole
    @Transactional
    public ResponseEntity<RoleGroupAM> createRoleGroup(@Valid @RequestBody final RoleGroupAM userRoleGroupRecord) {
        roleGroupService.getByName(userRoleGroupRecord.getName()).
                ifPresent(rg -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "A rolegroup with name already exists"); });
        final RoleGroup roleGroup = setRoleGroupProperties(userRoleGroupRecord, new RoleGroup());
        final RoleGroup result = roleGroupService.save(roleGroup);
        return new ResponseEntity<>(RoleGroupMapper.toApi(result), HttpStatus.CREATED);
    }


    @ApiResponses(value = {
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404", description = "RoleGroup not found", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) }),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content =
                    { @Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class)) })

    })
    @Operation(summary = "Get all users with a given rolegroup")
    @GetMapping("/api/v2/rolegroup/{id}/users")
    public ResponseEntity<List<UserAM2>> getByUsersByRoleGroupId(@PathVariable long id) {
        final RoleGroup roleGroup = roleGroupService.getOptionalById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        final List<UserAM2> result = userService.getByRoleGroupsIncludingInactive(roleGroup).stream()
                .map(UserMapper::toApi)
                .collect(Collectors.toList());
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    private RoleGroup setRoleGroupProperties(final RoleGroupAM userRoleGroupRecord, final RoleGroup target) {
        final Set<Long> currentUserRoleIds = target.getUserRoleAssignments() != null
                ? target.getUserRoleAssignments().stream().map(a -> a.getUserRole().getId()).collect(Collectors.toSet())
                : Collections.emptySet();
        final Set<Long> wantedUserRoleIds = userRoleGroupRecord.getUserRoles() != null
                ? userRoleGroupRecord.getUserRoles().stream().map(UserRoleAssignmentAM::getUserRoleId).collect(Collectors.toSet())
                : Collections.emptySet();
        final Set<Long> toRemove = new HashSet<>(currentUserRoleIds);
        toRemove.removeAll(wantedUserRoleIds);
        final Set<Long> toAdd = new HashSet<>(wantedUserRoleIds);
        toAdd.removeAll(currentUserRoleIds);
        if (target.getUserRoleAssignments() == null) {
            target.setUserRoleAssignments(new ArrayList<>());
        }
        toRemove.stream()
                .map(userRoleService::getOptionalById)
                .filter(Optional::isPresent)
                .forEach(ur -> roleGroupService.removeUserRole(target, ur.get()));
        toAdd.stream()
                .map(userRoleService::getOptionalById)
                .filter(Optional::isPresent)
                .forEach(ur -> roleGroupService.addUserRole(target, ur.get()));

        target.setName(userRoleGroupRecord.getName());
        target.setDescription(userRoleGroupRecord.getDescription());
        target.setUserOnly(userRoleGroupRecord.getUserOnly());
        target.setCanRequest(userRoleGroupRecord.getCanRequest());
        return target;
    }

}
