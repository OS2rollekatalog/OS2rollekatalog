package dk.digitalidentity.rc.dao.history;

import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.stream.Stream;

public interface HistoryItSystemDao extends JpaRepository<HistoryItSystem, Long> {

//  cannot get this to work, keeps returning 0 hits
//	List<HistoryItSystem> findByDato(LocalDate date);

    HistoryItSystem findFirstByDatoAndItSystemId(final LocalDate dato, Long itSystemId);

    @Query("SELECT hit FROM HistoryItSystem hit WHERE hit.dato=:dato")
    Stream<HistoryItSystem> streamByDate(@Param("dato") LocalDate dato);

}
