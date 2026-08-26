package dk.digitalidentity.rc.controller.rest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.ManualAssignmentEffectuation;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.enums.ManualAssignmentEffectuationOperation;
import dk.digitalidentity.rc.security.RequireManualEffectuationSystemOwnerOrAdministratorRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.ManualAssignmentEffectuationService;
import dk.digitalidentity.rc.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireManualEffectuationSystemOwnerOrAdministratorRole
@RestController
@RequiredArgsConstructor
public class ManualAssignmentEffectuationRestController {
	private final ManualAssignmentEffectuationService manualAssignmentEffectuationService;
	private final ItSystemService itSystemService;
	private final UserService userService;

	public record ManualAssignmentEffectuationDTO(
		long id,
		String userName,
		String userUuid,
		String itSystemName,
		String roleName,
		ManualAssignmentEffectuationOperation operation,
		String comment
	) { }

	public record CompleteEffectuationDTO(String comment) { }

	@GetMapping("/rest/manualeffectuation")
	public ResponseEntity<List<ManualAssignmentEffectuationDTO>> list(@RequestParam(required = false) Long itSystemId, @RequestParam(required = false) ManualAssignmentEffectuationOperation operation) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		boolean isAdmin = SecurityUtil.hasDirectAdminRole();

		List<ManualAssignmentEffectuation> pending;
		if (isAdmin) {
			pending = manualAssignmentEffectuationService.findAllPending();
		}
		else {
			List<Long> ownedItSystemIds = itSystemService.findBySystemOwner(user).stream().map(ItSystem::getId).toList();
			if (ownedItSystemIds.isEmpty()) {
				return new ResponseEntity<>(HttpStatus.FORBIDDEN);
			}

			pending = manualAssignmentEffectuationService.findPendingForItSystems(ownedItSystemIds);
		}

		List<ManualAssignmentEffectuationDTO> result = pending.stream()
			.filter(e -> itSystemId == null || e.getItSystem().getId() == itSystemId)
			.filter(e -> operation == null || e.getOperation() == operation)
			.map(this::toDTO)
			.toList();

		return ResponseEntity.ok(result);
	}

	@PostMapping("/rest/manualeffectuation/{id}/complete")
	public ResponseEntity<?> complete(@PathVariable long id, @RequestBody CompleteEffectuationDTO body) {
		User performer = userService.getByUserId(SecurityUtil.getUserId());
		if (performer == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		ManualAssignmentEffectuation effectuation = manualAssignmentEffectuationService.findById(id).orElse(null);
		if (effectuation == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		if (!SecurityUtil.hasDirectAdminRole() && !isSystemOwner(performer, effectuation.getItSystem())) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		manualAssignmentEffectuationService.markEffectuated(id, performer, body != null ? body.comment() : null);

		return ResponseEntity.ok().build();
	}

	private boolean isSystemOwner(User user, ItSystem itSystem) {
		Set<Long> ownedItSystemIds = itSystemService.findBySystemOwner(user).stream().map(ItSystem::getId).collect(Collectors.toSet());

		return ownedItSystemIds.contains(itSystem.getId());
	}

	private ManualAssignmentEffectuationDTO toDTO(ManualAssignmentEffectuation e) {
		return new ManualAssignmentEffectuationDTO(
			e.getId(),
			e.getUser().getName(),
			e.getUser().getUuid(),
			e.getItSystem().getName(),
			e.getUserRole() != null ? e.getUserRole().getName() : "",
			e.getOperation(),
			e.getComment()
		);
	}
}
