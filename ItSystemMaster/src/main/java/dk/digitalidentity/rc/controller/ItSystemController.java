package dk.digitalidentity.rc.controller;

import java.util.Date;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import dk.digitalidentity.rc.controller.viewmodel.ItSystemForm;
import dk.digitalidentity.rc.controller.viewmodel.SystemRoleForm;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.SystemRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.SystemRoleService;

@Controller
public class ItSystemController {

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SystemRoleService systemRoleService;

	@GetMapping(value = { "/", "/ui/itsystem/list" })
	public String list(Model model) {
		model.addAttribute("itsystems", itSystemService.getAll());

		return "itsystem/list";
	}

	@GetMapping(value = { "/ui/itsystem/new" })
	public String createItSystem(Model model) {
		ItSystemForm form = new ItSystemForm();
		model.addAttribute("itSystemForm", form);

		return "itsystem/new";
	}

	@PostMapping(value = { "/ui/itsystem/new" })
	public String createItSystem(Model model, @Valid @ModelAttribute("itSystemForm") ItSystemForm itSystemForm, BindingResult bindingResult) throws Exception {
		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("itSystemForm", itSystemForm);

			return "itsystem/new";
		}

		ItSystem itSystem = new ItSystem();
		itSystem.setName(itSystemForm.getName());
		itSystem.setMasterId(UUID.randomUUID().toString());
		itSystem.setLastModified(new Date());

		itSystem = itSystemService.save(itSystem);

		return "redirect:edit/" + itSystem.getMasterId();
	}

	@GetMapping(value = { "/ui/itsystem/edit/{masterID}" })
	public String editItSystem(Model model, @PathVariable("masterID") String id) {
		ItSystem itSystem = itSystemService.getByMasterID(id);
		if (itSystem == null) {
			return "redirect:../list";
		}

		SystemRoleForm systemRoleForm = new SystemRoleForm();
		systemRoleForm.setItSystemId(itSystem.getId());

		model.addAttribute("itsystem", itSystem);
		model.addAttribute("systemRoles", systemRoleService.getByItSystem(itSystem));
		model.addAttribute("systemRoleForm", systemRoleForm);

		return "itsystem/edit";
	}

	@PostMapping(value = { "/ui/itsystem/edit/{masterID}/addSystemRole" })
	public String addSystemRoleToItSystem(Model model, @PathVariable("masterID") String masterID, @Valid @ModelAttribute("systemRoleForm") SystemRoleForm systemRoleForm, BindingResult bindingResult) {
		ItSystem itSystem = itSystemService.getByMasterID(masterID);
		if (itSystem == null) {
			return "redirect:../../list";
		}

		if (bindingResult.hasErrors()) {
			model.addAttribute(bindingResult.getAllErrors());
			model.addAttribute("itsystem", itSystem);
			model.addAttribute("systemRoles", systemRoleService.getByItSystem(itSystem));
			model.addAttribute("systemRoleForm", systemRoleForm);

			return "itsystem/edit";
		}

		SystemRole systemRole = new SystemRole();
		if (systemRoleForm.getId() > 0) {
			systemRole = systemRoleService.getById(systemRoleForm.getId());
			if (systemRole == null) {
				return "redirect:../../list";
			}

			systemRole.setName(systemRoleForm.getName());
			systemRole.setDescription(systemRoleForm.getDescription());
		}
		else {
			systemRole.setName(systemRoleForm.getName());
			systemRole.setIdentifier(systemRoleForm.getIdentifier());
			systemRole.setDescription(systemRoleForm.getDescription());
			systemRole.setItSystem(itSystem);
		}

		itSystem.setLastModified(new Date());
		itSystemService.save(itSystem);
		systemRoleService.save(systemRole);

		return "redirect:";
	}
}
