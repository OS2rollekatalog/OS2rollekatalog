package dk.digitalidentity.rc.controller.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import dk.digitalidentity.rc.controller.mvc.viewmodel.SimulationDTO;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.UserService;

@Component
public class SimulationValidator implements Validator {

	@Autowired
	private UserService userService;

	@Override
	public boolean supports(Class<?> aClass) {
		return (SimulationDTO.class.isAssignableFrom(aClass));
	}

	@Override
	public void validate(Object o, Errors errors) {
		SimulationDTO simulation = (SimulationDTO) o;

		User alreadyExistingUser = userService.getByUserId(simulation.getUserId());
		if (alreadyExistingUser == null) {
			errors.rejectValue("userId", "html.errors.user.not.found");
		}
		
		if (simulation.getItSystem() == null) {
			errors.rejectValue("itSystem", "html.errors.itsystem.not.found");
		}
	}
}
