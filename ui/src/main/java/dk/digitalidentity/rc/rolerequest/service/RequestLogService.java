package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.rolerequest.dao.RequestLogDao;
import dk.digitalidentity.rc.rolerequest.model.entity.RequestLog;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.rc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RequestLogService {

	@Autowired
	private RequestLogDao requestLogDao;

	@Autowired
	private UserService userService;

	public List<RequestLog> getAllNewestFirst() {

		if (SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR)) {
			return requestLogDao.findAllByOrderByRequestTimestampDesc();
		} else {
			User loggedInUser = userService.getByUserId(SecurityUtil.getUserId());
			return requestLogDao.findByActingUser_UuidOrTargetUser_UuidOrderByRequestTimestampDesc(loggedInUser.getUuid(), loggedInUser.getUuid());
		}
	}
}
