package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.controller.rest.model.ItemPermissionDTO;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;

import dk.digitalidentity.rc.security.permission.Permission;
import dk.digitalidentity.rc.security.permission.Section;
import dk.digitalidentity.rc.security.permission.RequireControllerPermission;
import dk.digitalidentity.rc.security.permission.RequirePermission;
import dk.digitalidentity.rc.security.permission.UserPermissionContext;
import dk.digitalidentity.rc.service.ManagerDelegateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.stream.Collectors;

@RequireControllerPermission(section = Section.MANAGER, permission = Permission.READ)
@RequestMapping("ui/managerdelegate")
@RequiredArgsConstructor
@Controller
public class ManagerDelegateController {
	private final ManagerDelegateService managerDelegateService;
	private final UserPermissionContext userPermissionContext;

	public record ManagerDelegateListItem(long id, String delegateName, String managerName, LocalDate fromDate, LocalDate toDate, boolean indefinitely, ItemPermissionDTO allowedActions) {}
	@RequirePermission(section = Section.MANAGER, permission = Permission.UPDATE)
	@GetMapping("list")
	public String getManagerDelegates(Model model) {
		model.addAttribute("delegates", managerDelegateService.getAll().stream().map(md ->
				new ManagerDelegateListItem(
						md.getId(),
						md.getDelegate().getName() + " ("+ md.getDelegate().getUserId() + ")",
						md.getManager().getName()+ " ("+ md.getManager().getUserId() + ")",
						md.getFromDate(),
						md.getToDate(),
						md.isIndefinitely(),
						userPermissionContext.getSpecificAllowedActionsForOus(Section.MANAGER, md.getManager().getPositions().stream().map(p ->p.getOrgUnit().getUuid()).collect(Collectors.toSet()))
				)).toList());

		return "managerdelegate/list";
	}

	@RequirePermission(section = Section.MANAGER, permission = Permission.CREATE)
	@GetMapping("create")
	public String createManagerDelegate(Model model) {

		return "managerdelegate/fragments/create";
	}

	public record UserDTO (String uuid, String name, String id) {}
	public record ManagerDelegateEditDTO(long id, UserDTO delegate, UserDTO manager, LocalDate fromDate, LocalDate toDate, boolean indefinitely) {}
	@RequirePermission(section = Section.MANAGER, permission = Permission.UPDATE)
	@GetMapping("edit")
	public String editManagerDelegate(Model model, @RequestParam long id) {

		ManagerDelegate md = managerDelegateService.getById(id);

		model.addAttribute("managerDelegate", new ManagerDelegateEditDTO(
				md.getId(),
				new UserDTO(md.getDelegate().getUuid(), md.getDelegate().getName(), md.getDelegate().getUserId()),
				new UserDTO(md.getManager().getUuid(), md.getManager().getName(), md.getManager().getUserId()),
				md.getFromDate(),
				md.getToDate(),
				md.isIndefinitely()
				)
		);

		return "managerdelegate/fragments/edit";
	}
}
