package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationLockDao;
import dk.digitalidentity.rc.attestation.exception.ReportModuleBusyException;
import dk.digitalidentity.rc.attestation.model.entity.AttestationLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AttestationLockService {
    private final static int LOCK_TIMEOUT_S = 60*3;

    @Autowired
    private AttestationLockDao lockDao;

    // We need a new transaction, so we are sure the lock is written at the end of this methods
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquireLock(String lockName) {
        AttestationLock lock = lockDao.findById(lockName)
                .orElseGet(() -> AttestationLock.builder().lockId(lockName).build());
        if (lock.getAcquiredAt() != null &&
                lock.getAcquiredAt().isAfter(LocalDateTime.now().minusSeconds(LOCK_TIMEOUT_S))) {
            throw new ReportModuleBusyException();
        }
        lock.setAcquiredAt(LocalDateTime.now());
        lockDao.saveAndFlush(lock);
    }

    // We need a new transaction, so we are sure the lock is written at the end of this methods
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLock(String lockName) {
        final AttestationLock lock = lockDao.findById(lockName).orElseThrow();
        lock.setAcquiredAt(null);
        lockDao.saveAndFlush(lock);
    }

}
