package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.controller.api.dto.FunctionDTO;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.FunctionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequireApiOrganisationRole
@Slf4j
@RestController
@SecurityRequirement(name = "ApiKey")
@Tag(name = "Functions API")
public class FunctionApi {

	@Autowired
	private FunctionService functionService;


	@Operation(summary = "Read all active functions")
	@ApiResponses(value = {@ApiResponse(responseCode = "200", description = "List of active functions")})
	@RequestMapping(value = "/api/function", method = RequestMethod.GET)
	public List<FunctionDTO> list() {
		return functionService.getAllActive().stream()
			.map(f -> FunctionDTO.builder()
				.name(f.getName())
				.uuid(f.getUuid())
				.build())
			.toList();
	}


	@Operation(summary = "Set all active functions", description = "This method sets the active functions based on the provided names. " +
		"It activates existing functions and creates new ones if they don't exist.")
	@ApiResponses(value = {@ApiResponse(responseCode = "200")})
	@PostMapping(value = "/api/function")
	public ResponseEntity<?> save(@RequestBody @Valid Set<String> body) {
		List<Function> existingFunctions = functionService.getAllIncludingInactive();

		// Create or activate functions from payload
		for (String functionName : body) {
			Function function = existingFunctions.stream()
					.filter(f -> f.getName().equalsIgnoreCase(functionName))
					.findFirst()
					.orElse(null);

			if (function != null) {
				// Reactivate if needed
				if (!function.isActive()) {
					function.setActive(true);
					functionService.save(function);
				}
			} else {
				// Create new function
				function = new Function();
				function.setUuid(UUID.randomUUID().toString());
				function.setName(functionName);
				function.setActive(true);
				functionService.save(function);
			}
		}

		// Deactivate functions not in payload
		for (Function function : existingFunctions) {
			if (function.isActive() && body.stream().noneMatch(name -> name.equalsIgnoreCase(function.getName()))) {
				function.setActive(false);
				functionService.save(function);
			}
		}

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
