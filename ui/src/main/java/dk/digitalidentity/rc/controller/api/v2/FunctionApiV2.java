package dk.digitalidentity.rc.controller.api.v2;

import dk.digitalidentity.rc.controller.api.dto.FunctionDTO;
import dk.digitalidentity.rc.controller.api.mapper.FunctionMapper;
import dk.digitalidentity.rc.controller.api.model.ExceptionResponseAM;
import dk.digitalidentity.rc.dao.model.Function;
import dk.digitalidentity.rc.security.RequireApiOrganisationRole;
import dk.digitalidentity.rc.service.FunctionService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequireApiOrganisationRole
@SecurityRequirement(name = "ApiKey")
@RequiredArgsConstructor
@Tag(name = "Function API V2")
public class FunctionApiV2 {
	private final FunctionService functionService;

	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Returns a list of all functions."),
	})
	@Operation(summary = "Get all functions.", description = "Returns all the active funtions.")
	@GetMapping("/api/v2/function")
	public List<FunctionDTO> getAll() {
		return functionService.getAllActive().stream()
			.map(FunctionMapper::functionToApi)
			.collect(Collectors.toList());
	}

	@Operation(summary = "Create a new function")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "201", description = "Function successfully created."),
	})
	@PostMapping(value = "/api/v2/function")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public FunctionDTO create(@RequestBody @Valid @NotNull FunctionDTO function) {
		// Functions are deleted by setting active to false, so check if this is actually an old function that is being reactivated.
		final Function f = Optional.ofNullable(function.getUuid())
			.flatMap(functionService::findByUuid)
			.orElseGet(() -> {
				function.setUuid(UUID.randomUUID().toString());
				return FunctionMapper.functionToEntity(function);
			});
		f.setActive(true);
		final Function savedFunction = functionService.save(f);
		return FunctionMapper.functionToApi(savedFunction);
	}

	@Operation(summary = "Update an existing function")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "UserRole was successfully updated"),
		@ApiResponse(responseCode = "400", content = {
			@Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class))
		})
	})
	@PutMapping(value = "/api/v2/function/{uuid}")
	@Transactional
	public void update(@PathVariable final String uuid, @RequestBody @Valid @NotNull FunctionDTO function) {
		final Function f = functionService.findByUuid(uuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		f.setName(function.getName());
		functionService.save(f);
	}

	@Operation(summary = "Delete a function")
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Function was successfully deleted")
	})
	@DeleteMapping(value = "/api/v2/function/{uuid}")
	@Transactional
	public void delete(@PathVariable final String uuid) {
		final Function f = functionService.findByUuid(uuid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		f.setActive(false);
		functionService.save(f);
	}

	@Operation(
		summary = "Sync functions",
		description = """
        Synchronizes the full list of functions. Functions not present in the payload will be deactivated.

        Resolution order when no UUID is provided:
        1. Look up by UUID if present
        2. Fall back to name lookup
        3. Create new function with generated UUID if no match is found
        """
	)
	@ApiResponses(value = {
		@ApiResponse(responseCode = "200", description = "Functions successfully synced."),
		@ApiResponse(responseCode = "400", content = {
			@Content(mediaType = "application/json", schema = @Schema(implementation = ExceptionResponseAM.class))
		})
	})
	@PostMapping(value = "/api/v2/function/sync")
	@Transactional
	public List<FunctionDTO> sync(@RequestBody @Valid @NotNull List<FunctionDTO> functions) {
		if (functions == null || functions.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "List must not be empty");
		}

		final Set<String> incomingUuids = functions.stream()
			.map(FunctionDTO::getUuid)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());

		// Deaktiver alle der ikke er med i den indkommende liste
		functionService.getAllActive().stream()
			.filter(f -> !incomingUuids.contains(f.getUuid()))
			.forEach(f -> f.setActive(false));

		// Opret eller genaktiver + opdater dem der er med
		final List<Function> synced = functions.stream()
			.map(dto -> {
				final Function f = Optional.ofNullable(dto.getUuid())
					.flatMap(functionService::findByUuid)
					.or(() -> functionService.findByName(dto.getName()))
					.orElseGet(() -> {
						final Function newF = FunctionMapper.functionToEntity(dto);
						newF.setUuid(UUID.randomUUID().toString());
						return newF;
					});
				f.setName(dto.getName());
				f.setActive(true);
				return f;
			})
			.collect(Collectors.toList());

		return functionService.saveAll(synced).stream()
			.map(FunctionMapper::functionToApi)
			.collect(Collectors.toList());
	}

}
