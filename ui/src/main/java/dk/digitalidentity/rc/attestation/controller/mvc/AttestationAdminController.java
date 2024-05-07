package dk.digitalidentity.rc.attestation.controller.mvc;


import dk.digitalidentity.rc.attestation.dao.ItSystemRoleAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.ItSystemUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.dao.OrganisationUserAttestationEntryDao;
import dk.digitalidentity.rc.attestation.model.entity.Attestation;
import dk.digitalidentity.rc.attestation.service.AttestationAdminService;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAdminOrAttestationAdminRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dk.digitalidentity.rc.attestation.controller.mvc.AttestationViewHelpers.buildBreadcrumbs;

@Controller
@RequireAdminOrAttestationAdminRole
public class AttestationAdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private OrgUnitService orgUnitService;

    @Autowired
    private ManagerSubstituteService managerSubstituteService;

    @Autowired
    private AttestationAdminService attestationAdminService;

    @Autowired
    private OrganisationUserAttestationEntryDao organisationUserAttestationEntryDao;
    @Autowired
    private ItSystemUserAttestationEntryDao itSystemUserAttestationEntryDao;
    @Autowired
    private ItSystemRoleAttestationEntryDao itSystemRoleAttestationEntryDao;

    public enum AttestationStatus { NOT_STARTED, ON_GOING, FINISHED };
    record AttestationStatusListDTO(String name, User manager, List<User> substitutes, String path, AttestationStatus status) {}

    @Transactional
    @GetMapping(value = "/ui/attestation/v2/admin")
    @Timed("attestation.controller.mvc.attestation_admin_controller.index.timer")
    public String index(final Model model) {
        User user = userService.getByUserId(SecurityUtil.getUserId());
        if (user == null) {
            return "attestationmodule/error";
        }

        final List<AttestationStatusListDTO> ouStatusList = toOuStatusList(attestationAdminService.findAllCurrentOrganisationAttestations());
        final List<AttestationStatusListDTO> itSystemStatusList = toSystemStatusList(attestationAdminService.findAllCurrentItSystemRoleAttestations(),
                attestationAdminService.findAllCurrentItSystemUserAttestations());

        model.addAttribute("ouStatusList", ouStatusList);
        model.addAttribute("itSystems", itSystemStatusList);
        return "attestationmodule/admin/index";
    }

    private List<AttestationStatusListDTO> toSystemStatusList(final List<Attestation> attestations, List<Attestation> userAttestations) {
        Stream<Attestation> allAttestations = Stream.concat(attestations.stream(), userAttestations.stream());
        return allAttestations
                .map(a -> {
                    final AttestationStatus status = findAttestationStatus(a);
                    return new AttestationStatusListDTO(a.getItSystemName(), null,
                            Collections.emptyList(), a.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ATTESTATION
                            ? "Rolleopbygning" : "Rolletildelinger", status);
                })
                .collect(Collectors.toList());
    }

    private List<AttestationStatusListDTO> toOuStatusList(final List<Attestation> attestations) {
        return attestations.stream()
                .map(a -> {
                    OrgUnit ou = orgUnitService.getByUuid(a.getResponsibleOuUuid());
                    if (ou == null) {
                        return null;
                    }
                    return new AttestationStatusListDTO(ou.getName(), ou.getManager(), managerSubstituteService.getSubstitutesForOrgUnit(ou), buildBreadcrumbs(ou), findAttestationStatus(a));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private AttestationStatus findAttestationStatus(final Attestation attestation) {
        if (attestation.getVerifiedAt() != null) {
            return AttestationStatus.FINISHED;
        }
        if (attestation.getAttestationType() == Attestation.AttestationType.ORGANISATION_ATTESTATION) {
            if (organisationUserAttestationEntryDao.countByAttestationId(attestation.getId()) > 0) {
                return AttestationStatus.ON_GOING;
            }
        } else if (attestation.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ATTESTATION) {

            if (itSystemUserAttestationEntryDao.countByAttestationId(attestation.getId()) > 0) {
                return AttestationStatus.ON_GOING;
            }
        } else if (attestation.getAttestationType() == Attestation.AttestationType.IT_SYSTEM_ROLES_ATTESTATION) {
            if (itSystemRoleAttestationEntryDao.countByAttestationId(attestation.getId()) > 0) {
                return AttestationStatus.ON_GOING;
            }
        }
        return AttestationStatus.NOT_STARTED;
    }

}
