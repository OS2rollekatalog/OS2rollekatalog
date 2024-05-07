package dk.digitalidentity.rc.attestation.service.temporal;

import dk.digitalidentity.rc.attestation.service.AttestationCachedOuService;
import dk.digitalidentity.rc.dao.ItSystemDao;
import dk.digitalidentity.rc.dao.OrgUnitDao;
import dk.digitalidentity.rc.dao.RoleGroupDao;
import dk.digitalidentity.rc.dao.UserDao;
import dk.digitalidentity.rc.dao.UserRoleDao;
import dk.digitalidentity.rc.dao.history.HistoryItSystemDao;
import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.HistoryUserRoleDao;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import dk.digitalidentity.rc.dao.model.ItSystem;
import dk.digitalidentity.rc.dao.model.OrgUnit;
import dk.digitalidentity.rc.dao.model.RoleGroup;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserRole;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

import static dk.digitalidentity.rc.util.NullSafe.nullSafe;


@Component
public final class UpdaterContextService {

    @Autowired
    private UserRoleDao userRoleDao;

    @Autowired
    private HistoryUserRoleDao historyUserRoleDao;

    @Autowired
    private HistoryItSystemDao historyItSystemDao;

    @Autowired
    private HistoryOUDao historyOUDao;

    @Autowired
    private HistoryUserDao historyUserDao;

    @Autowired
    private ItSystemDao itSystemDao;

    @Autowired
    private OrgUnitDao orgUnitDao;

    @Autowired
    private RoleGroupDao roleGroupDao;

    @Autowired
    private UserDao userDao;

    @Autowired
    private AttestationCachedOuService cachedOuService;

    @Setter
    public final class UpdaterContext {
        private LocalDate when;
        private UserRole currentRole;
        private ItSystem currentItSystem;
        private OrgUnit currentOu;
        private RoleGroup currentRoleGroup;
        private User currentUser;
        private HistoryUserRole historyRole;
        private HistoryItSystem historyItSystem;
        private HistoryOU historyOU;
        private HistoryUser historyUser;

        public String roleName() {
            return currentRole != null ? currentRole.getName() : historyRole.getUserRoleName();
        }
        public String roleDescription() {
            return currentRole != null ? currentRole.getDescription() : historyRole.getUserRoleDescription();
        }

        public boolean isRoleSensitive() {
            return currentRole != null
                    ? currentRole.isSensitiveRole()
                    : (historyRole.getSensitiveRole() != null && historyRole.getSensitiveRole());
        }

        public String ouName() {
            return currentOu != null
                    ? currentOu.getName()
                    : (historyOU != null ? historyOU.getOuName() : null);
        }

        public String ouUuid() {
            return currentOu != null
                    ? currentOu.getUuid()
                    : historyOU != null ? historyOU.getOuUuid() : null;
        }

        public String parentOuUuid() {
            if (currentOu != null && currentOu.getParent() != null) {
                return currentOu.getParent().getUuid();
            }
            if (historyOU != null) {
                return historyOU.getOuParentUuid();
            }
            return null;
        }

        public String parentOuName() {
            if (currentOu != null && currentOu.getParent() != null) {
                return currentOu.getParent().getName();
            }
            if (historyOU != null && historyOU.getOuParentUuid() != null) {
                final HistoryOU historyParentOU = historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(when, historyOU.getOuParentUuid());
                if (historyParentOU != null) {
                    return historyParentOU.getOuName();
                }
            }
            return null;
        }

        public boolean isManager() {
            final String managerUuid = cachedOuService.getOuManager(when, ouUuid());
            final String userId = userUuid();
            return Objects.equals(userId, managerUuid);
        }

        public boolean isManager(final User user) {
            final String managerUuid = cachedOuService.getOuManager(when, ouUuid());
            return Objects.equals(user.getUuid(), managerUuid);
        }

        public String roleGroupDescription() {
            return currentRoleGroup != null ? currentRoleGroup.getDescription() : null;
        }

