package dk.digitalidentity.rc.controller.api;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.FunctionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import java.util.stream.Collectors;

@RequireApiOrganisationRole
@Slf4j
@RestController
@SecurityRequirement(name = "ApiKey")
public class FunctionApi {

	@Autowired
	private FunctionService functionService;

	@Autowired
	private RoleCatalogueConfiguration configuration;

	@RequestMapping(value = "/api/function", method = RequestMethod.GET)
	public ResponseEntity<Set<String>> read() {
		return new ResponseEntity<>(
				functionService.getAllActive().stream()
						.map(Function::getName)
						.collect(Collectors.toSet()),
				HttpStatus.OK
		);
	}

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