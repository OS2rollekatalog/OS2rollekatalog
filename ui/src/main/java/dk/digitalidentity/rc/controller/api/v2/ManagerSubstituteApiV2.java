package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@RestController
@RequireApiOrganisationRole
@SecurityRequirement(name = "ApiKey")
@Tag(name = "Manager and substitutes API V2")
public class ManagerSubstituteApiV2 {

	@Autowired
    private UserService userService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Schema(name = "Manager")
    record ManagerRecord(
            @Schema(description = "Unique ID for manager, which is uuid from managers user profile") String uuid,
            @Schema(description = "Name of manager") String name,
            @Schema(description = "Username for manager") String userId,
            @Schema(description = "List of all assigned substitutes for manager") List<ManagerSubstituteRecord> managerSubstitutes) {}

    @Schema(name = "ManagerSubstitute")
    record ManagerSubstituteRecord(
            @Schema(description = "Unique ID for the substitute, which is uuid from subs user profile") String uuid,
            @Schema(description = "Name of the substitute") String name,
            @Schema(description = "Username for substitute") String userId,
            @Schema(description = "Unique id for organisation") String orgUnitUuid,
            @Schema(description = "Name of organisation") String orgUnitName,
            @Schema(description = "Unique Id of the manager, which is uuid from the managers user profile") String managerUuid,
            @Schema(description = "Username for manager") String managerUserId){}

