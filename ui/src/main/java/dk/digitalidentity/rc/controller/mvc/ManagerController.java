package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.controller.mvc.xlsview.ManagersXlsxView;
import dk.digitalidentity.rc.controller.rest.model.ManagerSubstituteAssignmentDTO;
import dk.digitalidentity.rc.controller.rest.model.OrgUnitDTO;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.RequireAdministratorOrManagerRole;
import dk.digitalidentity.rc.security.RequireReadAccessOrManagerRole;
import dk.digitalidentity.rc.security.RequireReadAccessRole;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Controller
public class ManagerController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private MessageSource messageSource;

	@Autowired
	private OrgUnitService orgUnitService;

	@Autowired
	private ManagerSubstituteService managerSubstituteService;

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
			if (orgUnitService.isActiveAndIncluded(orgUnit)) {
				orgUnitDTOs.add(new OrgUnitDTO(orgUnit.getUuid(), orgUnit.getName()));
			}
		}
		orgUnitDTOs.sort(Comparator.comparing(OrgUnitDTO::getName));
		
		List<ManagerSubstituteAssignmentDTO> substitutesDTO = manager.getManagerSubstitutes().stream().filter(s -> !s.getSubstitute().isDeleted()).map(ManagerSubstituteAssignmentDTO::new).toList();
		
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

	public record ManagerSubstituteDTO(long id, String substituteName, String managerName, String ouName, String assignedDate, String warning) {}
	@RequireReadAccessRole
	@GetMapping("/ui/management/substitute/list")
	public String getSubstitutesList(Model model) {
//		model.addAttribute("substitutes", userService.findManagers());

		String pattern = "dd/MM-yyyy";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		List<ManagerSubstituteDTO> managerSubstitutes = managerSubstituteService.getAll().stream().map(ms -> {
			boolean managerInactive = ms.getManager().isDeleted() || ms.getManager().isDisabled();
			boolean substituteInactive = ms.getSubstitute().isDeleted() || ms.getSubstitute().isDisabled();;

			return new ManagerSubstituteDTO(
					ms.getId(),
					ms.getSubstitute().getName(),
					ms.getManager().getName(),
					ms.getOrgUnit().getName(),
					simpleDateFormat.format(ms.getAssignedTts()),
					managerInactive ? "Leder er ikke længere aktiv" :
							substituteInactive ? "Stedfortræder er ikke længere aktiv"
									: null
			);
		}).toList();


		model.addAttribute("manSubstitutes", managerSubstitutes);
		return "substitute/list";
	}

	public record SimpleManagerDTO(String uuid, String name, boolean selected) {}
	@RequireAdministratorOrManagerRole
	@GetMapping("/ui/management/substitute/create")
	public String getCreateFragment(Model model) {
		List<SimpleManagerDTO> managers = orgUnitService.getManagers().stream().map(user -> new SimpleManagerDTO(user.getUuid(), user.getName(), false)).toList();
		model.addAttribute("managers", managers);

		//Orglist is empty, but later dynamically fetched
		List<OUDTO> orgUnits = new ArrayList<>();
		model.addAttribute("orgUnits", orgUnits);
		return "substitute/fragments/create :: createModal";
	}


	public record OUDTO (String uuid, String name, boolean selected) {}
	@RequireAdministratorOrManagerRole
	@GetMapping("ui/management/substitute/manager/{uuid}/orgunit/options")
	public String getOUOptionsForManager(Model model, @PathVariable String uuid) {
		final User manager = userService.getOptionalByUuid(uuid)
				.orElseThrow(() -> new IllegalArgumentException("No manager found with uuid " + uuid));

		List<OUDTO> orgUnits = orgUnitService.getByManagerMatchingUser(manager).stream().map(ou -> new OUDTO(ou.getUuid(), ou.getName(), false)).toList();
		model.addAttribute("orgUnits", orgUnits);
		return "substitute/fragments/create :: orgUnitOptions";
	}


	public record SimpleSubstitute(String uuid, String name) {}
	@RequireAdministratorOrManagerRole
	@GetMapping("/ui/management/substitute/{id}/edit")
	public String getEditFragment(Model model, @PathVariable Long id) {
		ManagerSubstitute manSub = managerSubstituteService.findById(id);

		List<SimpleManagerDTO> managers = orgUnitService.getManagers().stream().map(user -> new SimpleManagerDTO(user.getUuid(), user.getName(), user.getUuid().equals(manSub.getManager().getUuid()))).toList();
		model.addAttribute("managers", managers);

		model.addAttribute("substitute", new SimpleSubstitute(manSub.getSubstitute().getUuid(), manSub.getSubstitute().getName()));

		//Orglist is empty, but later dynamically fetched
		List<OUDTO> orgUnits = orgUnitService.getByManagerMatchingUser(manSub.getManager()).stream().map(ou -> new OUDTO(ou.getUuid(), ou.getName(), ou.getUuid().equals(manSub.getOrgUnit().getUuid()))).toList();
		model.addAttribute("orgUnits", orgUnits);

		model.addAttribute("id", manSub.getId());
		return "substitute/fragments/edit :: editModal";
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
