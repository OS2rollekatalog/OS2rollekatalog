package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dk.digitalidentity.rc.dao.history.HistoryItSystemDao;
import dk.digitalidentity.rc.dao.history.HistoryKleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryManagerDao;
import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.HistoryOUKleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryOURoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryRoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryTitleDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryManager;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;

@Service
public class HistoryService {

	@Autowired
	private HistoryItSystemDao historyItSystemDao;
	
	@Autowired
	private HistoryKleAssignmentDao historyKleAssignmentDao;

	@Autowired
	private HistoryOUKleAssignmentDao historyOUKleAssignmentDao;

	@Autowired
	private HistoryOUDao historyOUDao;
	
	@Autowired
	private HistoryRoleAssignmentDao historyRoleAssignmentDao;
	
	@Autowired
	private HistoryUserDao historyUserDao;
	
	@Autowired
	private HistoryTitleDao historyTitleDao;
	
	@Autowired
	private HistoryManagerDao historyManagerDao;
	
	@Autowired
	private HistoryOURoleAssignmentDao historyOURoleAssignmentDao;

	public List<HistoryManager> getManagers(LocalDate localDate) {
		return historyManagerDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth()).stream()
				.sorted(new Comparator<HistoryManager>() {

					@Override
					public int compare(HistoryManager o1, HistoryManager o2) {
						return o1.getUserName().compareTo(o2.getUserName());
					}
				}).collect(Collectors.toList());
	}

	public Map<String, HistoryUser> getUsers(LocalDate localDate) {
		return historyUserDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
							 .stream()
							 .collect(Collectors.toMap(HistoryUser::getUserUuid, Function.identity()));
	}
	
	public List<HistoryTitle> getTitles(LocalDate localDate) {
		return historyTitleDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
	}
	
	public Map<String, HistoryOU> getOUs(LocalDate localDate) {
		return historyOUDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
						   .stream()
						   .collect(Collectors.toMap(HistoryOU::getOuUuid, Function.identity()));
	}
	
	public HistoryOU getOU(LocalDate localDate, String uuid) {
		return historyOUDao.findByDateAndUuid(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), uuid);
	}
	
	public List<HistoryItSystem> getItSystems(LocalDate localDate) {
		return historyItSystemDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
	}
	
	public Map<String, List<HistoryKleAssignment>> getKleAssignments(LocalDate localDate) {
		return historyKleAssignmentDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
									  .stream()
									  .collect(Collectors.groupingBy(HistoryKleAssignment::getUserUuid));
	}
	
	public List<HistoryKleAssignment> getKleAssignments(LocalDate localDate, String userUuid) {
		return historyKleAssignmentDao.findByDateAndUserUuid(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), userUuid);
	}

	public Map<String, List<HistoryOUKleAssignment>> getOUKleAssignments(LocalDate localDate) {
		return historyOUKleAssignmentDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
									  .stream()
									  .collect(Collectors.groupingBy(HistoryOUKleAssignment::getOuUuid));
	}
	
	public List<HistoryOUKleAssignment> getOUKleAssignments(LocalDate localDate, String ouUuid) {
		return historyOUKleAssignmentDao.findByDateAndOuUuid(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), ouUuid);
	}
	
	public Map<String, List<HistoryRoleAssignment>> getRoleAssignments(LocalDate localDate) {
		return historyRoleAssignmentDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
									   .stream()
									   .collect(Collectors.groupingBy(HistoryRoleAssignment::getUserUuid));
	}
	
	public Map<String, List<HistoryRoleAssignment>> getRoleAssignments(LocalDate localDate, List<Long> itSystemIds) {
		return historyRoleAssignmentDao.findByDateAndItSystems(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), itSystemIds)
									   .stream()
									   .collect(Collectors.groupingBy(HistoryRoleAssignment::getUserUuid));
	}
	
	public Map<String, List<HistoryOURoleAssignment>> getOURoleAssignments(LocalDate localDate) {
		return historyOURoleAssignmentDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
									   .stream()
									   .collect(Collectors.groupingBy(HistoryOURoleAssignment::getOuUuid));
	}
	
	public Map<String, List<HistoryOURoleAssignment>> getOURoleAssignments(LocalDate localDate, List<Long> itSystemIds) {
		return historyOURoleAssignmentDao.findByDateAndItSystems(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), itSystemIds)
									   .stream()
									   .collect(Collectors.groupingBy(HistoryOURoleAssignment::getOuUuid));
	}
}
