package dk.digitalidentity.rc.controller.rest;

import dk.digitalidentity.rc.controller.rest.model.UserHistoryDTOWrapper;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserHistoryService;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@Slf4j
@RestController
public class MyRestController {

	@Autowired
	private UserService userService;

	@Autowired
	private UserHistoryService userHistoryService;

	@Autowired
	private MessageSource messageSource;


	@RequestMapping(value = "/rest/users/history")
	public ResponseEntity<UserHistoryDTOWrapper> getHistory(Locale locale) {
		User user = userService.getByUserId(SecurityUtil.getUserId());
		if (user == null) {
			log.warn("Unable to history for user: " + SecurityUtil.getUserId());
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}

		UserHistoryDTOWrapper wrapper = new UserHistoryDTOWrapper(locale, messageSource);
		wrapper.setData(userHistoryService.getUserHistory(user));

		return new ResponseEntity<>(wrapper, HttpStatus.OK);
	}
}
