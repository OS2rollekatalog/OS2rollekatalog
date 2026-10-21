package dk.digitalidentity.rc.security;

import dk.digitalidentity.rc.config.Constants;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.log.WarnIfSlowerThan;
import dk.digitalidentity.rc.service.assignment.CurrentAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Database-backed admin check, used by {@link SecurityUtil#isAdmin(User)}.
 * Cached per-user, since admin status rarely changes, and cleared every 10 minutes
 */
@Component
@RequiredArgsConstructor
@EnableScheduling
public class AdminStatusChecker {

	private static final String CACHE_NAME = "security#isAdmin";

	private final CurrentAssignmentService currentAssignmentService;
	private final CacheManager cacheManager;

	@Cacheable(value = CACHE_NAME, key = "#user.uuid")
	public boolean checkAdminInDatabase(User user) {
		return currentAssignmentService.hasRoleCatalogueSystemRole(user, Constants.ROLE_ADMINISTRATOR_ID);
	}

	@Scheduled(cron = "${rc.security.admin_cache_ttl_cron:0 */10 * * * *}")
	@WarnIfSlowerThan(millis = 5 * 1000)
	public void clearCache() {
		Cache cache = cacheManager.getCache(CACHE_NAME);
		if (cache != null) {
			cache.clear();
		}
	}
}
