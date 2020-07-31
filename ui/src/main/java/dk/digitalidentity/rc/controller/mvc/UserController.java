package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.viewmodel.KleDTO;
import dk.digitalidentity.rc.dao.model.Kle;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserKLEMapping;
import dk.digitalidentity.rc.dao.model.enums.KleType;
import dk.digitalidentity.rc.security.AccessConstraintService;
import dk.digitalidentity.rc.security.RequireAssignerOrKleAdminRole;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.KleService;
import dk.digitalidentity.rc.service.UserService;
import dk.digitalidentity.rc.service.model.UserRoleAssignedToUser;

@RequireReadAccessOrManagerRole
@Controller
public class UserController {

    @Value("#{servletContext.contextPath}")
    private String servletContextPath;

	@Autowired
	private UserService userService;

    @Autowired
    private UserControllerHelper helper;
    
	@Autowired
	private KleService kleService;

    @Autowired
	private AccessConstraintService assignerRoleConstraint;

    @Value("${kle.ui.enabled:false}")
	private boolean kleUiEnabled;

	@GetMapping(value = "/ui/users/list")
	public String view() {
		return "users/list";
	}

	@GetMapping(value = "/ui/users/view/{id}")
	public String view(Model model, @PathVariable("id") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		boolean readOnly = !(SecurityUtil.hasRole(Constants.ROLE_ASSIGNER) || SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR));
		List<UserRoleAssignedToUser> assignments = userService.getAllUserRolesAssignedToUser(user, null);

		model.addAttribute("user", user);
		model.addAttribute("assignments", assignments);
		model.addAttribute("editable", !readOnly && assignerRoleConstraint.isUserAccessable(user, true));
		model.addAttribute("klePerforming", kleService.getKleAssignments(user, KleType.PERFORMING, true));
		model.addAttribute("kleInterest", kleService.getKleAssignments(user, KleType.INTEREST, true));
		model.addAttribute("kleUiEnabled", kleUiEnabled);

		return "users/view";
	}

	@RequireAssignerOrKleAdminRole
	@GetMapping(value = "/ui/users/edit/{id}")
	public String edit(Model model, @PathVariable("id") String uuid) {
		User user = userService.getByUuid(uuid);
		if (user == null) {
			return "redirect:../list";
		}

		List<Kle> kles = kleService.findAll();

		List<KleDTO> kleDTOS = new ArrayList<>();
		for (Kle kle : kles) {
			KleDTO kleDTO = new KleDTO();
			kleDTO.setId(kle.getCode());
			kleDTO.setParent(kle.getParent().equals("0") ? "#" : kle.getParent());
			kleDTO.setText(kle.isActive() ? kle.getCode() + " " + kle.getName() : kle.getCode() + " " + kle.getName() + " [UDGÅET]");
			kleDTOS.add(kleDTO);
		}

		boolean onlyKleAdmin = SecurityUtil.hasRole(Constants.ROLE_KLE_ADMINISTRATOR) && !SecurityUtil.hasRole(Constants.ROLE_ASSIGNER);

		model.addAttribute("allKles", kleDTOS);
		model.addAttribute("klePrimarySelected", user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.PERFORMING)).map(UserKLEMapping::getCode).collect(Collectors.toList()));
		model.addAttribute("kleSecondarySelected", user.getKles().stream().filter(userKLEMapping -> userKLEMapping.getAssignmentType().equals(KleType.INTEREST)).map(UserKLEMapping::getCode).collect(Collectors.toList()));

		model.addAttribute("user", user);
		model.addAttribute("addRoles", helper.getAddRoles(user));
		model.addAttribute("addRoleGroups", helper.getAddRoleGroups(user));
		model.addAttribute("kleUiEnabled", kleUiEnabled);
		model.addAttribute("onlyKleAdmin", onlyKleAdmin);

		return "users/edit";
	}
}