    @ApiResponses(value={
            @ApiResponse(responseCode = "200", description = "Returns a list of all managers and their assigned substitutes"),
            @ApiResponse(responseCode = "404", description = "There was no Managers in the system"),
    })
    @Operation(summary = "Get all managers and their substitutes.")
    @GetMapping("/api/v2/manager")
    public ResponseEntity<List<ManagerRecord>> getAllManagers() {
        List<ManagerRecord> managerRecords = new ArrayList<>();
        List<User> managerList = userService.findManagers();

        if (managerList== null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        for (User user : managerList) {
            ManagerRecord manager = new ManagerRecord(user.getUuid(), user.getName(), user.getUserId(), new ArrayList<ManagerSubstituteRecord>());

            List<ManagerSubstitute> subs = user.getManagerSubstitutes();
            for(ManagerSubstitute sub : subs){
                var org = sub.getOrgUnit();
                var man = sub.getManager();
                var substitute = sub.getSubstitute();
                
                manager.managerSubstitutes.add(new ManagerSubstituteRecord(substitute.getUuid(), substitute.getName(),substitute.getUserId(),org.getUuid(),
                                                                            org.getName(),man.getUuid(),man.getUserId()));
            }

            managerRecords.add(manager);
        }
        
        return new ResponseEntity<>(managerRecords, HttpStatus.OK);
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns a collection of substitutes for the given manager"),
            @ApiResponse(responseCode = "400", description = "The body is incorrectly filled out or the requested manager, is not a manager"),
            @ApiResponse(responseCode = "404", description = "No manager found with given id")})
    @Operation(summary = "Get all substitutes for manager")
    @GetMapping("/api/v2/manager/{id}")
    public ResponseEntity<List<ManagerSubstituteRecord>> getManagersSubstitutes(@PathVariable("id") String id){
        //method contains all logic for this endpoint. Moved into method to reuse multiple times.
        return getSubstitutesForManagerById(id);
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the substitute, if successful"),
            @ApiResponse(responseCode = "400", description = "The given manager was not a valid manager or the body is invalid"),
            @ApiResponse(responseCode = "404", description = "No manager with id was found")
    })
    @Operation(summary = "Adds substitute to a manager")
    @PostMapping("/api/v2/manager")
    public ResponseEntity<ManagerSubstituteRecord> addSubstituteManager(@RequestBody ManagerSubstituteRecord managerSubstituteRecord){
        User manager = userService.getByUserId(managerSubstituteRecord.managerUserId);
		if (manager == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (managerSubstituteRecord == null || !userService.isManager(manager)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		ManagerSubstitute managerSubstitute = new ManagerSubstitute();
		managerSubstitute.setManager(userService.getByUserId(managerSubstituteRecord.managerUserId));
		managerSubstitute.setSubstitute(userService.getByUserId(managerSubstituteRecord.userId));
		managerSubstitute.setOrgUnit(orgUnitService.getByUuid(managerSubstituteRecord.orgUnitUuid));
		managerSubstitute.setAssignedTts(new Date());
		manager.getManagerSubstitutes().add(managerSubstitute);

        userService.save(manager);

        return new ResponseEntity<>(managerSubstituteRecord, HttpStatus.OK);
    }
    
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the substitute, if successful"),
            @ApiResponse(responseCode = "400", description = "The given manager was not a valid manager or the body is invalid"),
            @ApiResponse(responseCode = "404", description = "Manager or substitute not found")
    })
    @Operation(summary = "Update a single substitute for manager")
    @PutMapping("/api/v2/manager/{id}")
    public ResponseEntity<ManagerSubstituteRecord> updateManager(@RequestBody ManagerSubstituteRecord managerSubstituteRecord, @Parameter(description = "user-ID of the manager", example = "bsg") @PathVariable("id") String id) {
        User manager = userService.getByUserId(managerSubstituteRecord.managerUserId);
		if (manager == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (managerSubstituteRecord == null || !userService.isManager(manager)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
        ManagerSubstitute oldManagerSub = manager.getManagerSubstitutes().stream().filter(x -> x.getSubstitute().getUserId().equals(id)).findFirst().get();
        ManagerSubstitute managerSubstitute = new ManagerSubstitute();
        managerSubstitute.setManager(userService.getByUserId(managerSubstituteRecord.managerUserId));
        managerSubstitute.setSubstitute(userService.getByUserId(managerSubstituteRecord.userId));
        managerSubstitute.setOrgUnit(orgUnitService.getByUuid(managerSubstituteRecord.orgUnitUuid));
        managerSubstitute.setAssignedTts(new Date());

        Collections.replaceAll(manager.getManagerSubstitutes(),oldManagerSub,managerSubstitute);

        userService.save(manager);

        return new ResponseEntity<>(managerSubstituteRecord,HttpStatus.OK);
    }

    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Returns the updated substitute list for given manager, if successful"),
            @ApiResponse(responseCode = "400", description = "The given manager was not a valid manager or the body is invalid"),
            @ApiResponse(responseCode = "404", description = "Manager or substitute not found")
    })
    @Operation(summary = "Remove substitute for manager")
    @DeleteMapping("/api/v2/manager/{id}")
    public ResponseEntity<List<ManagerSubstituteRecord>> deleteManagerSubstitute(@Parameter(description = "user-ID of the manager", example = "bsg") @PathVariable("id") String id, @RequestBody ManagerSubstituteRecord managerSubstituteRecord){
        User manager = userService.getByUserId(managerSubstituteRecord.managerUserId());

		if (manager == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		else if (!userService.isManager(manager)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		List<ManagerSubstitute> oldManager = manager.getManagerSubstitutes();
        manager.getManagerSubstitutes().removeIf(x -> Objects.equals(String.valueOf(x.getSubstitute().getUserId()),managerSubstituteRecord.userId)  && Objects.equals(x.getOrgUnit().getUuid(),managerSubstituteRecord.orgUnitUuid));

		if (!manager.getManagerSubstitutes().equals(oldManager)) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

        userService.save(manager);

        return getSubstitutesForManagerById(managerSubstituteRecord.managerUserId());
    }

    public ResponseEntity<List<ManagerSubstituteRecord>> getSubstitutesForManagerById(String id){
        List<ManagerSubstituteRecord> managerSubstituteRecords = new ArrayList<>();
        User manager = userService.getByUserId(id);
        List<ManagerSubstitute> subManagers  = manager.getManagerSubstitutes();

        if (subManagers == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

		if (!userService.isManager(manager)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

        for (ManagerSubstitute sub : subManagers) {
            var org = sub.getOrgUnit();
            var man = sub.getManager();
            var substitute = sub.getSubstitute();
            managerSubstituteRecords.add(new ManagerSubstituteRecord(substitute.getUuid(), substitute.getName(),substitute.getUserId(),org.getUuid(), org.getName(),man.getUuid(),man.getUserId()));
        }

        return new ResponseEntity<>(managerSubstituteRecords,HttpStatus.OK);
    }
}
