package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryDate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface HistoryDateDao extends JpaRepository<HistoryDate, LocalDate>  {

    Optional<HistoryDate> findFirstByOrderByDatoDesc();

}
