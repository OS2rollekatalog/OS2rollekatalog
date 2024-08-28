package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.model.ItSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static dk.digitalidentity.rc.attestation.AttestationConstants.CACHE_PREFIX;

@Service
public class AttestationCachedItSystemService {
    @Autowired
    private ItSystemDao itSystemDao;

    /**
     * Note cache are cleared on a schedule by {@link dk.digitalidentity.rc.attestation.task.AttestationCacheTTLTask}
     */
    @Cacheable(value = CACHE_PREFIX + "ItSystems")
    public boolean isItSystemExempt(final Long itSystemId) {
        if (itSystemId == null) {
            return false;
        }
        return itSystemDao.findById(itSystemId)
                .map(ItSystem::isAttestationExempt)
                .orElse(false);
    }


}
