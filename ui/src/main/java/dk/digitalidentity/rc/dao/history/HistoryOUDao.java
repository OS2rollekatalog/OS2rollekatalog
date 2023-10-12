package dk.digitalidentity.rc.dao.history;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOU;

public interface HistoryOUDao extends JpaRepository<HistoryOU, Long> {

    @Query("SELECT ho FROM HistoryOU ho WHERE ho.dato=?1")
    List<HistoryOU> findByDate(final LocalDate dato);

    HistoryOU findFirstByDatoAndOuUuidOrderByIdDesc(final LocalDate day, String uuid);

}
