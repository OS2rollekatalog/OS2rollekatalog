package dk.digitalidentity.rc.controller.mvc;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationSettingsForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;
import dk.digitalidentity.rc.controller.validator.AttestationSettingFormValidator;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.rolerequest.model.dto.RequestConstraintDTO;
import dk.digitalidentity.rc.rolerequest.model.dto.RoleRequestSettingsDTO;
import dk.digitalidentity.rc.rolerequest.service.RequestConstraintService;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@Controller
public class SettingsController {
	private final SettingsService settingsService;
	private final OrgUnitService orgUnitService;
	private final AttestationSettingFormValidator attestationSettingFormValidator;
	private final NotificationService notificationService;
    private final RequestConstraintService constraintService;

	@InitBinder(value = "attestationSettingsForm")
    public void initBinder(WebDataBinder binder) {
        binder.addValidators(attestationSettingFormValidator);
    }

    @GetMapping(value = "/ui/settings")
    public String settings(Model model) {
        populateModel(model);

        return "setting/settings";
    }

    @GetMapping(value = "/ui/settings/attestation")
    public String attestationSettings(Model model) {
        populateModelAttestation(model);

        return "setting/attestation_settings";
    }

    @GetMapping(value = "/ui/settings/rolerequest")
    public String roleRequestSettings(Model model) {

        RoleRequestSettingsDTO settingsForm = new RoleRequestSettingsDTO();
        settingsForm.setReasonSetting(settingsService.getRolerequestReason());
        settingsForm.setOnlyRecommendRoles(settingsService.getOnlyRecommendRoles());
		settingsForm.setAlternativeEmails(settingsService.getRoleRequestApproverEmails());
		settingsForm.setApprovableByList(settingsService.getRolerequestApprover());
		settingsForm.setRequestableByList(settingsService.getRolerequestRequester());
		settingsForm.setServicedeskEmail(settingsService.getRequestApproveServicedeskEmail());
		settingsForm.setShowSingleTableInRequestApproveEnabled(settingsService.isShowSingleTableInRequestApproveEnabled());

        Set<RequestConstraintDTO> constraints = constraintService.getAllConstraints().stream()
            .map(constraint -> RequestConstraintDTO.builder()
                .id(constraint.getId())
                .value(constraint.getValue())
                .build())
            .collect(Collectors.toSet());
        settingsForm.setConstraints(constraints);

        model.addAttribute("settingsForm", settingsForm);

        return "setting/rolerequest_settings";
    }

    @PostMapping(value = "/ui/settings")
    public String updateSettings(Model model, @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
//            populateModel(model);
            model.addAttribute(bindingResult.getAllErrors());

            log.warn("Bad settingsform - unable to save");
            return "setting/settings";
        }

        boolean requestApproveBefore = settingsService.isRequestApproveEnabled();

        settingsService.setRequestApproveEnabled(settingsForm.isRequestApproveEnabled());
		settingsService.setItSystemChangeEmail(settingsForm.getItSystemChangeEmail());
		settingsService.setCaseNumberEnabled(settingsForm.isCaseNumberEnabled());
		settingsService.setAutomaticNiveauMapping(settingsForm.isAutoNiveauEnabled());
		settingsService.setRemoveDirectAssignmentsForDisabled(settingsForm.getRemoveDirectAssignmentsForDeleted());
		if (!settingsForm.isAutoNiveauEnabled()) {
			settingsService.clearExistingNiveauMappings();
		}
		else {
			settingsService.setNiveauMapping(settingsForm.getDepthToNiveauMappings());
		}
		settingsService.setExcludedOUs(settingsForm.getExcludedOUs());

        redirectAttributes.addFlashAttribute("saved", true);

        if (!requestApproveBefore && settingsForm.isRequestApproveEnabled()) {
            Notification notification = new Notification();
            notification.setActive(true);
            notification.setCreated(new Date());
            notification.setNotificationType(NotificationType.EDIT_REQUEST_APPROVE_EMAIL_TEMPLATE);

            notificationService.save(notification);
        }

