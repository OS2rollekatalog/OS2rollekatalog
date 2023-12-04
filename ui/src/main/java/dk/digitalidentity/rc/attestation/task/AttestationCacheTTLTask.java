package dk.digitalidentity.rc.attestation.task;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CACHE_PREFIX;

import java.util.Objects;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class AttestationCacheTTLTask {
    
	@Autowired
    private CacheManager cacheManager;

    @Scheduled(cron = "${rc.attestation.attestation_cache_ttl_cron}")
    public void clearCache() {
        cacheManager.getCacheNames().stream()
                .filter(name -> StringUtils.startsWith(name, CACHE_PREFIX))
                .map(name -> cacheManager.getCache(name))
                .filter(Objects::nonNull)
                .forEach(Cache::clear);
    }
}
