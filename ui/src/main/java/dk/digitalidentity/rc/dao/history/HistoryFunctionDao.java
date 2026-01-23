package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface HistoryFunctionDao extends JpaRepository<HistoryFunction, Long> {

    @Query("SELECT ht FROM HistoryFunction ht WHERE ht.dato=?1")
    List<HistoryFunction> findByDate(LocalDate date);

}
