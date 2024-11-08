package dk.digitalidentity.rc.attestation.controller.mvc;

import dk.digitalidentity.rc.attestation.controller.mvc.xlsview.RunOverviewXlsView;
import dk.digitalidentity.rc.attestation.model.AttestationRunMapper;
import dk.digitalidentity.rc.attestation.model.dto.AdminAttestationDetailsDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationOverviewDTO;
import dk.digitalidentity.rc.attestation.model.dto.AttestationRunDTO;
import dk.digitalidentity.rc.attestation.model.dto.enums.AdminAttestationStatus;
import dk.digitalidentity.rc.attestation.model.dto.enums.AttestationAdminColor;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.model.entity.AttestationRun;
import dk.digitalidentity.rc.attestation.service.AttestationAdminService;
import dk.digitalidentity.rc.attestation.service.AttestationRunService;
import dk.digitalidentity.rc.attestation.service.ManualTransactionService;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAdminOrAttestationAdminRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequireAdminOrAttestationAdminRole
public class AttestationAdminController {
    @Autowired
    private UserService userService;
    @Autowired
    private AttestationAdminService attestationAdminService;
    @Autowired
    private AttestationRunService attestationRunService;
    @Autowired
    private AttestationRunMapper runMapper;
    @Autowired
    private ManualTransactionService manualTransactionService;

    @Transactional
    @GetMapping(value = "/ui/attestation/v2/admin")
    @Timed("attestation.controller.mvc.attestation_admin_controller.index.timer")
    public String index(final Model model) {
        User user = userService.getByUserId(SecurityUtil.getUserId());
        if (user == null) {
            return "attestationmodule/error";
        }
        final List<AttestationRunDTO> attestationRuns = manualTransactionService.doInReadOnlyTransaction(() ->
                runMapper.toRunDTOList(attestationAdminService.findNewestRuns(4)));
        model.addAttribute("attestationRuns", attestationRuns);
        return "attestationmodule/admin/index";
    }

    @Transactional
    @GetMapping(value = "/ui/attestation/v2/admin/details/{attestationId}")
    public String ouDetails(final Model model, final @PathVariable("attestationId") Long attestationId) {
        final AdminAttestationDetailsDTO attestationDetails = manualTransactionService.doInReadOnlyTransaction(() -> {
            final Attestation attestation = attestationAdminService.getAttestation(attestationId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            final AdminAttestationDetailsDTO details = attestationAdminService.findAttestationDetails(attestation);
            model.addAttribute("attestationDetails", details);
            model.addAttribute("color", adminColorForOverview(attestation, details.getOverview()));
            return details;
        });
        if (attestationDetails.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION) {
            return "attestationmodule/admin/details_roles";
        }
        return "attestationmodule/admin/details_users";
    }

    @GetMapping(value = "/ui/attestation/v2/admin/report/{runId}")
    public ModelAndView report(@PathVariable("runId") final Long runId, final HttpServletResponse response) {
        final AttestationRun run = attestationRunService.getRun(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        final Map<String, Object> model = new HashMap<>();
        model.put("run", runMapper.toRunDTO(run));

        response.setContentType("application/ms-excel");
        response.setHeader("Content-Disposition", "attachment; filename=\"attestations-oversigt.xlsx\"");
        return new ModelAndView(new RunOverviewXlsView(), model);
    }

    private AttestationAdminColor adminColorForOverview(final Attestation attestation, final AttestationOverviewDTO overview) {
        final AdminAttestationStatus attestationStatus = attestationAdminService.findAttestationStatus(attestation);
        return switch (attestationStatus) {
            case NOT_STARTED, ON_GOING -> overview.isPassedDeadline() ? AttestationAdminColor.RED : AttestationAdminColor.YELLOW;
            case FINISHED -> AttestationAdminColor.GREEN;
        };
    }

}