        public String itSystemName() {
            return currentItSystem != null
                    ? currentItSystem.getName()
                    : historyItSystem.getItSystemName();
        }

        public boolean isItSystemExempt() {
            return currentItSystem != null
                    ? currentItSystem.isAttestationExempt()
                    : historyItSystem.isAttestationExempt();
        }

        public User attestationResponsible() {
            return currentItSystem.getAttestationResponsible();
        }

        public String responsibleUserUuid() {
            return currentItSystem != null
                    ? nullSafe(() -> currentItSystem.getAttestationResponsible().getUuid())
                    : historyItSystem.getAttestationResponsible();
        }

        public String userId() {
            return currentUser != null
                    ? currentUser.getUserId()
                    : (historyUser != null ? historyUser.getUserUserId() : null);
        }

        public String userUuid() {
            return currentUser != null
                    ? currentUser.getUuid()
                    : (historyUser != null ? historyUser.getUserUuid() : null);
        }

        public String userName() {
            return currentUser != null
                    ? currentUser.getName()
                    : historyUser.getUserName();
        }

        public boolean isRoleAssignmentAttestationByAttestationResponsible() {
            return currentRole != null
                    ? currentRole.isRoleAssignmentAttestationByAttestationResponsible()
                    : historyRole.isRoleAssignmentAttestationByAttestationResponsible();
        }
    }

    public class UpdaterContextBuilder {
        private final LocalDate when;
        private final Long itSystemId;
        private final UpdaterContext context = new UpdaterContext();

        private UpdaterContextBuilder(final LocalDate when, final Long itSystemId) {
            this.when = when;
            this.itSystemId = itSystemId;
            final ItSystem currentItSystem = itSystemDao.findById(itSystemId).orElse(null);
            final HistoryItSystem historyItSystem = currentItSystem == null
                    ? historyItSystemDao.findFirstByDatoAndItSystemId(when, itSystemId)
                    : null;
            context.setCurrentItSystem(currentItSystem);
            context.setHistoryItSystem(historyItSystem);
            context.setWhen(when);
        }

        public UpdaterContextBuilder withRole(final Long roleId) {
            final UserRole currentRole = userRoleDao.findById(roleId).orElse(null);
            final HistoryUserRole historyRole = currentRole == null
                    ? historyUserRoleDao.findByUserRoleIdAndHistoryItSystem_IdOrderByIdDesc(roleId, itSystemId)
                    : null;
            context.setCurrentRole(currentRole);
            context.setHistoryRole(historyRole);
            return this;
        }

        public UpdaterContextBuilder withRoleGroup(final Long roleGroupId) {
            final RoleGroup currentRoleGroup = roleGroupId != null
                    ? roleGroupDao.findById(roleGroupId).orElse(null)
                    : null;
            context.setCurrentRoleGroup(currentRoleGroup);
            return this;
        }

        public UpdaterContextBuilder withOrgUnit(final String ouUuid) {
            if (ouUuid != null) {
                final OrgUnit currentOu = orgUnitDao.findById(ouUuid).orElse(null);
                final HistoryOU historyOU = currentOu == null
                        ? historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(when, ouUuid) : null;
                context.setCurrentOu(currentOu);
                context.setHistoryOU(historyOU);
            }
            return this;
        }

        public UpdaterContextBuilder withUser(final String userUuid) {
            final User currentUser = userDao.findById(userUuid).orElse(null);
            final HistoryUser historicUser = currentUser == null ? historyUserDao.findFirstByDatoAndUserUuid(when, userUuid) : null;
            context.setCurrentUser(currentUser);
            context.setHistoryUser(historicUser);
            return this;
        }

        public UpdaterContext getContext() {
            return context;
        }
    }

    public UpdaterContextBuilder contextBuilder(final LocalDate when, final Long itSystemId) {
        return new UpdaterContextBuilder(when, itSystemId);
    }

}

