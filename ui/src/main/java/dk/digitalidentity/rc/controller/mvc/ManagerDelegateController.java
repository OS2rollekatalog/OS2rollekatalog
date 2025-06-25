package dk.digitalidentity.rc.controller.mvc;

import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.security.RequireAdministratorOrManagerRole;

import dk.digitalidentity.rc.service.ManagerDelegateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;


@RequestMapping("ui/managerdelegate")
@Controller
public class ManagerDelegateController {
	@Autowired
	private ManagerDelegateService managerDelegateService;

	public record ManagerDelegateListItem(long id, String delegateName, String managerName, LocalDate fromDate, LocalDate toDate, boolean indefinitely) {}
	@RequireAdministratorOrManagerRole
	@GetMapping("list")
	public String getManagerDelegates(Model model) {
		model.addAttribute("delegates", managerDelegateService.getAll().stream().map(md ->
				new ManagerDelegateListItem(
						md.getId(),
						md.getDelegate().getName() + " ("+ md.getDelegate().getUserId() + ")",
						md.getManager().getName()+ " ("+ md.getManager().getUserId() + ")",
						md.getFromDate(),
						md.getToDate(),
						md.isIndefinitely()
				)));

		return "managerdelegate/list";
	}

	@RequireAdministratorOrManagerRole
	@GetMapping("create")
	public String createManagerDelegate(Model model) {

		return "managerdelegate/fragments/create";
	}

	public record UserDTO (String uuid, String name, String id) {}
	public record ManagerDelegateEditDTO(long id, UserDTO delegate, UserDTO manager, LocalDate fromDate, LocalDate toDate, boolean indefinitely) {}
	@RequireAdministratorOrManagerRole
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
