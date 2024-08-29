package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.history.HistoryDateDao;
import dk.digitalidentity.rc.dao.history.HistoryItSystemDao;
import dk.digitalidentity.rc.dao.history.HistoryKleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryManagerDao;
import dk.digitalidentity.rc.dao.history.HistoryOUDao;
import dk.digitalidentity.rc.dao.history.HistoryOUKleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryOURoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryOURoleAssignmentWithExceptionsDao;
import dk.digitalidentity.rc.dao.history.HistoryOURoleAssignmentWithTitlesDao;
import dk.digitalidentity.rc.dao.history.HistoryRoleAssignmentDao;
import dk.digitalidentity.rc.dao.history.HistoryTitleDao;
import dk.digitalidentity.rc.dao.history.HistoryUserDao;
import dk.digitalidentity.rc.dao.history.model.HistoryDate;
import dk.digitalidentity.rc.dao.history.model.HistoryItSystem;
import dk.digitalidentity.rc.dao.history.model.HistoryKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryManager;
import dk.digitalidentity.rc.dao.history.model.HistoryOU;
import dk.digitalidentity.rc.dao.history.model.HistoryOUKleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithExceptions;
import dk.digitalidentity.rc.dao.history.model.HistoryOURoleAssignmentWithTitles;
import dk.digitalidentity.rc.dao.history.model.HistoryRoleAssignment;
import dk.digitalidentity.rc.dao.history.model.HistoryTitle;
import dk.digitalidentity.rc.dao.history.model.HistoryUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

	@Autowired
	private HistoryOURoleAssignmentWithExceptionsDao historyOURoleAssignmentWithExceptionsDao;

	@Autowired
	private HistoryOURoleAssignmentWithTitlesDao historyOURoleAssignmentWithTitlesDao;

	@Autowired
	private HistoryDateDao historyDateDao;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${spring.datasource.url:}")
	private String dataSourceUrl;


	/**
	 * Check if the history job has succeeded.
	 * There is no real good way of doing that atm. so just check if there is any users at current date.
	 */
	public boolean hasHistoryBeenGenerated(final LocalDate date) {
		return historyDateDao.findById(date).isPresent();
	}

	public List<HistoryManager> getManagers(LocalDate localDate) {
		return historyManagerDao.findByDate(localDate).stream()
				.sorted(new Comparator<HistoryManager>() {

					@Override
					public int compare(HistoryManager o1, HistoryManager o2) {
						return o1.getUserName().compareTo(o2.getUserName());
					}
				}).collect(Collectors.toList());
	}

	public Map<String, HistoryUser> getUsers(LocalDate localDate) {
		return historyUserDao.findByDate(localDate)
				.stream()
				.collect(Collectors.toMap(HistoryUser::getUserUuid, Function.identity()));
	}

	public List<HistoryTitle> getTitles(LocalDate localDate) {
		return historyTitleDao.findByDate(localDate);
	}

	public Map<String, HistoryOU> getOUs(LocalDate localDate) {
		return historyOUDao.findByDate(localDate)
				.stream()
				.collect(Collectors.toMap(HistoryOU::getOuUuid, Function.identity()));
	}

	public HistoryOU getOU(LocalDate localDate, String uuid) {
		return historyOUDao.findFirstByDatoAndOuUuidOrderByIdDesc(localDate, uuid);
	}

	@Transactional
	public List<HistoryItSystem> getItSystems(LocalDate localDate) {
		return historyItSystemDao.findByDate(localDate);
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
		return historyOUKleAssignmentDao.findByDate(localDate)
				.stream()
				.collect(Collectors.groupingBy(HistoryOUKleAssignment::getOuUuid));
	}

	public List<HistoryOUKleAssignment> getOUKleAssignments(LocalDate localDate, String ouUuid) {
		return historyOUKleAssignmentDao.findByDateAndOuUuid(localDate, ouUuid);
	}

	@Transactional
	public Map<String, List<HistoryRoleAssignment>> getRoleAssignments(LocalDate localDate) {
		try (Stream<HistoryRoleAssignment> historyRoleAssignmentStream = historyRoleAssignmentDao.streamByDate(localDate)) {
			return historyRoleAssignmentStream
					.collect(Collectors.groupingBy(HistoryRoleAssignment::getUserUuid));
		}
	}

	public Map<String, List<HistoryRoleAssignment>> getRoleAssignments(LocalDate localDate, List<Long> itSystemIds) {
		return historyRoleAssignmentDao.findByDateAndItSystems(localDate, itSystemIds)
				.stream()
				.collect(Collectors.groupingBy(HistoryRoleAssignment::getUserUuid));
	}

	public Map<String, List<HistoryOURoleAssignment>> getOURoleAssignments(LocalDate localDate) {
		return historyOURoleAssignmentDao.findByDate(localDate)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignment::getOuUuid));
	}

	public Map<String, List<HistoryOURoleAssignment>> getOURoleAssignments(LocalDate localDate, List<Long> itSystemIds) {
		return historyOURoleAssignmentDao.findByDateAndItSystems(localDate, itSystemIds)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignment::getOuUuid));
	}

	public Map<String, List<HistoryOURoleAssignmentWithExceptions>> getOURoleAssignmentsWithExceptions(LocalDate localDate) {
		return historyOURoleAssignmentWithExceptionsDao.findByDate(localDate)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignmentWithExceptions::getOuUuid));
	}

	public Map<String, List<HistoryOURoleAssignmentWithExceptions>> getOURoleAssignmentsWithExceptions(LocalDate localDate, List<Long> itSystemIds) {
		return historyOURoleAssignmentWithExceptionsDao.findByDateAndItSystems(localDate, itSystemIds)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignmentWithExceptions::getOuUuid));
	}

	public Map<String, List<HistoryOURoleAssignmentWithTitles>> getOURoleAssignmentsWithTitles(LocalDate localDate) {
		return historyOURoleAssignmentWithTitlesDao.findByDate(localDate)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignmentWithTitles::getOuUuid));
	}

	public Map<String, List<HistoryOURoleAssignmentWithTitles>> getOURoleAssignmentsWithTitles(LocalDate localDate, List<Long> itSystemIds) {
		return historyOURoleAssignmentWithTitlesDao.findByDateAndItSystems(localDate, itSystemIds)
				.stream()
				.collect(Collectors.groupingBy(HistoryOURoleAssignmentWithTitles::getOuUuid));
	}

	@Transactional
	public void generateOrganisationHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryOrganisation;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryOrganisation();");
		}
	}

	@Transactional
	public void generateItSytemHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryItSystems;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryItSystems();");
		}
	}

	@Transactional
	public void generateRoleAssignmentHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryRoleAssignments;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryRoleAssignments();");
		}
	}

	@Transactional
	public void generateKleAssignmentHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryKleAssignments;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryKleAssignments();");
		}
	}

	@Transactional
	public void generateOURoleAssignmentHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryOURoleAssignments;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryOURoleAssignments();");
		}
	}

	@Transactional
	public void generateTitleRoleAssignmentHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryOURoleAssignmentsWithTitles;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryOURoleAssignmentsWithTitles();");
		}
	}

	@Transactional
	public void generateExceptedUsersHistory() {

		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("EXEC SP_InsertHistoryOURoleAssignmentsWithExceptions;");
		}
		else {
			jdbcTemplate.update("CALL SP_InsertHistoryOURoleAssignmentsWithExceptions();");
		}
	}

	public LocalDate lastGeneratedDate() {
		return historyDateDao.findFirstByOrderByDatoDesc()
				.map(HistoryDate::getDato)
				.orElse(LocalDate.EPOCH);
	}

	@Transactional
	public void generateDate() {
		final HistoryDate dato = new HistoryDate();
		dato.setDato(LocalDate.now());
		historyDateDao.save(dato);
	}

	@Transactional
	public void deleteHistoryForDay(final LocalDate date) {
		jdbcTemplate.update("DELETE FROM history_users WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_ous WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_role_assignments WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_it_systems WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_managers WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_role_assignment_titles WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_titles WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_role_assignment_excepted_users WHERE dato = ?", date);
		jdbcTemplate.update("DELETE FROM history_user_roles_system_roles");
	}

	@Transactional
	public void deleteOldHistory(long retentionPeriod) {

		// delete where (older than retention period) or (older than 2 months except days 7, 14, 21, 28)
		if (dataSourceUrl.startsWith("jdbc:sqlserver")) {
			jdbcTemplate.update("DELETE FROM history_users WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_ous WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_role_assignments WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_it_systems WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_managers WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_role_assignment_titles WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_titles WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_role_assignment_excepted_users WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
			jdbcTemplate.update("DELETE FROM history_ou_kle_assignments WHERE (dato < GETDATE() - " + retentionPeriod + ") OR (dato < DATEADD(month, -2, GETDATE()) AND DAY(dato) NOT IN (7, 14, 21, 28))");
		}
		else {
			jdbcTemplate.update("DELETE FROM history_users WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_ous WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28)) LIMIT 250000;");
			jdbcTemplate.update("DELETE FROM history_kle_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_it_systems WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_managers WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_ou_role_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_role_assignment_titles WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_titles WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_role_assignment_excepted_users WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28));");
			jdbcTemplate.update("DELETE FROM history_ou_kle_assignments WHERE dato < (NOW() - INTERVAL " + retentionPeriod + " DAY) OR (dato < (NOW() - INTERVAL 2 MONTH) AND DAY(dato) NOT IN (7, 14, 21, 28)) LIMIT 50000;");
		}
	}
}
