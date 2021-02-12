package dk.digitalidentity.rc.controller.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.service.TitleService;

@RequireReadAccessOrManagerRole
@Controller
public class TitleController {

	@Autowired
	private TitleService titleService;
	

	@GetMapping("/ui/titles/list")
	public String list(Model model) {
		model.addAttribute("titles", titleService.getAll());

		return "titles/list";
	}
}
