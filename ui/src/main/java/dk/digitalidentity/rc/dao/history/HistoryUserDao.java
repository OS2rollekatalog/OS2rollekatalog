package dk.digitalidentity.rc.dao.history;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.rc.dao.history.model.HistoryUser;

public interface HistoryUserDao extends JpaRepository<HistoryUser, Long> {

    @Query("SELECT hu FROM HistoryUser hu WHERE YEAR(hu.dato)=?1 AND MONTH(hu.dato)=?2 AND DAY(hu.dato)=?3")
    List<HistoryUser> findByDate(Integer year, Integer month, Integer day);

}
