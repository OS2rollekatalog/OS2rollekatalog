package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.AttestationOverviewService;
import dk.digitalidentity.rc.attestation.service.AttestationRunService;
import dk.digitalidentity.rc.attestation.service.ItSystemUserRolesAttestationService;
import dk.digitalidentity.rc.attestation.service.ItSystemUsersAttestationService;
import dk.digitalidentity.rc.attestation.service.ManagerDelegateAttestationService;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAnyAttestationEligibleRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static dk.digitalidentity.rc.attestation.service.AttestationOverviewService.buildItSystemsOverviews;
import static dk.digitalidentity.rc.attestation.service.AttestationOverviewService.buildItSystemsUsersOverviews;

@RequireAnyAttestationEligibleRole
@Controller
public class AttestationV2Controller {

	@Autowired
	private UserService userService;

	@Autowired
	private ItSystemUserRolesAttestationService itSystemUserRolesAttestationService;

	@Autowired
	private OrganisationAttestationService orgUserAttestationService;

	@Autowired
	private ItSystemUsersAttestationService itSystemUsersAttestationService;

	@Autowired
	private SecurityUtil securityUtil;

	@Autowired
	private AttestationOverviewService overviewService;

	@Autowired
	private AttestationRunService attestationRunService;

	@Autowired
	private ManagerDelegateAttestationService managerDelegateAttestationService;

	@GetMapping(value = "/ui/attestation/v2")
	@Timed("attestation.controller.mvc.attestation_v2_controller.index.timer")
	public String index(Model model) {
		final User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}
		final Optional<AttestationRun> currentRun = attestationRunService.getCurrentRun();
		final List<User> substituteFor = securityUtil.getManagersBySubstitute();
        final List<AttestationOverviewDTO> systemAttestations = currentRun.isPresent()
				? buildItSystemsOverviews(itSystemUserRolesAttestationService.getItSystemAttestationsForUser(currentRun.get(), user.getUuid()), false)
				: Collections.emptyList();
        final List<AttestationOverviewDTO> orgsForAttestation = currentRun.isPresent()
				? new ArrayList<>(overviewService.buildOrgUnitsOverviews(orgUserAttestationService.listOrganisationsForAttestation(currentRun.get(), user, substituteFor), false))
				: Collections.emptyList();
		final List<AttestationOverviewDTO> itSystemUsersAttestation = new ArrayList<>();
		if (currentRun.isPresent() && SecurityUtil.isSystemResponsible()) {
			itSystemUsersAttestation.addAll(buildItSystemsUsersOverviews(itSystemUsersAttestationService.listItSystemUsersForAttestation(currentRun.get(), user.getUuid()), false));
		}
		final List<User> delegateFor = managerDelegateAttestationService.getManagedUsersForDelegate(user);

		final List<AttestationOverviewDTO> managerDelegateAttestations = currentRun.isPresent()
				? new ArrayList<>(managerDelegateAttestationService.buildOrgUnitsOverviews(managerDelegateAttestationService.listOrganisationsForAttestation(currentRun.get(), delegateFor), user, false))
				: Collections.emptyList();

		model.addAttribute("orgUnits", orgsForAttestation);
		model.addAttribute("itSystems", systemAttestations);
		model.addAttribute("itSystemsForRoleAssignmentAttestation", itSystemUsersAttestation);
		model.addAttribute("managerDelegateAttestations", managerDelegateAttestations);
		return "attestationmodule/index";
	}
}
