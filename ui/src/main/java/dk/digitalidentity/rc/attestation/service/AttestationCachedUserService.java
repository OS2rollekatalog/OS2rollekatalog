package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.model.Position;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CACHE_PREFIX;


@Component
@EnableCaching
public class AttestationCachedUserService {
    @Autowired
    private UserDao userDao;

    /**
     * Note cache are cleared on a schedule by {@link dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask}
     */
    @Cacheable(value = CACHE_PREFIX + "UsernameFromUuid")
    public String userNameFromUuidCached(final String userUuid) {
        return userDao.findById(userUuid).map(User::getName).orElse("ukendt");
    }


    /**
     * Note cache are cleared on a schedule by {@link dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask}
     */
    @Cacheable(value = CACHE_PREFIX + "UserIdFromUuid")
    public String userIdFromUuidCached(final String userUuid) {
        return userDao.findById(userUuid).map(User::getUserId).orElse("ukendt");
    }


    /**
     * Note cache are cleared on a schedule by {@link dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask}
     */
    @Cacheable(value = CACHE_PREFIX + "UserPositions")
    public String getUserPositionsCached(final String userUuid, final String ouUuid) {
        return userDao.findById(userUuid)
                .map(u -> u.getPositions().stream()
                        .filter(p -> p.getOrgUnit() != null && p.getOrgUnit().getEntityId() != null && p.getOrgUnit().getEntityId().equals(ouUuid))
                        .map(Position::getName)
                        .distinct()
                        .collect(Collectors.joining(", "))
                )
                .orElse("ukendt");
    }

    /**
     * Note cache are cleared on a schedule by {@link dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask}
     */
    @Cacheable(value = CACHE_PREFIX + "AllUserPositions")
    public String getAllUserPositionsCached(final String userUuid) {
        return userDao.findById(userUuid)
                .map(u -> u.getPositions().stream()
                        .map(p -> p.getName() + "(" + p.getOrgUnit().getName() + ")")
                        .distinct()
                        .collect(Collectors.joining(", "))
                )
                .orElse("ukendt");
    }
}
