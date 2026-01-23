package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.mvc.xlsview.ManagersXlsxView;
import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.controller.rest.model.ManagerSubstituteAssignmentDTO;
import dk.digitalidentity.rc.controller.rest.model.OrgUnitDTO;
import dk.digitalidentity.rc.dao.model.ManagerSubstitute;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.security.permission.NotPermittedException;
import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.PermissionConstraint;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.ManagerSubstituteService;
import dk.digitalidentity.rc.service.OrgUnitService;
import dk.digitalidentity.rc.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RequireControllerPermission(section = Section.MANAGER, permission = Permission.READ)
@Controller
public class ManagerController {
	private final UserService userService;
	private final MessageSource messageSource;
	private final OrgUnitService orgUnitService;
	private final ManagerSubstituteService managerSubstituteService;
	private final UserPermissionContext userPermissionContext;


	@GetMapping("/ui/manager/substitute")
	public String getSubstitute(Model model, @RequestParam(required = false, name = "uuid") String uuid) {
		// make sure we have a manager to look at
		User manager = (uuid != null) ? userService.getOptionalByUuid(uuid).orElse(null) : getManager();
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

		boolean canEdit = userPermissionContext.hasPermission(Section.MANAGER, Permission.UPDATE) && (getManager() != null &&  Objects.equals(getManager().getUuid(), manager.getUuid()));
		model.addAttribute("canEdit", canEdit);

		return "manager/substitute";
	}

	public record ManagerListDTO(String name, String userId, String uuid, ItemPermissionDTO allowedActions) {}
	@GetMapping("/ui/manager/list")
	public String getManagers(Model model) {
		PermissionConstraint readconstraint = userPermissionContext.getConstraint(Section.MANAGER, Permission.READ);

		model.addAttribute("managers", userService.findManagers().stream()
				.filter(user ->
						readconstraint == null ||
								user.getPositions().stream()
										.map(p -> p.getOrgUnit().getUuid())
										.anyMatch(readconstraint::allowsOrgunit))
				.map(user -> new ManagerListDTO(
						user.getName(),
						user.getUserId(),
						user.getUuid(),
						userPermissionContext.getSpecificAllowedActionsForOus(Section.MANAGER, user.getPositions().stream().map(p->p.getOrgUnit().getUuid()).collect(Collectors.toSet()))
				) )
		);

		return "manager/list";
	}

	public record ManagerSubstituteDTO(long id, String substituteName, String managerName, String ouName, String assignedDate, String warning) {}
	@GetMapping("/ui/management/substitute/list")
	public String getSubstitutesList(Model model) {
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.MANAGER, Permission.READ);
		String pattern = "dd/MM-yyyy";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
		List<ManagerSubstituteDTO> managerSubstitutes = managerSubstituteService.getAll().stream()
				.filter(ms -> readConstraint.allowsOrgunit(ms.getOrgUnit().getUuid()))
				.map(ms -> {
					boolean managerInactive = ms.getManager().isDeleted() || ms.getManager().isDisabled();
					boolean substituteInactive = ms.getSubstitute().isDeleted() || ms.getSubstitute().isDisabled();

					return new ManagerSubstituteDTO(
							ms.getId(),
							ms.getSubstitute().getName(),
							ms.getManager().getName(),
							ms.getOrgUnit().getName(),
							ms.getAssignedTts() != null ? simpleDateFormat.format(ms.getAssignedTts()) : null,
							managerInactive ? "Leder er ikke længere aktiv" :
									substituteInactive ? "Stedfortræder er ikke længere aktiv"
											: null
					);
				}).toList();


		model.addAttribute("manSubstitutes", managerSubstitutes);
		return "substitute/list";
	}

	public record SimpleManagerDTO(String uuid, String name, boolean selected) {}
	@RequirePermission(section = Section.MANAGER, permission = Permission.CREATE)
	@GetMapping("/ui/management/substitute/create")
	public String getCreateFragment(Model model) {
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.MANAGER, Permission.READ);
		List<SimpleManagerDTO> managers = orgUnitService.getManagers().stream()
				.filter(user ->
						readConstraint == null ||
								user.getPositions().stream()
										.map(p -> p.getOrgUnit().getUuid())
										.anyMatch(readConstraint::allowsOrgunit))
				.map(user -> new SimpleManagerDTO(user.getUuid(), user.getName(), false))
				.sorted(Comparator.comparing(SimpleManagerDTO::name)).toList();
		model.addAttribute("managers", managers);

		//Orglist is empty, but later dynamically fetched
		List<OUDTO> orgUnits = new ArrayList<>();
		model.addAttribute("orgUnits", orgUnits);
		return "substitute/fragments/create :: createModal";
	}


	public record OUDTO (String uuid, String name, boolean selected) {}
	@GetMapping("ui/management/substitute/manager/{uuid}/orgunit/options")
	public String getOUOptionsForManager(Model model, @PathVariable String uuid) {
		final User manager = userService.getOptionalByUuid(uuid)
				.orElseThrow(() -> new IllegalArgumentException("No manager found with uuid " + uuid));

		List<OUDTO> orgUnits = orgUnitService.getByManagerMatchingUser(manager).stream().map(ou -> new OUDTO(ou.getUuid(), ou.getName(), false)).toList();
		model.addAttribute("orgUnits", orgUnits);
		return "substitute/fragments/create :: orgUnitOptions";
	}


	public record SimpleSubstitute(String uuid, String name) {}
	@RequirePermission(section = Section.MANAGER, permission = Permission.UPDATE)
	@GetMapping("/ui/management/substitute/{id}/edit")
	public String getEditFragment(Model model, @PathVariable Long id) {
		PermissionConstraint readConstraint = userPermissionContext.getConstraint(Section.MANAGER, Permission.READ);
		PermissionConstraint editConstraint = userPermissionContext.getConstraint(Section.MANAGER, Permission.UPDATE);

		ManagerSubstitute manSub = managerSubstituteService.findById(id);
		if (!editConstraint.allowsOrgunit(manSub.getOrgUnit().getUuid())) {
			throw new NotPermittedException(
					"Constraints does not include managersubstitute " + id,
					Section.MANAGER,
					Permission.UPDATE);
		}

		List<SimpleManagerDTO> managers = orgUnitService.getManagers().stream()
				.filter(user ->
						readConstraint == null ||
								user.getPositions().stream()
										.map(p -> p.getOrgUnit().getUuid())
										.anyMatch(readConstraint::allowsOrgunit))
				.map(user -> new SimpleManagerDTO(user.getUuid(), user.getName(), user.getUuid().equals(manSub.getManager().getUuid()))).toList();
		model.addAttribute("managers", managers);

		model.addAttribute("substitute", new SimpleSubstitute(manSub.getSubstitute().getUuid(), manSub.getSubstitute().getName()));

		//Orglist is empty, but later dynamically fetched
		List<OUDTO> orgUnits = orgUnitService.getByManagerMatchingUser(manSub.getManager()).stream().map(ou -> new OUDTO(ou.getUuid(), ou.getName(), ou.getUuid().equals(manSub.getOrgUnit().getUuid()))).toList();
		model.addAttribute("orgUnits", orgUnits);

		model.addAttribute("id", manSub.getId());
		return "substitute/fragments/edit :: editModal";
	}

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
		// TODO - refactoring target - this doesn't actually do what the signature say it does...
		return userService.getByUserId(SecurityUtil.getUserId());
	}

}
