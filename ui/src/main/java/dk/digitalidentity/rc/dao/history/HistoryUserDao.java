package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface HistoryUserDao extends JpaRepository<HistoryUser, Long> {

    @Query("SELECT hu FROM HistoryUser hu WHERE YEAR(hu.dato)=?1 AND MONTH(hu.dato)=?2 AND DAY(hu.dato)=?3")
    List<HistoryUser> findByDate(Integer year, Integer month, Integer day);

    HistoryUser findFirstByDatoAndUserUuid(final LocalDate day, final String userUuid);

    long countByDato(final LocalDate dato);

}
