package dk.digitalidentity.rc.controller.mvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.xlsview.ManagersXlsxView;
import dk.digitalidentity.rc.controller.rest.model.ManagerSubstituteAssignmentDTO;
import dk.digitalidentity.rc.controller.rest.model.OrgUnitDTO;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;

@Controller
public class ManagerController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private OrgUnitService orgUnitService;

	@RequireReadAccessOrManagerRole
	@GetMapping("/ui/manager/substitute")
	public String getSubstitute(Model model, @RequestParam(required = false, name = "uuid") String uuid) {
		// manager-only users can only access themselves
		if (!SecurityUtil.hasRole(Constants.ROLE_READ_ACCESS)) {
			if (StringUtils.hasLength(uuid)) {
				return "redirect:/";
			}
		}

		// make sure we have a manager to look at
		User manager = (uuid != null) ? userService.getByUuid(uuid) : getManager();
		if (manager == null) {
			return "redirect:/";
		}

		List<OrgUnitDTO> orgUnitDTOs = new ArrayList<>();
		List<OrgUnit> managerOrgUnits = orgUnitService.getByManagerMatchingUser(manager);
		for (OrgUnit orgUnit : managerOrgUnits) {
			orgUnitDTOs.add(new OrgUnitDTO(orgUnit.getUuid(), orgUnit.getName()));
		}
		
		List<ManagerSubstituteAssignmentDTO> substitutesDTO = manager.getManagerSubstitutes().stream().map(ManagerSubstituteAssignmentDTO::new).toList();
		
		model.addAttribute("managerUuid", manager.getUuid());
		model.addAttribute("managerName", manager.getName());
		model.addAttribute("substitutes", substitutesDTO);
		model.addAttribute("orgUnits", orgUnitDTOs);
		model.addAttribute("page", (uuid != null) ? "manager.list" : "manager.substitute");
		
		boolean canEdit = SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR) || (getManager() != null &&  Objects.equals(getManager().getUuid(), manager.getUuid()));
		model.addAttribute("canEdit", canEdit);
		
		return "manager/substitute";
	}
	
	@RequireReadAccessRole
	@GetMapping("/ui/manager/list")
	public String getManagers(Model model) {
		model.addAttribute("managers", userService.findManagers());

		return "manager/list";
	}

	@RequireReadAccessRole
	@RequestMapping(value = "/ui/managers/download")
	public ModelAndView download(HttpServletResponse response, Locale loc) {
		Map<String, Object> model = new HashMap<>();
		model.put("managers", userService.findManagers());
		model.put("locale", loc);
		model.put("messagesBundle", messageSource);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"ledere.xlsx\"");

		return new ModelAndView(new ManagersXlsxView(), model);
	}
	
	private User getManager() {
		return userService.getByUserId(SecurityUtil.getUserId());
	}

}
