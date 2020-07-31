package dk.digitalidentity.rc.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.UserHistoryDao;
import dk.digitalidentity.rc.dao.model.User;
import dk.digitalidentity.rc.dao.model.UserHistory;

@Service
public class UserHistoryService {

	@Autowired
	private UserHistoryDao userHistoryDao;

	public List<UserHistory> getUserHistory(User user) {
		List<UserHistory> userHistory = new ArrayList<>();

		userHistory.addAll(userHistoryDao.getDirectRoleGroupHistory(user));
		userHistory.addAll(userHistoryDao.getDirectUserRoleHistory(user));
		userHistory.addAll(userHistoryDao.getPositionRoleGroupHistory(user));
		userHistory.addAll(userHistoryDao.getPositionUserRoleHistory(user));

		return userHistory;
	}
}
