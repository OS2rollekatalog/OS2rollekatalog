package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.viewmodel.AttestationSettingsForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.OUListForm;
import dk.digitalidentity.rc.controller.mvc.viewmodel.SettingsForm;
import dk.digitalidentity.rc.controller.validator.AttestationSettingFormValidator;
import dk.digitalidentity.rc.dao.model.Notification;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.enums.NotificationType;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.NotificationService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.SettingsService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequireAdministratorRole
@Controller
public class SettingsController {
	
	@Autowired
	private SettingsService settingsService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private AttestationSettingFormValidator attestationSettingFormValidator;
	
	@Autowired
	private NotificationService notificationService;

	@InitBinder(value="attestationSettingsForm")
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

	@PostMapping(value = "/ui/settings")
	public String updateSettings(Model model, @Valid @ModelAttribute("settingsForm") SettingsForm settingsForm, BindingResult bindingResult, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			//populateModel(model);
			model.addAttribute(bindingResult.getAllErrors());

			log.warn("Bad settingsform - unable to save");
			return "setting/settings";
		}
		
		boolean requestApproveBefore = settingsService.isRequestApproveEnabled();

		settingsService.setRequestApproveEnabled(settingsForm.isRequestApproveEnabled());
		settingsService.setRequestApproveServicedeskEmail(settingsForm.getServicedeskEmail());

		settingsService.setItSystemChangeEmail(settingsForm.getItSystemChangeEmail());
		settingsService.setCaseNumberEnabled(settingsForm.isCaseNumberEnabled());

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

	private void populateModel(Model model) {
		SettingsForm settingsForm = new SettingsForm();

		settingsForm.setRequestApproveEnabled(settingsService.isRequestApproveEnabled());
		settingsForm.setServicedeskEmail(settingsService.getRequestApproveServicedeskEmail());
		settingsForm.setItSystemChangeEmail(settingsService.getItSystemChangeEmail());
		settingsForm.setExcludedOUs(settingsService.getExcludedOUs());
		settingsForm.setCaseNumberEnabled(settingsService.isCaseNumberEnabled());

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


	private Set<String> buildInverseSelectionOfOUUuids(Collection<String> selectedUuids, Collection<OrgUnit> ous, String highestUnselectedUuid, int level) {
		if (level > 15) {
			log.warn("Recursive loop detected reached level ${}, current ou ids: ${}", level, String.join(", ", ous.stream().map(OrgUnit::getUuid).toList()));
			return new HashSet<>();
		}
		Collection<OrgUnit> activeOus = ous.stream().filter(OrgUnit::isActive).toList();

		Set<String> result = new HashSet<>();
		if (activeOus.isEmpty()) {
			if (highestUnselectedUuid != null) {
				result.add(highestUnselectedUuid);
			}
			return result;
		}

		if (selectedUuids.containsAll(activeOus.stream().map(OrgUnit::getUuid).toList())) {
			//all in list is selected, return only the highestUnselectedUuid
			if (highestUnselectedUuid != null) {
				result.add(highestUnselectedUuid);
			}
			return result;
		} else if(activeOus.stream().noneMatch(orgUnit -> selectedUuids.contains(orgUnit.getUuid()))){
			//none is checked, keep searching with unchanged highestUnselectedUuid
			for ( var ou : activeOus) {
				result.addAll(buildInverseSelectionOfOUUuids(selectedUuids, ou.getChildren(), highestUnselectedUuid, level+1));
			}
			return result;
		} else {
			//some but not all in list is checked, keep searching in those, with highestUnselectedUuid = their uuid
			for ( var ou : activeOus) {
				if (!selectedUuids.contains(ou.getUuid())) {
					result.addAll(buildInverseSelectionOfOUUuids(selectedUuids, ou.getChildren(), ou.getUuid(), level+1));

				}
			}
			return result;
		}
	}

	private Set<String> selectRecursively(Collection<OrgUnit> baseCollection, Collection<String> exceptedUUIDs, int level) {
		if (level > 15) {
			log.warn("Recursive loop detected reached level ${}, current ou ids: ${}", level, String.join(", ", baseCollection.stream().map(OrgUnit::getUuid).toList()));
			return new HashSet<>();
		}
		Set<String> selectedUUIDs = new HashSet<>();
		for(var ou : baseCollection){
			Set<String> ouSelected = new HashSet<>();
			if(!exceptedUUIDs.contains(ou.getUuid())){
				//Select children
				ouSelected.addAll(selectRecursively(ou.getChildren(), exceptedUUIDs, level+1));

				//if no children was selected, select self
				if (ouSelected.isEmpty()) {
					ouSelected.add(ou.getUuid());
				}
				selectedUUIDs.addAll(ouSelected);
			}
		}
		return selectedUUIDs;
	}
}
