package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.RoleCatalogueConfiguration;
import dk.digitalidentity.rc.dao.model.FrontPageLink;
import dk.digitalidentity.rc.dao.model.enums.LinkType;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.service.FrontPageLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequireControllerPermission(section = Section.CONFIG, permission = Permission.READ)
@Controller
public class FrontPageConfigurationController {

	@Autowired
	private FrontPageLinkService frontPageLinkService;
	@Autowired
	private RoleCatalogueConfiguration roleCatalogueConfiguration;

	@GetMapping("/ui/frontpage/links")
	public String frontpageLinks(Model model) {
		Map<LinkType, List<FrontPageLink>> linksByType = Arrays.stream(LinkType.values())
			.collect(Collectors.toMap(
				type -> type,
				type -> frontPageLinkService.getAllByLinkTypeOrderedBySortOrder(type)
			));

		model.addAttribute("linksByType", linksByType);
		model.addAttribute("linkTypes", LinkType.values());
		model.addAttribute("icons", roleCatalogueConfiguration.getFrontPageLinkConfig().getIcons());
		return "setting/front_page_links_settings";
	}
}
