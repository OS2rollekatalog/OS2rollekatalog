package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryTitle;

public interface HistoryTitleDao extends JpaRepository<HistoryTitle, Long> {

    @Query("SELECT ht FROM HistoryTitle ht WHERE ht.dato=?1")
    List<HistoryTitle> findByDate(LocalDate date);

}
