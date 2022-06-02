package dk.digitalidentity.rc.controller.mvc;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import dk.digitalidentity.rc.controller.mvc.xlsview.ManagersXlsxView;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireManagerRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;

@Controller
public class ManagerController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private OrgUnitService orgUnitService;
	
	@Autowired
	private MessageSource messageSource;
	
	@RequireManagerRole
	@GetMapping("/ui/manager/substitute")
	public String getSubstitute(Model model) {
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
		model.addAttribute("managers", findManagers());

		return "manager/list";
	}

	@RequireReadAccessRole
	@RequestMapping(value = "/ui/managers/download")
	public ModelAndView download(HttpServletResponse response, Locale loc) {
		Map<String, Object> model = new HashMap<>();
		model.put("managers", findManagers());
		model.put("locale", loc);
		model.put("messagesBundle", messageSource);

		response.setContentType("application/ms-excel");
		response.setHeader("Content-Disposition", "attachment; filename=\"ledere.xlsx\"");

		return new ModelAndView(new ManagersXlsxView(), model);
	}
	
	private User getManager() {
		return userService.getByUserId(SecurityUtil.getUserId());
	}
	
	public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
	    Set<Object> seen = ConcurrentHashMap.newKeySet();
	    return t -> seen.add(keyExtractor.apply(t));
	}

	private List<User> findManagers() {
		return orgUnitService.getAllWithManager().stream()
				.map(o -> o.getManager())
				.filter(distinctByKey(User::getUuid))
				.collect(Collectors.toList());
	}
}
