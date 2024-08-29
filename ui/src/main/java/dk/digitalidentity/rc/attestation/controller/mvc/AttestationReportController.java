package dk.digitalidentity.rc.attestation.controller.mvc;

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

	record AttestationOrgUnitReportListDTO(String uuid, String name, String path) {}
	record AttestationITSystemReportListDTO(long id, String name) {}
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

		final List<OrgUnit> allOrgUnitsWithAttestations = organisationAttestationService.getAllOrgUnitsWithAttestations();
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

		return "attestationmodule/reports/reports";
	}

}
