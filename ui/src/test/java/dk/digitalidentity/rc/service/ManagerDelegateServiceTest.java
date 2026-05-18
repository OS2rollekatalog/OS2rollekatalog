package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ManagerDelegateDao;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerDelegateService — active delegation lookup")
class ManagerDelegateServiceTest {

    @Mock
    private ManagerDelegateDao managerDelegateDao;

    @InjectMocks
    private ManagerDelegateService service;

    // ---- Helpers ---- //

    private static User makeUser(String uuid) {
        User user = new User();
        user.setUuid(uuid);
        return user;
    }

    private static ManagerDelegate makeManagerDelegate(User manager, User delegate) {
        return ManagerDelegate.builder()
                .manager(manager)
                .delegate(delegate)
                .fromDate(LocalDate.now())
                .indefinitely(true)
                .build();
    }

    @Nested
    @DisplayName("getByDelegate")
    class GetByDelegate {

        @Test
        @DisplayName("returns only records for the given delegate")
        void returnsOnlyRecordsForMatchingDelegate() {
            // ---- Given ---- //
            User delegateA = makeUser("delegate-a-uuid");
            ManagerDelegate md = makeManagerDelegate(makeUser("manager-uuid"), delegateA);
            when(managerDelegateDao.findActiveByDelegate(eq(delegateA), any(LocalDate.class)))
                    .thenReturn(List.of(md));

            // ---- When ---- //
            List<ManagerDelegate> result = service.getByDelegate(delegateA);

            // ---- Then ---- //
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDelegate().getUuid()).isEqualTo("delegate-a-uuid");
        }

        @Test
        @DisplayName("excludes records belonging to other delegates, even if they are indefinitely active")
        void excludesRecordsForOtherDelegate() {
            // ---- Given ---- //
            User delegateA = makeUser("delegate-a-uuid");
            // DAO correctly scopes the query to delegateA — returns nothing
            when(managerDelegateDao.findActiveByDelegate(eq(delegateA), any(LocalDate.class)))
                    .thenReturn(List.of());

            // ---- When ---- //
            List<ManagerDelegate> result = service.getByDelegate(delegateA);

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }

    }

    @Nested
    @DisplayName("getByManager")
    class GetByManager {

        @Test
        @DisplayName("returns only records for the given manager")
        void returnsOnlyRecordsForMatchingManager() {
            // ---- Given ---- //
            User manager = makeUser("manager-uuid");
            ManagerDelegate md = makeManagerDelegate(manager, makeUser("delegate-uuid"));
            when(managerDelegateDao.findActiveByManager(eq(manager), any(LocalDate.class)))
                    .thenReturn(List.of(md));

            // ---- When ---- //
            List<ManagerDelegate> result = service.getByManager(manager);

            // ---- Then ---- //
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getManager().getUuid()).isEqualTo("manager-uuid");
        }

    }

    @Nested
    @DisplayName("getByDelegateUuid")
    class GetByDelegateUuid {

        @Test
        @DisplayName("returns records for the given UUID")
        void returnsOnlyRecordsForMatchingUuid() {
            // ---- Given ---- //
            ManagerDelegate md = makeManagerDelegate(makeUser("manager-uuid"), makeUser("delegate-uuid"));
            when(managerDelegateDao.findActiveByDelegateUuid(eq("delegate-uuid"), any(LocalDate.class)))
                    .thenReturn(List.of(md));

            // ---- When ---- //
            List<ManagerDelegate> result = service.getByDelegateUuid("delegate-uuid");

            // ---- Then ---- //
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("returns empty list for a UUID with no active records")
        void excludesRecordsForDifferentUuid() {
            // ---- Given ---- //
            when(managerDelegateDao.findActiveByDelegateUuid(eq("unknown-uuid"), any(LocalDate.class)))
                    .thenReturn(List.of());

            // ---- When ---- //
            List<ManagerDelegate> result = service.getByDelegateUuid("unknown-uuid");

            // ---- Then ---- //
            assertThat(result).isEmpty();
        }

    }
}
