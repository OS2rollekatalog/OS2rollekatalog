package dk.digitalidentity.rc.rolerequest.service;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingRequestsService {
	private final RequestService requestService;

	@Transactional(readOnly = true)
	public long countWaitingRequests(final User user) {
		if (requestService.isAuthorizationResponsibleAnywhere(user) ||
			requestService.isRequestAuthorizedAnywhere() ||
			requestService.isSystemResponsibleAnywhere(user) ||
			requestService.isManagerAnywhere(user) ||
			SecurityUtil.hasRole(Constants.ROLE_ADMINISTRATOR)
		) {
			return requestService.getPendingApprovableRequests().size();
		}
		return 0;
	}

}
