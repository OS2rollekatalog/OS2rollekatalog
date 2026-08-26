package dk.digitalidentity.rc.rolerequest.service;

import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.RequiredArgsConstructor;

/**
 * The Administrator UserRole rarely changes, so it's cached rather than re-queried on every
 * isAdmin() check. Cleared hourly along with the rest of the rolerequest module's caches
 * (RequestCacheTTLTask), bounding staleness if it's ever edited.
 */
@Component
@RequiredArgsConstructor
public class AdministratorRoleCache {

	private final UserRoleDao userRoleDao;

	@Cacheable(value = CACHE_PREFIX + "administratorRole")
	public UserRole get() {
		return userRoleDao.getByIdentifier(Constants.ROLE_ADMINISTRATOR_ID);
	}
}
