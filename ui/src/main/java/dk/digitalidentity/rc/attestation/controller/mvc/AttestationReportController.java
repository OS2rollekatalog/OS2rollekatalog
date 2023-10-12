package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.controller.mvc.xlsview.RoleAssignmentXlsView;
import dk.digitalidentity.rc.attestation.service.AttestationReportService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.controller.mvc.AttestationViewHelpers.buildBreadcrumbs;

@Controller
public class AttestationReportController {

	@Autowired
	private UserService userService;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private AttestationReportService attestationReportService;

	record AttestationOrgUnitReportListDTO(String uuid, String name, String path) {}
	record AttestationITSystemReportListDTO(long id, String name) {}
	@GetMapping(value = "/ui/attestation/v2/reports")
	@Timed("attestation.controller.mvc.attestation_report_controller.reports.timer")
	public String reports(Model model) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			return "attestationmodule/error";
		}

		boolean isAdmin = SecurityUtil.isAttestationAdminOrAdmin();
		Set<AttestationOrgUnitReportListDTO> managedOrgUnits = new HashSet<>();
		Set<AttestationITSystemReportListDTO> managedITSystems = new HashSet<>();


		if (isAdmin) {
			managedOrgUnits = orgUnitService.getAll().stream().map(o -> new AttestationOrgUnitReportListDTO(o.getUuid(), o.getName(), buildBreadcrumbs(o))).collect(Collectors.toSet());
			managedITSystems = itSystemService.getAll().stream().map(i -> new AttestationITSystemReportListDTO(i.getId(), i.getName())).collect(Collectors.toSet());
		} else {
			managedOrgUnits = orgUnitService.getByManagerMatchingUser(user).stream().map(o -> new AttestationOrgUnitReportListDTO(o.getUuid(), o.getName(), buildBreadcrumbs(o))).collect(Collectors.toSet());
			managedITSystems = itSystemService.findByAttestationResponsible(user).stream().map(i -> new AttestationITSystemReportListDTO(i.getId(), i.getName())).collect(Collectors.toSet());
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

	@GetMapping("/rest/attestation/v2/reports/download/all")
	@Timed("attestation.controller.mvc.attestation_report_controller.download_all.timer")
	public ModelAndView downloadAll(HttpServletResponse response, Locale locale,
									@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
		Map<String, Object> model = new HashMap<>();
		if (!SecurityUtil.isAttestationAdminOrAdmin()) {
			return new ModelAndView("attestationmodule/error", model);
		}
		model = attestationReportService.getAllReportModel(locale, since != null ? since : LocalDate.now().minusYears(1));

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"historiske_rolletildelinger_alle.xlsx\"");

		return new ModelAndView(new RoleAssignmentXlsView(), model);
	}

	@GetMapping("/rest/attestation/v2/reports/download/orgunits/{ouUuid}")
	@Timed("attestation.controller.mvc.attestation_report_controller.download_org_unit.timer")
	public ModelAndView downloadOrgUnit(HttpServletResponse response, Locale locale, @PathVariable String ouUuid,
										@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
		Map<String, Object> model = new HashMap<>();
		OrgUnit orgUnit = orgUnitService.getByUuid(ouUuid);
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (orgUnit == null || user == null) {
			return new ModelAndView("attestationmodule/error", model);
		}

		if (!SecurityUtil.isAttestationAdminOrAdmin()) {
			Set<String> managedOrgUnits = orgUnitService.getByManagerMatchingUser(user).stream().map(o -> o.getUuid()).collect(Collectors.toSet());
			managedOrgUnits.addAll(user.getSubstituteFor().stream().map(o -> o.getOrgUnit().getUuid()).collect(Collectors.toSet()));

			if (!managedOrgUnits.contains(ouUuid)) {
				return new ModelAndView("attestationmodule/error", model);
			}
		}

		model = attestationReportService.getOrgUnitReportModel(orgUnit, locale, since != null ? since : LocalDate.now().minusYears(1));

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"historiske_rolletildelinger_" + orgUnit.getName() + ".xlsx\"");

		return new ModelAndView(new RoleAssignmentXlsView(), model);
	}

	@GetMapping("/rest/attestation/v2/reports/download/itsystems/{id}")
	@Timed("attestation.controller.mvc.attestation_report_controller.download_it_system.timer")
	public ModelAndView downloadITSystem(HttpServletResponse response, Locale locale, @PathVariable long id,
										 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
		Map<String, Object> model = new HashMap<>();
		ItSystem itSystem = itSystemService.getById(id);
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (itSystem == null || user == null) {
			return new ModelAndView("attestationmodule/error", model);
		}
		if (!itSystem.getAttestationResponsible().getUuid().equals(user.getUuid()) &&
				!SecurityUtil.isAttestationAdminOrAdmin()) {
			return new ModelAndView("attestationmodule/error", model);
		}

		model = attestationReportService.getItSystemReportModel(itSystem, locale, since != null ? since : LocalDate.now().minusYears(1));

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"historiske_rolletildelinger_" + itSystem.getName() + ".xlsx\"");

		return new ModelAndView(new RoleAssignmentXlsView(), model);
	}
}
