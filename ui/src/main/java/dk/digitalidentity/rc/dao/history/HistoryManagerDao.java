package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryManager;

public interface HistoryManagerDao extends JpaRepository<HistoryManager, Long> {

    @Query("SELECT hm FROM HistoryManager hm WHERE hm.dato=?1")
    List<HistoryManager> findByDate(LocalDate date);

}
