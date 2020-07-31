package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryTitle;

public interface HistoryTitleDao extends JpaRepository<HistoryTitle, Long> {

    @Query("SELECT ht FROM HistoryTitle ht WHERE YEAR(ht.dato)=?1 AND MONTH(ht.dato)=?2 AND DAY(ht.dato)=?3")
    List<HistoryTitle> findByDate(Integer year, Integer month, Integer day);

}
