package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryManager;

public interface HistoryManagerDao extends JpaRepository<HistoryManager, Long> {

    @Query("SELECT hm FROM HistoryManager hm WHERE YEAR(hm.dato)=?1 AND MONTH(hm.dato)=?2 AND DAY(hm.dato)=?3")
    List<HistoryManager> findByDate(Integer year, Integer month, Integer day);

}
