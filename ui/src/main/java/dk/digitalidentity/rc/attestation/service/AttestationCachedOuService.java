package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.service.OrgUnitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CACHE_PREFIX;


@Slf4j
@Component
@EnableCaching
public class AttestationCachedOuService {
    @Autowired
    private HistoryOUDao historyOUDao;
    @Autowired
    private OrgUnitDao orgUnitDao;

    @Autowired
    private OrgUnitService orgUnitService;

    /**
     * Note cache are cleared on a schedule by {@link AttestationCacheTTLTask}
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

    @Transactional
    @Cacheable(value = CACHE_PREFIX + "ParentWithDirrentManager")
    public String findParentOuWithDifferentManager(final String currentUserUuid, final String ouUuid) {
        int cnt = 0;
        OrgUnit currentOu = orgUnitDao.findByUuidAndActiveTrue(ouUuid);
        if (currentOu != null && !orgUnitService.isActiveAndIncluded(currentOu)) {
            currentOu = null;
        }

        while (currentOu != null && currentOu.getManager() != null && currentOu.getManager().getUuid().equals(currentUserUuid)) {
            currentOu = currentOu.getParent();
            if (cnt++ > 10) {
                // In case there is a loop in the organisation hierarchy
                return null;
            }
        }
        return currentOu != null ? currentOu.getUuid() : null;
    }

}
