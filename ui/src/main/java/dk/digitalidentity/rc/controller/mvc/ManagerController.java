package dk.digitalidentity.rc.controller.mvc;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.controller.mvc.xlsview.ManagersXlsxView;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;

@Controller
public class ManagerController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private RoleCatalogueConfiguration roleCatalogueConfiguration;
	
	@RequireManagerRole
	@GetMapping("/ui/manager/substitute")
	public String getSubstitute(Model model) {
		if (roleCatalogueConfiguration.getSubstituteManagerAPI().isEnabled()) {
			return "redirect:/";
		}

		User manager = getManager();
		if (manager == null) {
			return "redirect:/";
		}

		model.addAttribute("substitute", manager.getManagerSubstitute());
		
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