        return "redirect:/ui/settings";
    }

	@Transactional
    @PostMapping(value = "/ui/settings/attestation")
    public String updateAttestationSettings(Model model, @Valid @ModelAttribute("attestationSettingsForm") AttestationSettingsForm attestationSettingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(bindingResult.getAllErrors());

            log.warn("Bad settingsform - unable to save");
            return "setting/attestation_settings";
        }

        boolean attestationBefore = settingsService.isScheduledAttestationEnabled();

        settingsService.setAttestationChangeEmail(attestationSettingsForm.getAttestationChangeEmail());
        settingsService.setScheduledAttestationEnabled(attestationSettingsForm.isScheduledAttestationEnabled());
        settingsService.setScheduledAttestationInterval(attestationSettingsForm.getScheduledAttestationInterval());

		if (attestationSettingsForm.isOrgUnitOptIn()) {
			settingsService.setScheduledAttestationOptedInOrgUnits(attestationSettingsForm.getScheduledAttestationOptedInOrgUnits());
		} else {
			settingsService.setScheduledAttestationFilter(attestationSettingsForm.getScheduledAttestationFilter());
		}

        settingsService.setADAttestationEnabled(attestationSettingsForm.isAdAttestationEnabled());
        settingsService.setFirstAttestationDate(attestationSettingsForm.getFirstAttestationDate());
        settingsService.setAttestationRequestChangesEnabled(attestationSettingsForm.isChangeRequestsEnabled());
        settingsService.setDontSendMailToManagerEnabled(attestationSettingsForm.isDontSendMailToManager());
		settingsService.setAttestationDescriptionRequired(attestationSettingsForm.isDescriptionRequired());
		settingsService.setAttestationHideDescription(attestationSettingsForm.isHideDescription());
		settingsService.setAttestationOrgUnitSelectionOptIn(attestationSettingsForm.isOrgUnitOptIn());

        redirectAttributes.addFlashAttribute("saved", true);

        if (!attestationBefore && attestationSettingsForm.isScheduledAttestationEnabled()) {
            Notification notification = new Notification();
            notification.setActive(true);
            notification.setCreated(new Date());
            notification.setNotificationType(NotificationType.EDIT_ATTESTATION_EMAIL_TEMPLATE);

            notificationService.save(notification);
        }

        return "redirect:/ui/settings/attestation";
    }

    @PostMapping(value = "/ui/settings/rolerequest")
    public String updateRoleRequestSettings(Model model, @Valid @ModelAttribute("settingsForm") RoleRequestSettingsDTO settingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(bindingResult.getAllErrors());
            log.warn("Bad settingsform - unable to save");
            return "setting/attestation_settings";
        }

        settingsService.setRolerequestRequester(settingsForm.getRequestableByList());
        settingsService.setRolerequestApprover(settingsForm.getApprovableByList());
        settingsService.setShowSingleTableInRequestApproveEnabled(settingsForm.isShowSingleTableInRequestApproveEnabled());
        settingsService.setRolerequestReason(settingsForm.getReasonSetting());
        settingsService.setOnlyRecommendRoles(settingsForm.isOnlyRecommendRoles());
		settingsService.setRoleRequestApproverEmails(settingsForm.getAlternativeEmails());
		settingsService.setRequestApproveServicedeskEmail(settingsForm.getServicedeskEmail());
        redirectAttributes.addFlashAttribute("saved", true);
        return "redirect:/ui/settings/rolerequest";
    }

    private void populateModel(Model model) {
        SettingsForm settingsForm = new SettingsForm();

        settingsForm.setRequestApproveEnabled(settingsService.isRequestApproveEnabled());
        settingsForm.setItSystemChangeEmail(settingsService.getItSystemChangeEmail());
		settingsForm.setExcludedOUs(settingsService.getExcludedOUs());
		settingsForm.setCaseNumberEnabled(settingsService.isCaseNumberEnabled());
		settingsForm.setAlternativeEmails(settingsService.getRoleRequestApproverEmails());
		settingsForm.setAutoNiveauEnabled(settingsService.isAutomaticNiveauMappingEnabled());
		settingsForm.setDepthToNiveauMappings(settingsService.getNiveauMapping());
		settingsForm.setRemoveDirectAssignmentsForDeleted(settingsService.getRemoveDirectAssignmentsForDisabled());

		List<OUListForm> allOUs = orgUnitService.getAllCachedIncludingExcluded()
				.stream()
				.map(ou -> new OUListForm(ou, false))
				.collect(Collectors.toList());

		model.addAttribute("allOUs", allOUs);
        model.addAttribute("settingsForm", settingsForm);
    }

    private void populateModelAttestation(Model model) {
        AttestationSettingsForm settingsForm = new AttestationSettingsForm();

        settingsForm.setAttestationChangeEmail(settingsService.getAttestationChangeEmail());
        settingsForm.setScheduledAttestationEnabled(settingsService.isScheduledAttestationEnabled());
        settingsForm.setScheduledAttestationInterval(settingsService.getScheduledAttestationInterval());
        settingsForm.setScheduledAttestationFilter(settingsService.getScheduledAttestationFilter());
        settingsForm.setAdAttestationEnabled(settingsService.isADAttestationEnabled());
        settingsForm.setFirstAttestationDate(settingsService.getFirstAttestationDate());
        settingsForm.setChangeRequestsEnabled(settingsService.isAttestationRequestChangesEnabled());
        settingsForm.setDontSendMailToManager(settingsService.isDontSendMailToManagerEnabled());
		settingsForm.setDescriptionRequired(settingsService.isAttestationDescriptionRequired());
		settingsForm.setHideDescription(settingsService.isAttestationHideDescription());
		settingsForm.setOrgUnitOptIn(settingsService.isAttestationOrgUnitSelectionOptIn());
		settingsForm.setScheduledAttestationOptedInOrgUnits(settingsService.getScheduledAttestationOptedInOrgUnits());

		List<OrgUnit> allOUs = orgUnitService.getAll();

		Set<String> filteredOUs = settingsForm.getScheduledAttestationFilter();
		Set<String> optedInOuSs = settingsForm.getScheduledAttestationOptedInOrgUnits();

		model.addAttribute("selectedOUs", filteredOUs);
		model.addAttribute("optedInOuSelection", optedInOuSs);
		model.addAttribute("allOUs", allOUs.stream()
				.map(ou -> new OUListForm(ou, false))
				.collect(Collectors.toList()));
        model.addAttribute("attestationSettingsForm", settingsForm);
    }
}
