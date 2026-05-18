package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.TestContainersConfiguration;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.service.DomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Requires Java 25+ — circular dependency resolution in Spring Boot breaks context loading on Java 21. Enable when the project is on Java 25.")
@SpringBootTest
@ActiveProfiles({"test"})
@TestPropertySource(locations = "classpath:test.properties")
@Import(TestContainersConfiguration.class)
@Transactional
@DisplayName("ManagerDelegateDao — active delegation queries")
class ManagerDelegateDaoTest {

    @Autowired private ManagerDelegateDao dao;
    @Autowired private UserDao userDao;
    @Autowired private DomainService domainService;

    private User manager;
    private User delegate;
    private User otherDelegate;
    private User otherManager;

    @BeforeEach
    void setup() {
        var domain = domainService.getPrimaryDomain();

        manager = new User();
        manager.setUuid(UUID.randomUUID().toString());
        manager.setUserId("test-manager-" + UUID.randomUUID());
        manager.setName("Manager");
        manager.setDeleted(false);
        manager.setDomain(domain);
        userDao.save(manager);

        delegate = new User();
        delegate.setUuid(UUID.randomUUID().toString());
        delegate.setUserId("test-delegate-" + UUID.randomUUID());
        delegate.setName("Delegate");
        delegate.setDeleted(false);
        delegate.setDomain(domain);
        userDao.save(delegate);

        otherDelegate = new User();
        otherDelegate.setUuid(UUID.randomUUID().toString());
        otherDelegate.setUserId("test-other-delegate-" + UUID.randomUUID());
        otherDelegate.setName("Other Delegate");
        otherDelegate.setDeleted(false);
        otherDelegate.setDomain(domain);
        userDao.save(otherDelegate);

        otherManager = new User();
        otherManager.setUuid(UUID.randomUUID().toString());
        otherManager.setUserId("test-other-manager-" + UUID.randomUUID());
        otherManager.setName("Other Manager");
        otherManager.setDeleted(false);
        otherManager.setDomain(domain);
        userDao.save(otherManager);
    }

    // ---- Helpers ---- //

    private void persist(User mgr, User del, LocalDate from, LocalDate to, boolean indefinitely) {
        dao.save(ManagerDelegate.builder()
                .manager(mgr)
                .delegate(del)
                .fromDate(from)
                .toDate(to)
                .indefinitely(indefinitely)
                .build());
    }

    @Nested
    @DisplayName("findActiveByDelegate")
    class FindActiveByDelegate {

        @Test
        @DisplayName("returns active record for the matching delegate")
        void returnsRecordForMatchingDelegate() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now(), null, true);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegate(delegate, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDelegate().getUuid()).isEqualTo(delegate.getUuid());
        }

        @Test
        @DisplayName("excludes records for other delegates, even when indefinitely=true (regression: OR-precedence bug)")
        void excludesRecordForOtherDelegate_evenIfIndefinitelyTrue() {
            // ---- Given ---- //
            // otherDelegate has an indefinitely=true record — the old broken query returned ALL indefinitely=true records
            persist(manager, otherDelegate, LocalDate.now(), null, true);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegate(delegate, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("excludes records whose toDate is in the past")
        void excludesExpiredRecord() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), false);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegate(delegate, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("excludes records whose fromDate is in the future")
        void excludesFutureRecord() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now().plusDays(1), LocalDate.now().plusDays(7), false);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegate(delegate, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("includes record active exactly on today's date")
        void includesRecordActiveTodayByDate() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now(), LocalDate.now(), false);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegate(delegate, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("findActiveByManager")
    class FindActiveByManager {

        @Test
        @DisplayName("returns active record for the matching manager")
        void returnsRecordForMatchingManager() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now(), null, true);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByManager(manager, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getManager().getUuid()).isEqualTo(manager.getUuid());
        }

        @Test
        @DisplayName("excludes records for other managers, even when indefinitely=true (regression: OR-precedence bug)")
        void excludesRecordForOtherManager_evenIfIndefinitelyTrue() {
            // ---- Given ---- //
            persist(otherManager, delegate, LocalDate.now(), null, true);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByManager(manager, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("excludes records whose toDate is in the past")
        void excludesExpiredRecord() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), false);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByManager(manager, LocalDate.now());

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActiveByDelegateUuid")
    class FindActiveByDelegateUuid {

        @Test
        @DisplayName("returns active record for the matching UUID")
        void returnsRecordForMatchingUuid() {
            // ---- Given ---- //
            persist(manager, delegate, LocalDate.now(), null, true);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegateUuid(delegate.getUuid(), LocalDate.now());

            // ---- Then ---- //
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDelegate().getUuid()).isEqualTo(delegate.getUuid());
        }

        @Test
        @DisplayName("excludes records for other delegate UUIDs, even when indefinitely=true (regression: OR-precedence bug)")
        void excludesRecordForOtherUuid_evenIfIndefinitelyTrue() {
            // ---- Given ---- //
            persist(manager, otherDelegate, LocalDate.now(), null, true);

            // ---- When ---- //
            List<ManagerDelegate> result = dao.findActiveByDelegateUuid(delegate.getUuid(), LocalDate.now());

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }
    }
}
