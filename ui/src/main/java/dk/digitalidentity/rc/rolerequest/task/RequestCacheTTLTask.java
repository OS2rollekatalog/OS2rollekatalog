package dk.digitalidentity.rc.rolerequest.task;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static dk.digitalidentity.rc.rolerequest.RequestConstants.CACHE_PREFIX;

@Component
@EnableScheduling
public class RequestCacheTTLTask {

	@Autowired
    private CacheManager cacheManager;

    @Scheduled(cron = "${rc.request.request_cache_ttl_cron:0 0 * * * *}")
    public void clearCache() {
        cacheManager.getCacheNames().stream()
                .filter(name -> StringUtils.startsWith(name, CACHE_PREFIX))
                .map(name -> cacheManager.getCache(name))
                .filter(Objects::nonNull)
                .forEach(Cache::clear);
    }

}
