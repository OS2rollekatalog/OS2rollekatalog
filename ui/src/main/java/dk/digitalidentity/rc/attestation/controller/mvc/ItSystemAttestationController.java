package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.model.dto.ItSystemAttestationDTO;
import java.util.List;
import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireItSystemResponsibleOrAttestationAdminRole;
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

@Controller
@RequireItSystemResponsibleOrAttestationAdminRole
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

		List<String> responsibleUuids = itSystemService.getAttestationResponsibleUuids(itSystem);
		if (!SecurityUtil.isAttestationAdminOrAdmin() && (responsibleUuids.isEmpty() || !responsibleUuids.contains(user.getUuid()))) {
			return "attestationmodule/error";
		}
		boolean openInView = SecurityUtil.isAttestationAdminOrAdmin() && (responsibleUuids.isEmpty() || !responsibleUuids.contains(user.getUuid()));
		ItSystemAttestationDTO attestation = attestationService.getAttestation(id, true);
		model.addAttribute("openInView", openInView);
		model.addAttribute("itsystem", attestation);
		model.addAttribute("totalCount", attestation.getUserRoles().size());
		model.addAttribute("changeRequestsEnabled", settingsService.isAttestationRequestChangesEnabled());
		model.addAttribute("attestationDescriptionRequired", settingsService.isAttestationDescriptionRequired());

		return "attestationmodule/itsystems/attestate";
	}
}
