package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;

public interface HistoryItSystemDao extends JpaRepository<HistoryItSystem, Long> {

//  cannot get this to work, keeps returning 0 hits
//	List<HistoryItSystem> findByDato(LocalDate date);
	
    @Query("SELECT hit FROM HistoryItSystem hit WHERE YEAR(hit.dato)=?1 AND MONTH(hit.dato)=?2 AND DAY(hit.dato)=?3")
    List<HistoryItSystem> findByDate(Integer year, Integer month, Integer day);

}
