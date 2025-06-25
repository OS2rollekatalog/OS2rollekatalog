package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.ManagerDelegateDao;
import dk.digitalidentity.rc.dao.model.ManagerDelegate;
import dk.digitalidentity.rc.dao.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Service
public class ManagerDelegateService {
	@Autowired
	private ManagerDelegateDao managerDelegateDao;

	@Autowired
	private UserService userService;

	public List<ManagerDelegate> getAll() {
		return new ArrayList<>((Collection<ManagerDelegate>) managerDelegateDao.findAll());
	}

	public List<ManagerDelegate> getAllActive() {
		return managerDelegateDao.findByFromDateAfterAndToDateBeforeOrIndefinitelyTrueOrderByManager_NameAsc(LocalDate.now(), LocalDate.now());
	}

	public ManagerDelegate getById(Long id) {
		return managerDelegateDao.findById(id).orElseThrow();
	}

	/**
	 * Updates or Creates a ManagerDelegate, depending on if the passed Id is null or not.
	 * @param id id
	 * @param managerUuid managerUuid
	 * @param delegateUuid delegateUuid
	 * @param fromDate fromDate
	 * @param toDate toDate
	 * @param indefinitely indefinitely
	 * @return ManagerDelegate
	 */
	public ManagerDelegate upsert(Long id, String managerUuid, String delegateUuid, LocalDate fromDate, LocalDate toDate, boolean indefinitely) {
		//validation
		if (fromDate == null
			|| (toDate == null && !indefinitely)
			|| (toDate != null && toDate.isBefore(fromDate))
		) {
			throw new IllegalArgumentException("One or more dates are invalid. 'from' date must exist and cannot be in the past. 'to' date can only be null if assignment is indefinitely");
		}

		User delegate = userService.getOptionalByUuid(delegateUuid)
				.orElseThrow();
		User manager = userService.getOptionalByUuid(managerUuid)
				.orElseThrow();

		ManagerDelegate managerDelegate;
		if (id == null) {
			//Create
			managerDelegate = ManagerDelegate.builder()
					.fromDate(fromDate)
					.indefinitely(indefinitely)
					.toDate(indefinitely ? null : toDate)
					.manager(manager)
					.delegate(delegate)
					.build();
		} else {
			//Update
			managerDelegate = managerDelegateDao.findById(id)
					.orElseThrow();
			managerDelegate.setIndefinitely(indefinitely);
			managerDelegate.setFromDate(fromDate);
			managerDelegate.setToDate(toDate);
			managerDelegate.setManager(manager);
			managerDelegate.setDelegate(delegate);

		}

		return managerDelegateDao.save(managerDelegate);
	}

	public void delete(long id) {
		managerDelegateDao.deleteById(id);
	}

	public List<ManagerDelegate> getByDelegateUuid(String delegateUuid) {
		return managerDelegateDao.findByDelegateUuidAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(delegateUuid, LocalDate.now(), LocalDate.now());
	}

	public List<ManagerDelegate> getByManager(User manager) {
		return managerDelegateDao.getByManagerAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(manager, LocalDate.now(), LocalDate.now());
	}

	public List<ManagerDelegate> getByDelegate(User delegate) {
		return managerDelegateDao.getByDelegateAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(delegate, LocalDate.now(), LocalDate.now());
	}

	public List<ManagerDelegate> getByDelegateAndManager(User delegate, User manager) {
		return managerDelegateDao.getByDelegateAndManagerAndFromDateAfterAndToDateBeforeOrIndefinitelyTrue(delegate, manager, LocalDate.now(), LocalDate.now());
	}
}
