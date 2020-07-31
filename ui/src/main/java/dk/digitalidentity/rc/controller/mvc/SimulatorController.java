package dk.digitalidentity.rc.controller.mvc;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SimulationDTO;
import dk.digitalidentity.rc.controller.validator.SimulationValidator;
import dk.digitalidentity.rc.dao.model.SystemRoleAssignment;
import dk.digitalidentity.rc.dao.model.UserRole;
import dk.digitalidentity.rc.exceptions.UserNotFoundException;
import dk.digitalidentity.rc.security.RequireAdministratorRole;
import dk.digitalidentity.rc.service.ItSystemService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequireAdministratorRole
public class SimulatorController {

	@Autowired
	Environment environment;

	@Autowired
	private ItSystemService itSystemService;

	@Autowired
	private SimulationValidator simulationValidator;

	@Autowired
	private UserService userService;

	@InitBinder
	void initBinder(WebDataBinder binder) {
		binder.addValidators(simulationValidator);
	}

	@GetMapping("/ui/simulate/login")
	public String loginSimulator(Model model) {
		SimulationDTO simulation = new SimulationDTO();

		return returnLoginSimulationPage(model, simulation);
	}

	@PostMapping(value = "/ui/simulate/login")
	public String loginPost(Model model, @Valid @ModelAttribute("simulation") SimulationDTO simulation, BindingResult bindingResult) {
		if (bindingResult.hasErrors()) {
			return returnLoginSimulationPage(model, simulation);
		}

		String result = "";
		String userId = simulation.getUserId();
		String itSystemIdentifier = simulation.getItSystem().getIdentifier();
		List<UserRole> roles = new ArrayList<UserRole>();
		try {
			roles = userService.getAllUserRoles(userId, itSystemIdentifier);
		}
		catch (UserNotFoundException ex) {
			// should already be dealt with by validator
			log.warn("Tried to fetch all user roles for user that does not exist");

			return returnLoginSimulationPage(model, simulation);
		}

		switch (simulation.getLoginType()) {
			case OIO_BPP:
				try {
					result = userService.generateOIOBPP(userId, itSystemIdentifier, new HashMap<String, String>());
					result = new String(Base64.getDecoder().decode(result), Charset.forName("UTF-8"));
				}
				catch (UserNotFoundException ex) {
					log.warn("Tried to generate OIOBPP for user that does not exist.", ex);

					return returnLoginSimulationPage(model, simulation);
				}

				break;
			case SYSTEM_ROLE:
				Set<String> systemRoleSet = new HashSet<>();
				for (UserRole role : roles) {
					for (SystemRoleAssignment systemRole : role.getSystemRoleAssignments()) {
						systemRoleSet.add(systemRole.getSystemRole().getIdentifier() + " - " + systemRole.getSystemRole().getName());
					}
				}
				result = Arrays.toString(systemRoleSet.toArray());
			case USER_ROLE:
				List<String> userRoleList = new ArrayList<>();
				for (UserRole role : roles) {
					userRoleList.add(role.getIdentifier() + " - " + role.getName());
				}

				result = Arrays.toString(userRoleList.toArray());
				break;
		}

		model.addAttribute("result", result);

		return "simulator/result";
	}

	private String returnLoginSimulationPage(Model model, SimulationDTO simulation) {
		model.addAttribute("simulation", simulation);
		model.addAttribute("itSystems", itSystemService.getAll());

		return "simulator/login";
	}
}
