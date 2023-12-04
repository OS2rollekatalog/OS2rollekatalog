package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@RequireAdministratorRole
@Controller
public class FrontPageConfigurationController {

	@Autowired
	private FrontPageLinkService frontPageLinkService;
	@Autowired
	private RoleCatalogueConfiguration roleCatalogueConfiguration;

	@GetMapping("/ui/frontpage/links")
	public String frontpageLinks(Model model) {
		model.addAttribute("links", frontPageLinkService.getAll());
		model.addAttribute("icons", roleCatalogueConfiguration.getFrontPageLinkConfig().getIcons());
		return "setting/front_page_links_settings";
	}
}
