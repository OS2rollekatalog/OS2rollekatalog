package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireItSystemResponsibleRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SettingsService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RequireItSystemResponsibleRole
@Controller
public class ItSystemAttestationController {

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemUserRolesAttestationService attestationService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SettingsService settingsService;

	@GetMapping(value = "/ui/attestation/v2/itsystems/{id}")
	@Timed("attestation.controller.mvc.it_system_attestation_controller.index.timer")
	public String index(Model model, @PathVariable long id) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		ItSystem itSystem = itSystemService.getById(id);
		if (itSystem == null) {
			return "attestationmodule/error";
		}

		if (itSystem.getAttestationResponsible() == null || !itSystem.getAttestationResponsible().getUuid().equals(user.getUuid())) {
			return "attestationmodule/error";
		}

		ItSystemAttestationDTO attestation = attestationService.getAttestation(id, true);
		model.addAttribute("itsystem", attestation);
		model.addAttribute("totalCount", attestation.getUserRoles().size());
		model.addAttribute("changeRequestsEnabled", settingsService.isAttestationRequestChangesEnabled());

		return "attestationmodule/itsystems/attestate";
	}
}
