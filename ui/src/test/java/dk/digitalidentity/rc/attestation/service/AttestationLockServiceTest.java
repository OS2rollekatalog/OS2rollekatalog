package dk.digitalidentity.rc.attestation.service;

import dk.digitalidentity.rc.attestation.dao.AttestationLockDao;
import dk.digitalidentity.rc.attestation.exception.ReportModuleBusyException;
import dk.digitalidentity.rc.attestation.model.entity.AttestationLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Attestation Lock Service Tests")
class AttestationLockServiceTest {

	private static final String TEST_LOCK_NAME = "test-lock";
	private static final int LOCK_TIMEOUT_SECONDS = 60 * 3;

	@Mock
	private AttestationLockDao lockDao;

	@InjectMocks
	private AttestationLockService attestationLockService;

	@Nested
	@DisplayName("acquireLock() Tests")
	class AcquireLockTests {

		@Test
		@DisplayName("Should acquire lock when no existing lock found")
		void acquireLock_WhenNoExistingLock_ShouldCreateAndSaveLock() {
			// Arrange
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.empty());

			// Act
			attestationLockService.acquireLock(TEST_LOCK_NAME);

			// Assert
			ArgumentCaptor<AttestationLock> lockCaptor = ArgumentCaptor.forClass(AttestationLock.class);
			verify(lockDao).saveAndFlush(lockCaptor.capture());
			AttestationLock savedLock = lockCaptor.getValue();
			assertEquals(TEST_LOCK_NAME, savedLock.getLockId());
			assertNotNull(savedLock.getAcquiredAt());
		}

		@Test
		@DisplayName("Should acquire lock when existing lock has expired")
		void acquireLock_WhenLockExpired_ShouldAcquireLock() {
			// Arrange
			AttestationLock expiredLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now().minusSeconds(LOCK_TIMEOUT_SECONDS + 10))
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(expiredLock));

			// Act
			attestationLockService.acquireLock(TEST_LOCK_NAME);

			// Assert
			ArgumentCaptor<AttestationLock> lockCaptor = ArgumentCaptor.forClass(AttestationLock.class);
			verify(lockDao).saveAndFlush(lockCaptor.capture());
			AttestationLock savedLock = lockCaptor.getValue();
			assertNotNull(savedLock.getAcquiredAt());
			assertTrue(savedLock.getAcquiredAt().isAfter(LocalDateTime.now().minusSeconds(5)));
		}

		@Test
		@DisplayName("Should acquire lock when existing lock has null acquiredAt")
		void acquireLock_WhenLockHasNullAcquiredAt_ShouldAcquireLock() {
			// Arrange
			AttestationLock lockWithNullAcquiredAt = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(null)
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(lockWithNullAcquiredAt));

			// Act
			attestationLockService.acquireLock(TEST_LOCK_NAME);

			// Assert
			ArgumentCaptor<AttestationLock> lockCaptor = ArgumentCaptor.forClass(AttestationLock.class);
			verify(lockDao).saveAndFlush(lockCaptor.capture());
			assertNotNull(lockCaptor.getValue().getAcquiredAt());
		}

		@Test
		@DisplayName("Should throw ReportModuleBusyException when lock is still active")
		void acquireLock_WhenLockStillActive_ShouldThrowException() {
			// Arrange
			AttestationLock activeLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now().minusSeconds(30)) // 30 seconds ago, still within timeout
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(activeLock));

			// Act & Assert
			assertThrows(ReportModuleBusyException.class, () -> attestationLockService.acquireLock(TEST_LOCK_NAME));
		}

		@Test
		@DisplayName("Should throw ReportModuleBusyException when lock was just acquired")
		void acquireLock_WhenLockJustAcquired_ShouldThrowException() {
			// Arrange
			AttestationLock justAcquiredLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now())
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(justAcquiredLock));

			// Act & Assert
			assertThrows(ReportModuleBusyException.class, () -> attestationLockService.acquireLock(TEST_LOCK_NAME));
		}
	}

	@Nested
	@DisplayName("isLocked() Tests")
	class IsLockedTests {

		@Test
		@DisplayName("Should return false when no lock exists")
		void isLocked_WhenNoLockExists_ShouldReturnFalse() {
			// Arrange
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.empty());

			// Act
			boolean result = attestationLockService.isLocked(TEST_LOCK_NAME);

			// Assert
			assertFalse(result);
		}

		@Test
		@DisplayName("Should return false when lock has null acquiredAt")
		void isLocked_WhenLockHasNullAcquiredAt_ShouldReturnFalse() {
			// Arrange
			AttestationLock lockWithNullAcquiredAt = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(null)
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(lockWithNullAcquiredAt));

			// Act
			boolean result = attestationLockService.isLocked(TEST_LOCK_NAME);

			// Assert
			assertFalse(result);
		}

		@Test
		@DisplayName("Should return false when lock has expired")
		void isLocked_WhenLockExpired_ShouldReturnFalse() {
			// Arrange
			AttestationLock expiredLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now().minusSeconds(LOCK_TIMEOUT_SECONDS + 10))
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(expiredLock));

			// Act
			boolean result = attestationLockService.isLocked(TEST_LOCK_NAME);

			// Assert
			assertFalse(result);
		}

		@Test
		@DisplayName("Should return true when lock is still active")
		void isLocked_WhenLockActive_ShouldReturnTrue() {
			// Arrange
			AttestationLock activeLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now().minusSeconds(30))
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(activeLock));

			// Act
			boolean result = attestationLockService.isLocked(TEST_LOCK_NAME);

			// Assert
			assertTrue(result);
		}

		@Test
		@DisplayName("Should return true when lock was just acquired")
		void isLocked_WhenLockJustAcquired_ShouldReturnTrue() {
			// Arrange
			AttestationLock justAcquiredLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now())
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(justAcquiredLock));

			// Act
			boolean result = attestationLockService.isLocked(TEST_LOCK_NAME);

			// Assert
			assertTrue(result);
		}
	}

	@Nested
	@DisplayName("releaseLock() Tests")
	class ReleaseLockTests {

		@Test
		@DisplayName("Should release lock by setting acquiredAt to null")
		void releaseLock_WhenLockExists_ShouldSetAcquiredAtToNull() {
			// Arrange
			AttestationLock existingLock = AttestationLock.builder()
					.lockId(TEST_LOCK_NAME)
					.acquiredAt(LocalDateTime.now())
					.build();
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.of(existingLock));

			// Act
			attestationLockService.releaseLock(TEST_LOCK_NAME);

			// Assert
			ArgumentCaptor<AttestationLock> lockCaptor = ArgumentCaptor.forClass(AttestationLock.class);
			verify(lockDao).saveAndFlush(lockCaptor.capture());
			AttestationLock savedLock = lockCaptor.getValue();
			assertEquals(TEST_LOCK_NAME, savedLock.getLockId());
			assertNull(savedLock.getAcquiredAt());
		}

		@Test
		@DisplayName("Should throw exception when lock does not exist")
		void releaseLock_WhenLockDoesNotExist_ShouldThrowException() {
			// Arrange
			when(lockDao.findById(TEST_LOCK_NAME)).thenReturn(Optional.empty());

			// Act & Assert
			assertThrows(NoSuchElementException.class, () -> attestationLockService.releaseLock(TEST_LOCK_NAME));
		}
	}
}
