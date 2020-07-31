package dk.digitalidentity.rc.controller.rest;

import java.security.Principal;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dk.digitalidentity.rc.controller.rest.model.UserHistoryDTOWrapper;
import dk.digitalidentity.rc.dao.model.RequestApprove;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.RequestApproveService;
import dk.digitalidentity.rc.service.UserHistoryService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.log4j.Log4j;

@Log4j
@RestController
public class MyRestController {

	@Autowired
	private UserService userService;

	@Autowired
	private UserHistoryService userHistoryService;

	@Autowired
	private MessageSource messageSource;
	
	@Autowired
	private RequestApproveService requestApproveService;

	@RequestMapping(value = "/rest/users/history")
	public ResponseEntity<UserHistoryDTOWrapper> getHistory(Principal principal, Locale locale) {
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("Unable to history for user: " + principal.getName());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		UserHistoryDTOWrapper wrapper = new UserHistoryDTOWrapper(locale, messageSource);
		wrapper.setData(userHistoryService.getUserHistory(user));

		return new ResponseEntity<>(wrapper, HttpStatus.OK);
	}
	
	@PostMapping(value = { "/rest/my/requests/delete/{id}" })
	public ResponseEntity<String> deleteRequest(@PathVariable("id") long id, Principal principal) {
		User user = userService.getByUserId(principal.getName());
		if (user == null) {
			log.warn("Unable delete request for user: " + principal.getName());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		
		RequestApprove request = requestApproveService.getById(id);
		if (request == null || !request.getRequester().getUuid().equals(user.getUuid())) {
			return new ResponseEntity<>("Man kan ikke slette andres anmodninger!", HttpStatus.FORBIDDEN);
		}
		
		requestApproveService.delete(request);

		return new ResponseEntity<>(HttpStatus.OK);
	}
}
