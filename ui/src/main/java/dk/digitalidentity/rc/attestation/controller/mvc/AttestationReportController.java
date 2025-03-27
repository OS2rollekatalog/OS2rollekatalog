package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.model.AttestationRunMapper;
import dk.digitalidentity.rc.attestation.model.dto.AttestationRunDTO;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.AttestationAdminService;
import dk.digitalidentity.rc.attestation.service.AttestationRunService;
import dk.digitalidentity.rc.attestation.service.ManualTransactionService;
import dk.digitalidentity.rc.attestation.service.OrganisationAttestationService;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.controller.mvc.AttestationViewHelpers.buildBreadcrumbs;

@Controller
public class AttestationReportController {

	@Autowired
	private UserService userService;

	@Autowired
	private OrganisationAttestationService organisationAttestationService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private AttestationAdminService attestationAdminService;

	record AttestationOrgUnitReportListDTO(String uuid, String name, String path) {}
	record AttestationITSystemReportListDTO(long id, String name) {}
	record AttestationRunReportListDTO(long id, String deadline, boolean finished, boolean sensitive, boolean extraSensitive) {}
	@GetMapping(value = "/ui/attestation/v2/reports")
	@Timed("attestation.controller.mvc.attestation_report_controller.reports.timer")
	public String reports(Model model) {
		User user = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElse(null);
		if (user == null) {
			return "attestationmodule/error";
		}

		boolean isAdmin = SecurityUtil.isAttestationAdminOrAdmin();
		Set<AttestationOrgUnitReportListDTO> managedOrgUnits;
		Set<AttestationITSystemReportListDTO> managedITSystems;

		final LocalDate when = LocalDate.now();

		final List<OrgUnit> allOrgUnitsWithAttestations = organisationAttestationService.getAllOrgUnitsWithAttestations(when);
		if (isAdmin) {
			managedOrgUnits = allOrgUnitsWithAttestations.stream().map(o -> new AttestationOrgUnitReportListDTO(o.getUuid(), o.getName(), buildBreadcrumbs(o))).collect(Collectors.toSet());
			managedITSystems = itSystemService.getVisible().stream()
					.filter(its -> !its.isAttestationExempt())
					.map(i -> new AttestationITSystemReportListDTO(i.getId(), i.getName()))
					.collect(Collectors.toSet());
		} else {
			managedOrgUnits = allOrgUnitsWithAttestations.stream()
					.filter(ou -> ou.getManager() != null && ou.getManager().getUuid().equals(user.getUuid()))
					.map(o -> new AttestationOrgUnitReportListDTO(o.getUuid(), o.getName(), buildBreadcrumbs(o))).collect(Collectors.toSet());
			managedITSystems = itSystemService.findByAttestationResponsible(user).stream()
					.filter(its -> !its.isAttestationExempt())
					.map(i -> new AttestationITSystemReportListDTO(i.getId(), i.getName()))
					.collect(Collectors.toSet());
			managedOrgUnits.addAll(user.getSubstituteFor().stream().map(o -> new AttestationOrgUnitReportListDTO(o.getOrgUnit().getUuid(), o.getOrgUnit().getName(), buildBreadcrumbs(o.getOrgUnit()))).collect(Collectors.toSet()));
		}

		if (managedITSystems.isEmpty() && managedOrgUnits.isEmpty()) {
			return "attestationmodule/error";
		}

		model.addAttribute("orgUnits", managedOrgUnits);
		model.addAttribute("itSystems", managedITSystems);
		model.addAttribute("isAdmin", isAdmin);
		model.addAttribute("since", LocalDate.now().minusYears(1)
				.format(DateTimeFormatter.ofPattern("dd/MM-yyyy")));

		if (isAdmin) {
			List<AttestationRun> attestationRuns = attestationAdminService.findAllRunsSorted();
			model.addAttribute("attestationRuns", attestationRuns.stream().map(r -> new AttestationRunReportListDTO(r.getId(), r.getDeadline().format(DateTimeFormatter.ofPattern("dd/MM-yyyy")), r.isFinished(), r.isSensitive(), r.isExtraSensitive())).collect(Collectors.toList()));
		}

		return "attestationmodule/reports/reports";
	}

}
