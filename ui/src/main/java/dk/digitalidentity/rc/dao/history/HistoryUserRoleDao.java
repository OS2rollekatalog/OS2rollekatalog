package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryUserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryUserRoleDao extends JpaRepository<HistoryUserRole, Long>  {

    HistoryUserRole findFirstByUserRoleIdAndHistoryItSystemOrderByIdDesc(final long roleId, final HistoryItSystem itSystem);

    HistoryUserRole findByUserRoleIdAndHistoryItSystem_IdOrderByIdDesc(final long roleId, final long itSystemId);

}
