package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface HistoryUserDao extends JpaRepository<HistoryUser, Long> {

    @Query("SELECT hu FROM HistoryUser hu WHERE hu.dato=?1")
    List<HistoryUser> findByDate(LocalDate date);

    HistoryUser findFirstByDatoAndUserUuid(final LocalDate day, final String userUuid);

    long countByDato(final LocalDate dato);

}
