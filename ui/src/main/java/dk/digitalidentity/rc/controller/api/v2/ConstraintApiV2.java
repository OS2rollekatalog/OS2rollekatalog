package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.controller.api.mapper.ConstraintMapper;
import dk.digitalidentity.rc.controller.api.model.ConstraintTypeAM;
import dk.digitalidentity.rc.security.RequireApiReadAccessRole;
import dk.digitalidentity.rc.service.ConstraintTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequireApiReadAccessRole
@SecurityRequirement(name = "ApiKey")
@Tag(name = "Constraint API V2")
public class ConstraintApiV2 {

    @Autowired
    private ConstraintTypeService constraintTypeService;

    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns the list of all constraint."),
            @ApiResponse(responseCode = "404", description = "No it-constraints were found") })
    @Operation(summary = "Get all constraint types", description = "Returns a list of all available constraint")
    @GetMapping("/api/v2/constraint")
    public List<ConstraintTypeAM> getAllConstraintTypes() {
        return constraintTypeService.getAll().stream().map(ConstraintMapper::toApi).toList();
    }

    @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Returns specific constraint type by id."),
            @ApiResponse(responseCode = "404", description = "Constraint type not found.") })
    @Operation(summary = "Get constraint by id", description = "Returns the specific constraint type by a given id")
    @GetMapping("/api/v2/constraint/{id}")
    public ConstraintTypeAM getConstraintTypeById(@PathVariable("id") Long id) {
        return constraintTypeService.findById(id).map(ConstraintMapper::toApi)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

}
