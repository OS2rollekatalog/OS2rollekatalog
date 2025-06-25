package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryAttestationManagerDelegate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HistoryAttestationManagerDelegateDao extends JpaRepository<HistoryAttestationManagerDelegate, Long> {

	List<HistoryAttestationManagerDelegate> findAllByDate(LocalDate date);



}
