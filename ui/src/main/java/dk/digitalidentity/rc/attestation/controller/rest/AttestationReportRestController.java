package dk.digitalidentity.rc.attestation.controller.rest;

import dk.digitalidentity.rc.attestation.controller.mvc.xlsview.RoleAssignmentXlsView;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.AttestationAdminService;
import dk.digitalidentity.rc.attestation.service.AttestationLockService;
import dk.digitalidentity.rc.attestation.service.report.AttestationReportService;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.AttestationConstants.REPORT_LOCK_NAME;

@RestController
public class AttestationReportRestController {

    @Autowired
    private UserService userService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private AttestationReportService attestationReportService;

    @Autowired
    private AttestationLockService lockService;

    @Autowired
    private ItSystemService itSystemService;

    @Autowired
    private AttestationAdminService attestationAdminService;

    @GetMapping("/rest/attestation/v2/reports/")
    public ResponseEntity<?> busy() {
        if (lockService.isLocked(REPORT_LOCK_NAME)) {
            return new ResponseEntity<>(HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }


    @GetMapping("/rest/attestation/v2/reports/download/all")
    @Timed("attestation.controller.mvc.attestation_report_controller.download_all.timer")
    public ModelAndView downloadAll(HttpServletResponse response, Locale locale,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        if (!SecurityUtil.isAttestationAdminOrAdmin()) {
            return new ModelAndView("attestationmodule/error", new HashMap<>());
        }
        final LocalDate when = LocalDate.now();
        final Map<String, Object> model = attestationReportService
                .getAllReportModel(locale, since != null ? since : when.minusYears(1), when);

        response.setContentType("application/ms-excel");
        response.setHeader("Content-Disposition", "attachment; filename=\"historiske_rolletildelinger_alle.xlsx\"");

        return new ModelAndView(new RoleAssignmentXlsView(lockService), model);
    }

    @GetMapping("/rest/attestation/v2/reports/download/orgunits/{ouUuid}")
    @Timed("attestation.controller.mvc.attestation_report_controller.download_org_unit.timer")
    public ModelAndView downloadOrgUnit(HttpServletResponse response, Locale locale, @PathVariable String ouUuid,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        Map<String, Object> model = new HashMap<>();
        final OrgUnit orgUnit = orgUnitService.getByUuid(ouUuid);
        final User user = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElse(null);
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

        final LocalDate when = LocalDate.now();
        model = attestationReportService.getOrgUnitReportModel(orgUnit, locale, since != null ? since : when.minusYears(1), when);

        response.setContentType("application/ms-excel");
        response.setHeader("Content-Disposition", "attachment; filename=\"historiske_rolletildelinger_" + orgUnit.getName() + ".xlsx\"");

        return new ModelAndView(new RoleAssignmentXlsView(lockService), model);
    }

    @GetMapping("/rest/attestation/v2/reports/download/itsystems/{id}")
    @Timed("attestation.controller.mvc.attestation_report_controller.download_it_system.timer")
    public ModelAndView downloadITSystem(HttpServletResponse response, Locale locale, @PathVariable long id,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        Map<String, Object> model = new HashMap<>();
        final ItSystem itSystem = itSystemService.getById(id);
        final User user = userService.getOptionalByUserId(SecurityUtil.getUserId()).orElse(null);
        if (itSystem == null || user == null) {
            return new ModelAndView("attestationmodule/error", model);
        }
        if ((itSystem.getAttestationResponsible() == null || !itSystem.getAttestationResponsible().getUuid().equals(user.getUuid())) &&
                !SecurityUtil.isAttestationAdminOrAdmin()) {
            return new ModelAndView("attestationmodule/error", model);
        }

        LocalDate when = LocalDate.now();
        model = attestationReportService.getItSystemReportModel(itSystem, locale, since != null ? since : when.minusYears(1), when);

        response.setContentType("application/ms-excel");
        response.setHeader("Content-Disposition", "attachment; filename=\"historiske_rolletildelinger_" + itSystem.getName() + ".xlsx\"");

        return new ModelAndView(new RoleAssignmentXlsView(lockService), model);
    }

    @GetMapping("/rest/attestation/v2/reports/download/audit/{attestationRunId}")
    @Timed("attestation.controller.mvc.attestation_report_controller.download_audit.timer")
    public ModelAndView downloadAuditReport(HttpServletResponse response, Locale locale,
            @PathVariable long attestationRunId) {
        if (!SecurityUtil.isAttestationAdminOrAdmin()) {
            return new ModelAndView("attestationmodule/error", new HashMap<>());
        }

        AttestationRun attestationRun = attestationAdminService.findById(attestationRunId).stream().findAny().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attestation run not found"));

        AttestationRun nextRun = attestationAdminService.findNextRun(attestationRun.getCreatedAt());
        LocalDate until = nextRun == null ? LocalDate.now() : nextRun.getCreatedAt();
        final Map<String, Object> model = attestationReportService
                .getAuditReportModel(locale, attestationRun.getCreatedAt(), until, attestationRun);

        // TODO amalie tilpas med fane til users

        response.setContentType("application/ms-excel");
        response.setHeader("Content-Disposition", "attachment; filename=\"revisionsrapport.xlsx\"");

        return new ModelAndView(new RoleAssignmentXlsView(lockService), model);
    }


}
