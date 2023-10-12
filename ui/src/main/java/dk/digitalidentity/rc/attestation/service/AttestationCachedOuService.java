package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CACHE_PREFIX;


@Slf4j
@Component
@EnableCaching
public class AttestationCachedOuService {
    @Autowired
    private HistoryOUDao historyOUDao;

    /**
     * Note cache are cleared on a schedule by {@link dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask}
     */
    @Cacheable(value = CACHE_PREFIX + "OuManager")
    public String getOuManager(final LocalDate when, final String ouUuid) {
        if (ouUuid == null) {
            return null;
        }
        HistoryOU foundOU = historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(when, ouUuid);
        if (foundOU == null) {
            log.warn("OU not found for " + ouUuid);
            return null;
        }
        return foundOU.getOuManagerUuid();
    }

}
