package dk.digitalidentity.rc.attestation.controller.mvc;

import static dk.digitalidentity.rc.attestation.service.AttestationOverviewService.buildItSystemsOverviews;
import static dk.digitalidentity.rc.attestation.service.AttestationOverviewService.buildItSystemsUsersOverviews;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.OrganisationAttestationDTO;
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
import org.springframework.web.bind.annotation.PathVariable;

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
		// Only the newest run is shown on the overview. The detail views always resolve the latest
		// attestation per system/org-unit (findFirst...OrderByDeadlineDesc), so older runs could not be
		// opened correctly anyway.
		// TODO: settingsService.getMaxAttestationsToRenderOnOverview() (SETTING_SCHEDULED_ATTESTATION_OVERVIEW_MAX_RUNS_TO_RENDER)
		// is intentionally bypassed here — it stays settable in the admin UI but has no effect on this overview
		// until the detail views become run-aware (e.g. /itsystems/{id}?runId=...). Honor the setting again, or remove it.
		model.addAttribute("simpleAttestationRuns", attestationRunService.getLatestRuns(1));
		return "attestationmodule/index";
	}

	@GetMapping(value = "/ui/attestation/v2/run/{runId}")
	@Timed("attestation.controller.mvc.attestation_v2_controller.tab_content.timer")
	public String indexTabContent(@PathVariable Long runId, Model model) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}
		final AttestationRun run = attestationRunService.getRun(runId).orElse(null);
		if (run == null) {
			return "attestationmodule/error";
		}
		final List<User> substituteFor = securityUtil.getManagersBySubstitute();
		final List<AttestationOverviewDTO> systemAttestations = buildItSystemsOverviews(
				itSystemUserRolesAttestationService.getItSystemAttestationsForUser(run, user.getUuid()), false);
		// Always show only the org-units the current user can actually attest (manager/substitute), also for
		// admins. Previously admins saw ALL org-units here, but they open read-only for units the admin does
		// not manage — which looked like missing buttons. Admins get the org-wide oversight on the dedicated
		// admin overview (/ui/attestation/v2/admin) instead.
		final List<OrganisationAttestationDTO> orgAttestations =
				orgUserAttestationService.listOrganisationsForAttestation(run, user, substituteFor);
		final List<AttestationOverviewDTO> orgsForAttestation = new ArrayList<>(
				overviewService.buildOrgUnitsOverviews(orgAttestations, false, user.getUuid()));
		final List<AttestationOverviewDTO> itSystemUsersAttestation = new ArrayList<>();
		if (SecurityUtil.isSystemResponsible()) {
			itSystemUsersAttestation.addAll(buildItSystemsUsersOverviews(
					itSystemUsersAttestationService.listItSystemUsersForAttestation(run, user.getUuid()), false));
		}
		final List<User> delegateFor = managerDelegateAttestationService.getManagedUsersForDelegate(user);
		final List<AttestationOverviewDTO> managerDelegateAttestations = new ArrayList<>(
				managerDelegateAttestationService.buildOrgUnitsOverviews(
						managerDelegateAttestationService.listOrganisationsForAttestation(run, delegateFor), user, false));

		model.addAttribute("runId", runId);
		model.addAttribute("orgUnits", orgsForAttestation);
		model.addAttribute("itSystems", systemAttestations);
		model.addAttribute("itSystemsForRoleAssignmentAttestation", itSystemUsersAttestation);
		model.addAttribute("managerDelegateAttestations", managerDelegateAttestations);


		return "attestationmodule/index_tabcontent_fragment :: index_tabcontent";
	}
}
