package dk.digitalidentity.rc.dao;

import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDate;
import java.util.List;

public interface ManagerDelegateDao extends CrudRepository<ManagerDelegate, Long> {

	List<ManagerDelegate> findByFromDateAfterAndToDateBeforeOrIndefinitelyTrueOrderByManager_NameAsc(LocalDate fromDate, LocalDate toDate);

	List<ManagerDelegate> findByDelegateUuidAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(String delegateUuid, LocalDate fromDate, LocalDate toDate);

	List<ManagerDelegate> getByDelegateAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(User delegate, LocalDate fromDate, LocalDate toDate);

	List<ManagerDelegate> getByDelegateAndManagerAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(User delegate, User manager, LocalDate fromDate, LocalDate toDate);

	List<ManagerDelegate> getByManagerAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(User manager, LocalDate fromDate, LocalDate toDate);
}
