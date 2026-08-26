package dk.digitalidentity.rc.attestation.controller.mvc;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import dk.digitalidentity.rc.attestation.model.dto.ItSystemRoleAttestationDTO;
import dk.digitalidentity.rc.attestation.model.dto.RoleAssignmentDTO;
import dk.digitalidentity.rc.attestation.service.ItSystemUsersAttestationService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireItSystemResponsibleOrAttestationAdminRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequireItSystemResponsibleOrAttestationAdminRole
@Controller
public class ItSystemRoleAssignmentAttestationController {

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private ItSystemUsersAttestationService itSystemUsersAttestationService;

	@Autowired
	private SettingsService settingsService;

	@GetMapping(value = "/ui/attestation/v2/itsystems/{id}/roleassignments")
	@Timed("attestation.controller.mvc.it_system_role_assignment_attestation_controller.index.timer")
	public String index(Model model, @PathVariable long id) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("No user logged in");
			return "attestationmodule/error";
		}

		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			log.warn("it-system with id " + id + " does not exist");
			return "attestationmodule/error";
		}

		List<ItSystem> managedItSystems = itSystemService.findByAttestationResponsible(user);
		boolean isNotResponsibleForItSystem = managedItSystems.stream().noneMatch(i -> i.getId() == id);
		if (isNotResponsibleForItSystem && !SecurityUtil.isAttestationAdminOrAdmin()) {
			log.warn("No access to attestation for it-system " + id + " for user " + user.getUserId());
			return "attestationmodule/error";
		}

		ItSystemRoleAttestationDTO attestation = itSystemUsersAttestationService.getAttestation(id, true, user.getUuid());

		model.addAttribute("attestation", attestation);
		model.addAttribute("totalCount",attestation.getUsers().size());
		model.addAttribute("orgUnitTotalCount", attestation.getOrgUnits().size());
		model.addAttribute("changeRequestsEnabled", settingsService.isAttestationRequestChangesEnabled());
		// Read-only when an admin opens a system they are not responsible for, or when the attestation is
		// already verified (opened via the "eye" on the overview) — a completed attestation cannot be re-attested.
		model.addAttribute("openInView", attestation.getVerifiedAt() != null || (isNotResponsibleForItSystem && SecurityUtil.isAttestationAdminOrAdmin()));
		model.addAttribute("attestationDescriptionRequired", settingsService.isAttestationDescriptionRequired());
		return "attestationmodule/itsystems/roleAssignmentAttestation";
	}

	@GetMapping(value = "/ui/attestation/v2/itsystems/{attestationUuid}/users/{userUuid}/userRemarkFragment")
	@Timed("attestation.controller.mvc.it_system_role_assignment_attestation_controller.user_remark_fragment.timer")
	public String userRemarkFragment(Model model, @PathVariable String attestationUuid, @PathVariable String userUuid, @RequestParam int number) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		List<RoleAssignmentDTO> roles = itSystemUsersAttestationService.getUserRoleAssignments(attestationUuid, userUuid);

		model.addAttribute("roleAssignments", roles);
		model.addAttribute("number", number);
		model.addAttribute("userUuid", userUuid);
		model.addAttribute("openInView", false);
		model.addAttribute("attestationDescriptionRequired", settingsService.isAttestationDescriptionRequired());

		return "attestationmodule/fragments/userRemarkModal :: userRemarkModal";
	}
}
