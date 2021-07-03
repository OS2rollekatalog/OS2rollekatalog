package dk.digitalidentity.rc.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dk.digitalidentity.rc.dao.HistoryOURoleAssignmentWithExceptionsDao;
import dk.digitalidentity.rc.dao.history.HistoryItSystemDao;
import dk.digitalidentity.rc.dao.history.HistoryKleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryManagerDao;
import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.HistoryOUKleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryOURoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryRoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryTitleDao;
import dk.digitalidentity.rc.dao.history.HistoryTitleRoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryManager;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryTitleRoleAssignment;
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
	private HistoryTitleRoleAssignmentDao historyTitleRoleAssignmentDao;
	
	@Autowired
	private HistoryOURoleAssignmentDao historyOURoleAssignmentDao;

	@Autowired
	private HistoryOURoleAssignmentWithExceptionsDao historyOURoleAssignmentWithExceptionsDao;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${spring.datasource.url:}")
	private String dataSourceUrl;

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
		return historyKleAssignmentDao.findByDate(localDate.toString())
									  .stream()
									  .collect(Collectors.groupingBy(HistoryKleAssignment::getUserUuid));
	}
	
	public List<HistoryKleAssignment> getKleAssignments(LocalDate localDate, String userUuid) {
		return historyKleAssignmentDao.findByDateAndUserUuid(localDate.toString(), userUuid);
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

	public Map<String, List<HistoryOURoleAssignmentWithExceptions>> getOURoleAssignmentsWithExceptions(LocalDate localDate) {
		return historyOURoleAssignmentWithExceptionsDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignmentWithExceptions::getOuUuid));
	}

	public Map<String, List<HistoryOURoleAssignmentWithExceptions>> getOURoleAssignmentsWithExceptions(LocalDate localDate, List<Long> itSystemIds) {
		return historyOURoleAssignmentWithExceptionsDao.findByDateAndItSystems(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), itSystemIds)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignmentWithExceptions::getOuUuid));
	}
	
	public Map<String, List<HistoryTitleRoleAssignment>> getTitleRoleAssignments(LocalDate localDate) {
		return historyTitleRoleAssignmentDao.findByDate(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth())
									   .stream()
									   .collect(Collectors.groupingBy(HistoryTitleRoleAssignment::getTitleUuid));
	}
	
	public Map<String, List<HistoryTitleRoleAssignment>> getTitleRoleAssignments(LocalDate localDate, List<Long> itSystemIds) {
		return historyTitleRoleAssignmentDao.findByDateAndItSystems(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), itSystemIds)
									   .stream()
									   .collect(Collectors.groupingBy(HistoryTitleRoleAssignment::getTitleUuid));
	}
	
	@Transactional
	public void generateOrganisationHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryOrganisation();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryOrganisation;");
		}
	}
	
	@Transactional
	public void generateItSytemHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryItSystems();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryItSystems;");
		}
	}
	
	@Transactional
	public void generateRoleAssignmentHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryRoleAssignments();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryRoleAssignments;");
		}
	}
	
	@Transactional
	public void generateKleAssignmentHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryKleAssignments();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryKleAssignments;");
		}
	}
	
	@Transactional
	public void generateOURoleAssignmentHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryOURoleAssignments();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryOURoleAssignments;");
		}
	}
	
	@Transactional
	public void generateTitleRoleAssignmentHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryTitleRoleAssignments();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryTitleRoleAssignments;");
		}
	}

	@Transactional
	public void generateExceptedUsersHistory() {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("CALL SP_InsertHistoryOURoleAssignmentsWithExceptions();");
		}
		else {
			jdbcTemplate.update("EXEC SP_InsertHistoryOURoleAssignmentsWithExceptions;");
		}
	}

	@Transactional
	public void deleteOldHistory(long retentionPeriod) {
		if (dataSourceUrl.startsWith("jdbc:mysql")) {
			jdbcTemplate.update("DELETE FROM history_users WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_ous WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_it_systems WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_managers WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_title_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_titles WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
			jdbcTemplate.update("DELETE FROM history_role_assignment_excepted_users WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY);");
		}
		else {
			jdbcTemplate.update("DELETE FROM history_users WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_ous WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_role_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_it_systems WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_managers WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_title_role_assignments WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_titles WHERE dato < GETDATE() - " + retentionPeriod + ";");
			jdbcTemplate.update("DELETE FROM history_role_assignment_excepted_users WHERE dato < GETDATE() - " + retentionPeriod + ";");
		}
	}
}
