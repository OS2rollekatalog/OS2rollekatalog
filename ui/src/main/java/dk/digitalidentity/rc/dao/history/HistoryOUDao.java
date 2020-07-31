package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryOU;

public interface HistoryOUDao extends JpaRepository<HistoryOU, Long> {

    @Query("SELECT ho FROM HistoryOU ho WHERE YEAR(ho.dato)=?1 AND MONTH(ho.dato)=?2 AND DAY(ho.dato)=?3")
    List<HistoryOU> findByDate(Integer year, Integer month, Integer day);

    @Query("SELECT ho FROM HistoryOU ho WHERE YEAR(ho.dato)=?1 AND MONTH(ho.dato)=?2 AND DAY(ho.dato)=?3 AND ou_uuid=?4")
    HistoryOU findByDateAndUuid(Integer year, Integer month, Integer day, String uuid);
}
