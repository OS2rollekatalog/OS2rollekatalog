package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ManagerDelegateDao extends CrudRepository<ManagerDelegate, Long> {

	@Query("SELECT md FROM manager_delegate md WHERE md.delegate.uuid = :delegateUuid AND md.fromDate <= :today AND (md.indefinitely = true OR md.toDate >= :today)")
	List<ManagerDelegate> findActiveByDelegateUuid(@Param("delegateUuid") String delegateUuid, @Param("today") LocalDate today);

	@Query("SELECT md FROM manager_delegate md WHERE md.delegate = :delegate AND md.fromDate <= :today AND (md.indefinitely = true OR md.toDate >= :today)")
	List<ManagerDelegate> findActiveByDelegate(@Param("delegate") User delegate, @Param("today") LocalDate today);

	@Query("SELECT md FROM manager_delegate md WHERE md.manager = :manager AND md.fromDate <= :today AND (md.indefinitely = true OR md.toDate >= :today)")
	List<ManagerDelegate> findActiveByManager(@Param("manager") User manager, @Param("today") LocalDate today);
}
